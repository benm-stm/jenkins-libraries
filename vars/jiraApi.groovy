// vars/jira/jiraApi.groovy

/**
 * Récupération de l'ensemble des versions d'un projet dans Jira
 * @param project le projet duquel récupérer les versions (JIRA_PROJECTS)
 * @return la réponse (array), null si erreur
 */
def getProjectVersions(project, logDebug) {
    logDebug.debug("Récupération des versions du projet ${project.name}")
    def urlRest = "${jiraConstants.jiraApiUrl}/project/${project.id}/versions"

    def response
    try {
        response = httpRequest(
                url: urlRest,
                authentication: "gitlab_jira",
                httpMode: "GET",
                timeout: 5
        )
    } catch (error) {
        echo "\u001B[31m Impossible de récupérer les versions du projet ${project.name} (${project.id}) dans Jira : ${error.message} \u274C \u001B[0m"
        return null
    }

    if (response.status != 200) {
        echo "\u001B[31m Impossible de récupérer les versions du projet ${project.name} (${project.id}) dans Jira : HTTP ${response.status} (${response.content}) \u274C \u001B[0m"
        return null
    }

    return new groovy.json.JsonSlurper().parseText(response.content).toArray()
}

/**
 * Mise à jour de la version du ticket
 * @param issue le ticket à mettre à jour
 * @param idVersion la version à mettre dans le ticket
 * @param logDebug l'outil de log
 * @return rien
 */
def updateVersion(issue, idVersion, simulation, logDebug) {
    logDebug.debug("Mise à jour de la version d'id ${idVersion} dans le ticket ${issue}.")
    def urlRest = "${jiraConstants.jiraApiUrl}/issue/${issue}"
    def body = "{\"fields\": {\"fixVersions\":[{\"id\":\"${idVersion}\"}]}}"
    logDebug.debug("Corps de la requête envoyée à l'API REST (${urlRest}) : ${body}")

    def response

    if (simulation) {
        echo "MODE SIMULATION ACTIF : Mise à jour de la version (${idVersion}) dans le ticket ${issue} non effectuée."
        return
    }

    try {
        response = httpRequest(
                url: urlRest,
                customHeaders: jiraConstants.jiraApiContentType,
                authentication: "gitlab_jira",
                httpMode: "PUT",
                requestBody: body,
                timeout: 5
        )
    } catch (error) {
        def message = "Impossible de mettre à jour la version (${idVersion}) du ticket ${issue} dans Jira : ${error.message}."
        throw new Exception (message)
    }

    if (response.status != 204) {
        def message = "Impossible de mettre à jour la version (${idVersion}) du ticket ${issue} dans Jira : HTTP ${response.status} (${response.content})."
        throw new Exception (message)
    }
}

/**
 * Passe d'un status Jira à un autre. Contrôle que le status demandé est possible par-rapport à l'actuel en respectant le workflow Jira.
 * @param issue le nom du ticket
 * @param status le status que l'on veut valider afin de passer au suivant
 * @param simulation true pour ne pas faire la mise à jour
 * @param logDebug classe de log
 * @return 0 si ok, 1 si mode simulation, 2 si status non possible, exception sinon
 */
def updateStatus(issue, TRANSITIONS status, simulation, logDebug) {
    logDebug.debug("updateStatus : issue = ${issue}, status=${status}")

    // Contrôle que le status demandé est possible pour la demande
    // 1) Récupération de la listes des status possibles
    def statusList = getTicketPossibleTransitions(issue, logDebug)
    logDebug.debug("Status possibles : ${statusList}")

    // 2) Contrôle que le status demandé correspond à l'un des status possibles pour la demande
    if (! statusList.contains(status.id)) {
        def message = "\u001B[31m Le status ${status.id} ne fait pas partie des status possibles (${statusList}) pour" +
                " le ticket ${issue}. Le status n'a pas été mis à jour. \u274C \u001B[0m"
        echo message
        return 2
    }

    // Mise à jour du status
    return updateTransition(issue, status, simulation, logDebug)
}

