// vars/utils.groovy

def key_value_parser(file, key_param) {
    //echo "reading entries file"
    str = fileToString(file)
    map = stringToArrayParser(str, ':', '\n')
    def result = ""
    // iterate on project -> key ------------------------------------------------------------------------------------
    if ( ! map.isEmpty() ) {
        map.each{ key, value -> 
            if( key == key_param) {
                echo "${key} = ${value}"
                result = value
            }
        }
    } else {
        echo "entries file empty or malformed"
    }
    return result
}

def getFileNamesListUnderPath(path, filters, remove_extention=false) {
    filters += " | sed -e 's@/@@g'"
    if ( remove_extention )
        filters += " | sed 's/\\.[^.]*\$//'"
    list = sh (
        script: "ls -1 ${path} | grep ${filters}",
        returnStdout: true
    )
    return list
}

def appendStringAfterFirstMatchInFile(stringToAdd, stringToMatch, stringFormater, delimiter="--delimiter--", file) {
    entry = sh (
                script: "sed -i 's/${delimiter}/\"${stringToAdd}\"/'",
                returnStdout: true
            )
    //entry = "\\ \\ \\ \\ \\ \\ \\ \\ - { ${it} }"
    sh "grep -qF \"${stringToAdd}\" ${file} || sed -i '/${stringToMatch}/a ${entry}' ${file}"
    sh "cat ${file}"
}

def getFolderNamesList(isLastVersion = false) {
    def caddi_rpm_version = sh (
            //script: "cd /data/eboutique_releases && ls -dt */ | ${regex} sed -e 's/\\///g' | head -n1",
            script: "grep caddi.rpm.version build.properties | cut -d= -f2",
            returnStdout: true
        ).trim()    //def values = env.BRANCH_NAME.split( '_' )
    //def release_parts = values[1].tokenize( '.' )
    def regex = "grep ${caddi_rpm_version} |"
    //release_parts.each{ curr -> 
    //    regex += " grep ${curr} |"
    //}
    if (isLastVersion) {
        list = sh (
            //script: "cd /data/eboutique_releases && ls -dt */ | ${regex} sed -e 's/\\///g' | head -n1",
            script: "cd /data/eboutique_releases && ls -dt */ | ${regex} sed -e 's@/@@g' | head -n1",
            returnStdout: true
        ).trim()
    } else {
        list = sh (
            //script: "cd /data/eboutique_releases && ls -dt */ | ${regex} sed -e 's/\\//\\n/g'| sed -e 's/ //g'",
            script: "cd /data/eboutique_releases && ls -dt */ | ${regex} sed -e 's@/@@g'",
            returnStdout: true
        )
    }
    return list
}

def copyRemoteFileToLocal(remoteFile, localLocation, server, user) {
    try {
        sh "scp -i ~/.ssh/id_rsa ${user}@${server}:${file} ${localLocation}"
    } catch (error) {
        // On ne bloque pas le build si on ne peut pas envoyer de message à Mattermost.
        echo "\u001B[31m ${file} copy failed from ${server} for user ${user} : ${error.message} \u274C \u001B[0m"
    }
}

def copyLocalFileToRemote(localFile, remoteLocation, server, user) {
    try {
        sh "scp -i ~/.ssh/id_rsa ${localFile} ${user}@${server}:${remoteLocation}"
    } catch (error) {
        // On ne bloque pas le build si on ne peut pas envoyer de message à Mattermost.
        echo "\u001B[31m ${file} copy failed to ${server} for user ${user} : ${error.message} \u274C \u001B[0m"
    }
}

def deleteCurrentBuildFromHistory(jobName) {
    def job = Jenkins.instance.getItem(jobName)

    //job.getBuilds().each { it.delete() }
    def current_build = job.getBuilds().first()
    current_build.delete()
    // uncomment these lines to reset the build number to 1:
    //job.nextBuildNumber = 1
    //job.save()
}
def isDuplicatedLine(file, input_source, input_version_release) {
    def file_content = fileToString(pwd()+'/'+file)
    branches_map = stringToArrayParser(file_content, ':', '\n')
    branches_map.each{ source, version_release -> 
        if( source == input_source && version_release == input_version_release)
            return true
    }
    return false
}

def appendToFile(file, data) {
    //echo pwd()
    def f = new File(pwd()+'/'+file)
    f.append("\n"+ data)
}

def stringToArrayParser(data, indexContentDelimiter, cellsDelimiter) {
    def map = [:]
    if (data) {
        data.split(cellsDelimiter).each {param ->
            def nameAndValue = param.split(indexContentDelimiter)
            map[nameAndValue[0]] = nameAndValue[1]
        }
    }
    return map
}

