// vars/jira/jiraUtils.groovy

/**
 * Extraction des informations du commentaire :
 * - nom du projet Jira
 * - numéro de la demande Jira
 * - commentaire de commit
 * Les informations sont ensuite stockées dans la classe JiraGitComment.
 * Le commentaire est forcé côté Gitlab et ne peut contenir que <nom_projet>-<id_ticket> <texte du commentaire>
 * @param comment le commentaire à analyser
 * @param logDebug la classe de log
 * @return JiraGitComment
 */
def extractInfosFromComment(ticket, logDebug) {
    logDebug.debug("Ticket à analyser : ${ticket}")

    def infos = ticket =~ "^(.*)-([0-9]+)\$"
    def projet = infos[0][1]
    def numIssue = infos[0][2]
    def issue = "${projet}-${numIssue}"

    def jgComment = new laposte.utils.JiraGitComment()
    jgComment.project = JIRA_PROJECTS.valueOf(projet)
    jgComment.jiraIssue = issue

    logDebug.debug("Projet extrait du commentaire : ${jgComment.project}")
    logDebug.debug("Ticket Jira extrait du commentaire : ${jgComment.jiraIssue}")

    return jgComment
}

/**
 * Contrôle qu'un projet contient bien la version passée en paramètre.
 * @param project le projet (JIRA_PROJECTS)
 * @param version le nom de la version à chercher dans le projet
 * @param logDebug l'outil de log
 * @return l'id de la version
 */
def getVersionIdFromBranch(project, version, logDebug) {
    def versions = jiraApi.getProjectVersions(project, logDebug)
    if (versions == null) {
        echo "Pas de versions trouvées par l'appel REST."
        return -1
    }

    for (int i=0; i < versions.size(); i++) {
        def versionName = versions[i].name
        logDebug.debug("Contrôle de la version ${versionName} du projet ${project.name} vs ${version}")
        if (version.equals(versionName)) {
            logDebug.debug("Version ${version} trouvée pour le projet ${project.name} (${project.id})")
            return Integer.parseInt(versions[i].id)
        }
    }

    echo "La version ${version} n'existe pas pour le projet ${project.name} (${project.id})"
    return -1
}

return this