/**
 * Récupère la liste des status possibles par-rapport au status actuel
 * @param issue
 * @param simulation
 * @param logDebug
 * @return la liste des status possibles (List<Integer>)
 */
def getTicketPossibleTransitions(issue, logDebug) {
    logDebug.debug("Récupération des status possibles de la demande ${issue}")
    def urlRest = "${jiraConstants.jiraApiUrl}/issue/${issue}/transitions?expand=transitions.fields"

    def response
    try {
        response = httpRequest(
                url: urlRest,
                authentication: "gitlab_jira",
                httpMode: "GET",
                timeout: 5
        )
    } catch (error) {
        echo "\u001B[31m Impossible de récupérer les status possibles de la demande ${issue} dans Jira : ${error.message} \u274C \u001B[0m"
        return null
    }

    if (response.status != 200) {
        echo "\u001B[31m Impossible de récupérer les status possibles de la demande ${issue} dans Jira : HTTP ${response.status} (${response.content}) \u274C \u001B[0m"
        return null
    }

    def statusList = new HashMap(new groovy.json.JsonSlurper().parseText(response.content))
    def result = []
    statusList.transitions.each {
        def id = it.id
        def name = it.name
        logDebug.debug("Ajout du status ${id} (${name}) à la liste des status possibles")
        result << Integer.parseInt(id)
    }

    return result
}

/**
 * Modification du status d'une demande.
 * @param issue la demande dont le statut doit changer
 * @param status le status à positionner
 * @param simulation true pour simuler et ne pas effectuer de modification dans Jira
 * @param logDebug la classe de log
 * @return 0 si ok, 1 si simulation, exception sinon
 */
private updateTransition(issue, TRANSITIONS status, simulation, logDebug) {
    def urlRest = "${jiraConstants.jiraApiUrl}/issue/${issue}/transitions?expand=transitions.fields"
    def body = "{\"transition\": {\"id\":\"${status.id}\"}}"

    logDebug.debug("Mise à jour du status du ticket ${issue} => ${status.id}.")
    logDebug.debug("Corps de la requête envoyée à l'API REST (${urlRest}) : ${body}.")

    if (simulation) {
        echo "MODE SIMULATION ACTIF : Mise à jour du status (${status.id}) du ticket ${issue} non effectuée."
        return 1
    }

    try {
        response = httpRequest(
                url: urlRest,
                customHeaders: jiraConstants.jiraApiContentType,
                authentication: "gitlab_jira",
                httpMode: "POST",
                requestBody: body,
                timeout: 5
        )
    } catch (error) {
        def message = "Impossible de mettre à jour le ticket ${issue} avec le status ${status.id} : ${error.message}."
        throw new Exception (message)
    }

    if (response.status != 204) {
        def message = "Impossible de mettre à jour le ticket ${issue} avec le status ${status.id} : HTTP ${response.status} (${response.content})."
        throw new Exception (message)
    }

    return 0
}

/**
 * Ajout d'un lien vers le commit au ticket associé
 * @param jgComment les informations du commit
 * @param urlRepo l'url du repo Git vers lequel faire le lien
 * @param idCommit le sha1 du commit vers lequel faire le lien
 * @param simulation true pour ne pas effectuer réellement l'ajout du lien
 * @param logDebug classe de log
 * @return 0 si ok, 1 si simulation, exception sinon
 */
