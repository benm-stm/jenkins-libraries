// vars/nodeUtils.groovy

private def initNode(nodeName, Closure body) {
    node (nodeName) {
        ansiColor('xterm') {
            timestamps {
                echo "\u001B[34m \u2776 Paramètres du JOB " + params + " \u001B[0m"
                body()
            }
        }
    }
}

def nodeCode(Map vars, Closure body) {
    vars = vars ?: [:]
    def destinataires = vars.get('to')
    if(!destinataires) {
        destinataires = 'pic-eboutique-bnum@laposte.net, TRA-LaPoste-BNum@hardis.fr'
    }

    def notify = vars.get('notify', false)
    //******************************************************
    //* Notify the team when a build is performed
    //******************************************************
    if(notify) {
        mailUtils.sendStartMessage(destinataires)
    }

    def nodeName = vars.get('nodeName', 'master')
    initNode (nodeName) {
        body()
    }
    //******************************************************
    //*  Notify the team when a build is finished  */
    //******************************************************
    if(notify) {
        mailUtils.sendEndMessage(destinataires)
    }
}

def stageWithTryCatch(vars, body) {
    vars = vars ?: [:]
    def destinataires = vars.get('to', 'ld-corp-devops-bnum@laposte.fr')
    echo "\u001B[34m Stage \u27A1 " + vars.get('stageName', 'Build') + " \u001B[0m"
    stage(vars.get('stageName', 'Build')) {
        try {
            body()
        } catch (error) {
            mailUtils.sendFailedMessage(destinataires, error)
            echo "\u001B[31m ERROR: ${error.message} \u274C \u001B[0m"
            utils.sendMessageToDevops('Jenkins', 'Erreur', error.message, "ROUGE", false)
            if (params.PROJET) {
                utils.sendMessageToMattermost(error.message, params.PROJET)
            }
            throw error
        }
    }
}

def stageWithTryCatchForMerge(vars, body) {
    vars = vars ?: [:]
    echo "\u001B[34m Stage \u27A1 " + vars.get('stageName', 'Build') + " \u001B[0m"
    stage(vars.get('stageName', 'Build')) {
        try {
            body()
        } catch (error) {
            def conflictsMapSource = [:]
            def conflictsMap_mailbodySource = [:]
            def devops_mail_body_Source = ""

            def conflictsMapDest = [:]
            def conflictsMap_mailbodyDest = [:]
            def devops_mail_body_Dest = ""

            def conflictsMap_collectiv = [:]
            def devops_mail_body_collectiv = ""

            def devops_mail_header = "Bonjour,<br><br>Merge conflicts détectés entre la "+ vars.get('branche_source') +" et la "+ vars.get('branche_cible') +"<br>"

            def files = gitUtils.gitConflicts()
            def files_list = utils.getListFromSshCall(files, '\n')
            
            echo "\u001B[31m ERROR: ${error.message} \u274C \u001B[0m"

            conflictsMapSource = constructMailFilesGroupmentPerUser(files_list, conflictsMapSource)
            conflictsMap_mailbodySource = constructUserMail(conflictsMapSource, vars)
            devops_mail_body_Source = constructDevopsMail(conflictsMapSource, devops_mail_body_Source)

            gitUtils.gitAbortMerge()
            gitUtils.gitChangeBranch(vars.get('branche_source'))

            conflictsMapDest = constructMailFilesGroupmentPerUser(files_list, conflictsMapDest)
            conflictsMap_mailbodyDest = constructUserMail(conflictsMapDest, vars)
            devops_mail_body_Dest = constructDevopsMail(conflictsMapDest, devops_mail_body_Dest)
            

            devops_mail_body_collectiv =  "<br>=> Affected files by the merge:<br>" + "- "+vars.get('branche_cible')+"<br>" +devops_mail_body_Source + "<br>" + "- "+vars.get('branche_source')+"<br>" +devops_mail_body_Dest
            conflictsMap_mailbodySource = appendAllConflicts(conflictsMap_mailbodySource, devops_mail_body_collectiv)
            conflictsMap_mailbodyDest = appendAllConflicts(conflictsMap_mailbodyDest, devops_mail_body_collectiv)

            sendmailFromList(conflictsMap_mailbodySource)
            sendmailFromList(conflictsMap_mailbodyDest)

            //sendmail to devops
            devops_mail_body_collectiv = devops_mail_header + devops_mail_body_collectiv+"<br><br>Cordialement"
            mailUtils.sendCustomMail("ld-corp-devops-bnum@laposte.fr", 
                                     "Jenkins - Merge conflict #${env.BUILD_NUMBER}",
                                    devops_mail_body_collectiv,
                                    "cpit-boutique-bnum@laposte.net")
            if (params.PROJET) {
                utils.sendMessageToMattermost(error.message, params.PROJET)
            }
                //throw error
                return true
        }
    }
    
}

def constructUserMail(conflictsMap, vars) {
    def conflictsMap_mailbody = [:]
    conflictsMap.each{ mail, file -> 
        conflictsMap_mailbody[mail] = "Bonjour,<br><br>Merge conflicts détectés entre la "+ vars.get('branche_source') +" et la "+ vars.get('branche_cible')+ " veuillez corriger les conflits" + "<br><br> => Affected files by you:<br>" + file
    }
    return conflictsMap_mailbody
}

def constructDevopsMail(conflictsMap, devops_mail_body) {
    conflictsMap.each{ mail, file -> 
        devops_mail_body = devops_mail_body +"<br>" + mail +":"+ file +"<br><br>"
    }
    return devops_mail_body
}

def appendAllConflicts(conflictsMap_mailbody, devops_mail_body) {

    //refine mail body (files groupment per user) add all conflicts to the users mail and send mail
    conflictsMap_mailbody.each{ mail, user_mail_body -> 
        conflictsMap_mailbody[mail] = user_mail_body + devops_mail_body
    }
    return conflictsMap_mailbody
}

def constructMailFilesGroupmentPerUser(files_list, conflictsMap){
    files_list.eachWithIndex { item, index ->
        def user_mails_to_notify=gitUtils.gitLogs(item, "--pretty=%ae --")
        //def user_names_to_notify=gitUtils.gitLogs(item, "%an")

        //to remove concatenation null issue
        if (conflictsMap[user_mails_to_notify] == null) {
            conflictsMap[user_mails_to_notify] = ""
        }
        //construct a hash containing : hash[mail]=mail_body
        conflictsMap[user_mails_to_notify] = conflictsMap[user_mails_to_notify] + "<br>" + item
    }
    return  conflictsMap
}

def sendmailFromList(list) {
    list.each{ mail, content -> 
        mailUtils.sendCustomMail(mail, 
                            "Jenkins - Merge conflict #${env.BUILD_NUMBER}",
                            content)
    }
}