def fileToString(path) {
    String content
    if (fileExists(path)) {
        content = readFile(path)
    } else {
        echo 'File does not exist in the specified path'
    }
    return content
}

def parseJson(json) {
    return new HashMap(new groovy.json.JsonSlurper().parseText(json))
}

def versionSbt() {
    def matcher = readFile('build.sbt') =~ 'version in ThisBuild := "(.+)"'
    matcher ? matcher[0][1] : null
}

def versionGradle() {
    def matcher = readFile('gradle.properties') =~ "version=(.+)"
    matcher ? matcher[0][1] : null
}

def groupGradle() {
    def matcher = readFile('gradle.properties') =~ "group=(.+)"
    matcher ? matcher[0][1] : null
}

def sendMessageToDevops(service, title, message, couleur, buzzer) {
    if (message == null) {
        echo "\u001B[31m On essaie d'afficher un message sur le Raspberry mais on n'a pas de message à afficher :( C'est ballot ... \u274C \u001B[0m"
        return
    }

    try {
        def codeCouleur = (couleur as COULEURS).codeCouleur
        httpRequest(
                url: "http://215.5.112.31/api/display",
                contentType: "APPLICATION_JSON",
                httpMode: "POST",
                requestBody: '{"service":"' + service + '","title":"' + title + '","message":"' + message + '","alertlight":"' + codeCouleur + '","buzzer":"' + buzzer + '"}',
                timeout: 2
        )
    } catch (error) {
        // On ne bloque pas le build si on ne peut pas se connecter au raspberry.
        echo "\u001B[31m Le raspberry ne répond pas (pas grave, on continue quand même) : ${error.message} \u274C \u001B[0m"
    }
}

def sendMessageToMattermost(message, String projet) {
    sendMessageToMattermost(message, projet as CANAUX_MATTERMOST)
}

def sendMessageToMattermost(message, CANAUX_MATTERMOST canal) {
    try {
        if (message == null) {
            echo "\u001B[31m On essaie d'afficher un message sur Mattermost mais on n'a pas de message à afficher :( C'est ballot ... \u274C \u001B[0m"
            return
        }
        def idCanal = canal.id
        def finalMessage = message.replace("'", " ")
        sh 'export http_proxy="http://221.128.58.183:3128" && export https_proxy="http://221.128.58.183:3128" && curl -i --insecure -X POST -d payload=\'{"text": "' + finalMessage + '"}\' https://bavardages.picbnum.fr/hooks/' + idCanal
    } catch (error) {
        // On ne bloque pas le build si on ne peut pas envoyer de message à Mattermost.
        echo "\u001B[31m Mattermost ne répond pas (pas grave, on continue quand même) : ${error.message} \u274C \u001B[0m"
    }
}

def getListFromSshCall(list, delimiter) {
    return list.split(delimiter).collect{it as String}
}

/**
 * Retourne l'url de download d'un artéfact (depuis artifactory) via le reverse-proxy.
 * @param uri url de l'artéfact dans artifactory
 * @param rpUrl url du reverse-proxy
 * @return l'url de l'artefact depuis artifactory
 */
def reverseProxyDownloadUri(uri, rpUrl) {
    def infos = uri =~ ".*/artifactory/(.*)"
    def urlApk = infos[0][1]
    def url = "${rpUrl}${urlApk}"

    echo "Url de DL via le reverse-proxy : ${url}"
    return url
}

/**
 *
 * @param message
 * @param idCanal
 * @return
 */
def sendMattermostMessage(message, idCanal) {
    try {
        if (message == null) {
            echo "\u001B[31m On essaie d'afficher un message sur Mattermost mais on n'a pas de message à afficher :( C'est ballot ... \u274C \u001B[0m"
            return
        }
        def finalMessage = message.replace("'", " ")
        sh 'export http_proxy="http://221.128.58.183:3128" && export https_proxy="http://221.128.58.183:3128" && curl -i --insecure -X POST -d payload=\'{"text": "' + finalMessage + '"}\' https://bavardages.picbnum.fr/hooks/' + idCanal
    } catch (error) {
        // On ne bloque pas le build si on ne peut pas envoyer de message à Mattermost.
        echo "\u001B[31m Mattermost ne répond pas (pas grave, on continue quand même) : ${error.message} \u274C \u001B[0m"
    }
}

enum CANAUX_MATTERMOST {
    DEFAULT_TEST2("xxxxxxx"),
    PROJET_TEST1("xxxxxxxx")

    String id

    CANAUX_MATTERMOST(String id) {
        this.id = id
    }
}

enum COULEURS {
    VERT("green"),
    ORANGE("orange"),
    ROUGE("red")

    String codeCouleur

    COULEURS(String codeCouleur) {
        this.codeCouleur = codeCouleur
    }
}