def addCommitLinkToTicket(jgComment, urlRepo, idCommit, simulation, logDebug) {
    def issue = jgComment.jiraIssue
    def urlRest = "${jiraConstants.jiraApiUrl}/issue/${issue}/remotelink"
    def gitCommitUrl = "${urlRepo}/commit/${idCommit}"
    def titre = "Commit n° ${idCommit}"
    def resume = "${env.gitlabUserName} : ${jgComment.comment}"
    def icone = '{"url16x16":"https://build.bnum.laposte.fr/gitlab/favicon.ico", "title": "Gitlab"}'
    def body = "{\"object\": {\"url\":\"${gitCommitUrl}\", \"title\":\"${titre}\", \"summary\":\"${resume}\", \"icon\": ${icone}}}"

    logDebug.debug("Ajout du lien vers ${gitCommitUrl} dans le ticket ${issue}.")
    logDebug.debug("Corps de la requête envoyée à l'API REST (${urlRest}) : ${body}")

    if (simulation) {
        echo "MODE SIMULATION ACTIF : Ajout du lien ${gitCommitUrl} dans le ticket ${issue} non effectué."
        return 1
    }

    try {
        response = httpRequest(
                url: urlRest,
                customHeaders: jiraConstants.jiraApiContentType,
                authentication: "gitlab_jira",
                httpMode: "POST",
                requestBody: body,
                timeout: 5
        )
    } catch (error) {
        def message = "Impossible d'ajouter un lien au ticket ${issue} dans Jira : ${error.message}."
        throw new Exception (message)
    }

    if (response.status != 201) {
        def message = "Impossible d'ajouter un lien au ticket ${issue} dans Jira : HTTP ${response.status} (${response.content})."
        throw new Exception (message)
    }

    return 0
}

/**
 * Ajout d'un commentaire dans un ticket.
 * @param jgComment
 * @param simulation
 * @param logDebug
 */
def addCommentToTicket(jgComment, simulation, logDebug) {
    def issue = jgComment.jiraIssue
    def urlRest = "${jiraConstants.jiraApiUrl}/issue/${issue}/comment"
    def commentaire = "${jgComment.gitUser} a modifié le code relatif au ticket en précisant : ${jgComment.comment}"
    def body = "{\"body\":\"${commentaire}\"}"

    logDebug.debug("Ajout du commentaire '${commentaire}' dans le ticket ${issue}.")
    logDebug.debug("Corps de la requête envoyée à l'API REST (${urlRest}) : ${body}")

    if (simulation) {
        echo "MODE SIMULATION ACTIF : Ajout du commentaire '${commentaire}' dans le ticket ${issue} non effectué."
        return 1
    }

    try {
        response = httpRequest(
                url: urlRest,
                customHeaders: jiraConstants.jiraApiContentType,
                authentication: "gitlab_jira",
                httpMode: "POST",
                requestBody: body,
                timeout: 5
        )
    } catch (error) {
        def message = "Impossible d'ajouter un lien au ticket ${issue} dans Jira : ${error.message}."
        throw new Exception (message)
    }

    if (response.status != 201) {
        def message = "Impossible d'ajouter un lien au ticket ${issue} dans Jira : HTTP ${response.status} (${response.content})."
        throw new Exception (message)
    }

    return 0
}

/**
 * Création d'une version du projet passé en paramètre dans Jira
 * @param versionName Le nom de la version
 * @param simulation 'Non' si on doit effectivement créer la version
 * @param projet Le projet dans lequel créer la version
 * @return 0 si ok, -1 si erreur et 1 si mode simulation
 */
def creerVersionJira(versionName, projet, simulation, logDebug) {
    echo "Création de la version $versionName dans Jira"
    def urlRest = "${jiraConstants.jiraApiUrl}/version"
    def body = "{\"description\": \"${versionName}\",\"name\": \"${versionName}\",\"archived\": false,\"released\": false,\"project\": \"${projet.name}\",\"projectId\": ${projet.id}}"
    logDebug.debug("Corps de la requête envoyée à l'API REST (${urlRest}) : ${body}")

    if ( simulation ) {
        echo "MODE SIMULATION ACTIF : Version $versionName non créée dans Jira."
        return 1
    }

    try {
        httpRequest(
                url: urlRest,
                customHeaders: jiraConstants.jiraApiContentType,
                authentication: "gitlab_jira",
                httpMode: "POST",
                requestBody: body,
                timeout: 5
        )
    } catch (error) {
        // On ne bloque pas le build si on ne peut pas créer la version dans Jira.
        echo "\u001B[31m Impossible de créer la version $versionName dans Jira (pas grave, on continue quand même) : ${error.message} \u274C \u001B[0m"
        return -1
    }

    return 0
}

return this