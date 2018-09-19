// vars/gitUtils.groovy

/**
 * Suite au problème de DNS l'accès à git en SSH est différent suivant le serveur qui y accède
 */
def getGitRepoBranches() {
    def map = ["pf1a", "pf2a", "pf3a", "pft", "pf4"]
    return map
}

def urlGitDefault() {
    //return "cdlpw264.net3-courrier.extra.laposte.fr"
    return "cdlpw264-exploit.net3-courrier.extra.laposte.fr"
}

def urlExploitGit() {
    return "cdlpw264-exploit.net3-courrier.extra.laposte.fr"
}

def urlGitWriteAccess() {
    return "writeaccess.cdlpw264-exploit.net3-courrier.extra.laposte.fr"
}

private def urlGit(group, repo) {
    return urlGit(urlGitDefault(), group, repo)
}

private def urlGit(server, group, repo) {
    return "git@${server}:${group}/${repo}.git"
}


def getBranchesForProject(project) {

    //sh "git ls-remote --heads git@cdlpw264-exploit.net3-courrier.extra.laposte.fr:$project "
    def result = sh(
                script: "git ls-remote --heads git@cdlpw264-exploit.net3-courrier.extra.laposte.fr:${project}.git | sed 's/.*\\trefs\\/heads\\///g'",
                returnStdout: true
                ).trim()
    return result
}

/**
 * Récupère la branche du projet spécifié dans le groupe "eBoutique" avec les credentials du Jenkins Master.
 *
 * @param branch
 * @param project
 * @return rien
 */
def gitCall(branch, project, group="eBoutique") {
    gitCallWithGroup (branch, group, project)
}

/**
 * Récupère la branche du projet dans le groupe spécifié avec les credentials du Jenkins Master.
 *
 * @param branch
 * @param group
 * @param project
 * @return rien
 */
def gitCallWithGroup(branch, group, project) {
    gitCallWithCredentialsAndGroup (branch, group, project, 'jenkins_ssh_key')
}

/**
 * Récupère la branche du projet dans le groupe spécifié avec les credentials du Jenkins Master.
 *
 * @param vars
 * @return rien
 */
def gitCallWithMap(vars) {
    def branch = vars.get('branch')
    def group = vars.get('group', 'eBoutique')
    def project = vars.get('project')
    def credentialsId = vars.get('credentialsId', 'jenkins_ssh_key')
    def server = vars.get('server', urlGitDefault())
    git (branch: branch, credentialsId: credentialsId, url: urlGit(server, group, project))
}

/**
 * Récupère la branche du projet dans le groupe spécifié avec les credentials du Jenkins Master.
 *
 * @param vars
 * @return rien
 */
def gitCallWithMapLpfr(vars) {
    def branch = vars.get('branch')
    def group = vars.get('group', 'LaPosteFR')
    def project = vars.get('project')
    def credentialsId = vars.get('credentialsId', 'jenkins_ssh_key')
    def server = vars.get('server', urlGitDefault())
    git (branch: branch, credentialsId: credentialsId, url: urlGit(server, group, project))
}

/**
 * Récupère la branche du projet spécifié dans le groupe "eBoutique" avec les credentials spécifiés.
 *
 * @param branch
 * @param project
 * @param credentialsId
 * @return rien
 */
def gitCallWithCredentials(branch, project, credentialsId, group="eBoutique") {
    gitCallWithCredentialsAndGroup(branch, group, project, credentialsId)
}

/**
 * Récupère la branche du projet dans le groupe spécifié avec les credentials spécifiés.
 *
 * @param branch
 * @param group
 * @param project
 * @param credentialsId
 * @return rien
 */
def gitCallWithCredentialsAndGroup(branch, group, project, credentialsId) {
    git (branch: branch, credentialsId: credentialsId, url: urlGit(group, project))
}

/**
 * Récupère la branche du projet dans le groupe spécifié avec les credentials spécifiés sur le serveur spécifié.
 *
 * @param branch
 * @param group
 * @param project
 * @param credentialsId
 * @param server
 * @return rien
 */
def gitCallWithCredentialsAndGroupAndServer(branch, group, project, credentialsId, server) {
    git (branch: branch, credentialsId: credentialsId, url: urlGit(server, group, project))
}

/**
 * Récupère la branche du projet et ses sous modules/
 *
 * @param rootProject
 * @param subModules
 * @return rien
 */
def gitCallWithSubModules(rootProject, subModules, group="eBoutique") {
    if(rootProject) {
        echo "\u001B[32m \u2756 Checkout projet ${rootProject.project} \u001B[0m"
        gitCall(rootProject.branch, rootProject.project, group)
    }
    for(int i = 0; i < subModules.size(); i++){
        dir(subModules[i].dir){
            gitCall(subModules[i].branch, subModules[i].project, group)
        }
    }
}

/**
 * Récupère la branche du projet et ses sous modules/
 *
 * @param rootProject
 * @param subModules
 * @return rien
 */
def gitCallWithSubModulesAndOptions(rootProject, subModules, vars = [:]) {
    echo "\u001B[32m \u2756 Checkout projet ${rootProject.project} \u001B[0m"
    gitCallWithMap(branch: rootProject.branch,
                            group: vars.get('group', 'eboutique'),
                            project: rootProject.project,
                            server: vars.get('server', urlExploitGit()))
    for(int i = 0; i < subModules.size(); i++){
        dir(subModules[i].dir){
            gitCallWithMap(branch: subModules[i].branch,
                    group: vars.get('group', 'eboutique'),
                    project: subModules[i].project,
                    server: vars.get('server', urlExploitGit()))
        }
    }
}

def sourcesCDP(branch) {
    gitCallWithGroup(branch, "devops", "ansible.pipeline")
}

def sourcesCDP() {
    gitCallWithGroup("master", "devops", "ansible.pipeline")
}

/**
 * @return la liste des branches eBoutique répondant au pattern (en dur dans le code : TODO)
 */
def getListeBranches() {
    def workingDir = '/data/jenkins/tmp/git_compute_eboutique_branches'
    sh 'mkdir -p ' + workingDir

    dir(workingDir) {
        gitCall('master', 'eboutique.gfi')

        def result = sh(
                script: "git branch -r",
                returnStdout: true
        ).trim()

        def branchesFiltrees = result.findAll(/origin\/(goal|release|develop|remove|feature-release-fevrier|gen-artifacts|hotfix|projet)(.*)\n/)
        def branchesRetouchees = new ArrayList()
        for (String branche : branchesFiltrees) {
            branchesRetouchees.add(branche.replace('\n', '').replace('\r', ''))
            // les closures Groovy ne fonctionnent pas dans les pipelines :(
        }
        return branchesRetouchees
    }
}

/**
 * @return la liste des branches sous forme de paramètre au format utilisable dans "Extended Choice Parameter"
 */
def getListeBrancheAsParam() {
    def branches = getListeBranches()
    def result = '[ '
    for (String branche : branches) {
        result = result + '"' + branche + '", '
    }
    result = result.substring(0, result.length() - 2) + ' ]'
    return result
}

/**
 * Récupère les rôles via Galaxy
 */
def gitGalaxy(vars, body) {
    vars = vars ?: [:]
    def credentialsId = vars.get('credentialsId', 'jenkins_ssh_key')
    def server = vars.get('server', urlGitDefault())
    def branch = vars.get('branch')
    def group = vars.get('group')
    def project = vars.get('project')
    def platform = vars.get('platform')
    def cloneRequirements = vars.get('cloneRequirements', true)
    git (branch: branch, credentialsId: credentialsId, url: urlGit(server, group, project))
    dir(platform) {
        sh script: 'rm -rf ./roles/*'
    }
    if ( cloneRequirements ) {
        dir(platform) {
            def status = sh script: 'ansible-galaxy install -r requirements.yml --roles-path=./roles/', returnStatus: true
            if (status != 0) {
               throw new Exception("Problème de récupération des rôles.")
            }
        }
    } else {
        dir('eboutique/roles') {
            sh script: 'mkdir logs.forwarder'
        }
        dir('eboutique/roles/logs.forwarder') {
            git (branch: 'master', credentialsId: credentialsId, url: urlGit(server, 'ansible', 'logs.forwarder'))
        }
    }
    dir(platform) {
        body()
    }
}

/**
 * Création et checkout d'une branche à partir de la branche en cours
 * @param branch_name
 * @return
 */
def gitCreateBranch(branch_name) {
    def commande = "git checkout -b $branch_name"
    sh commande
    echo "Branche $branch_name créée."
}

/**
 * Checkout de la branche, définition du user et clean du repo local.
 *
 * @param source_branch
 * @param git_group_name Groupe Git dans lequel se trouve le projet
 * @param git_project_name Nom du projet Git dans le Groupe
 * @param git_credentials Crédentials Jenkins à utiliser pour se connecter au repo Git (forcé par les méthodes appelées ensuite)
 * @param git_server Nom du serveur (à définir dans ~/.ssh/config) pour utiliser des credentials différents si nécessaire
 * @return
 */
def initialiserRepoGit(source_branch, git_group_name, git_project_name, git_credentials, git_server) {
    // Récupération de la branche
    gitCallWithCredentialsAndGroupAndServer(source_branch, git_group_name, git_project_name, git_credentials, git_server)

    // Nettoyage du repo local
    // Suppression des branches locales non présentes en remote (job planté ?)
    def commande = "git reset --hard && git clean -d -x -f"
    sh commande
    echo "Repository local nettoyé."

    commande = "git config --global user.name \"Jenkins\""
    sh commande
    commande = "git config --global user.email ld-corp-devops-bnum@laposte.fr"
    sh commande
    echo "User initialisé."
}

/**
 * Push d'une branche dans Git.
 * @param branch_name
 * @param branch_only false s'il faut faire un add et un commit d'init avant de pusher la branche
 * @param simulation Oui pour ne pas réaliser le push ('add' et 'commit' seront réalisés).
 * @return
 */
def pushBranchToRepo(branch_name, branch_only, simulation, logDebug) {
    if (!branch_only) {
        sh "git add ."
        logDebug.debug("Modifications ajoutées au workspace.")

        sh "git commit -m \"Initialisation de la branche $branch_name\""
        logDebug.debug("Modifications committées dans le repo git local.")
    } else {
        logDebug.debug("Création simple de la branche '${branch_name}' sans commit préalable.")
    }

    if ( !simulation ) {
        // Push request en attente :
        //    (https://github.com/jenkinsci/credentials-binding-plugin/pull/18/files/f848c17061e2a81cc8e80d626bb01c9e333c4b4b)
        // A réactiver quand le code aura été accepté.
        //withCredentials([[$class: 'SSHUserPrivateKeyBinding', credentialsId: git_credentials]]) {
        //    sh "git push --set-upstream origin $nom_branche"
        //}

        // Ca fonctionne car https://superuser.com/questions/232373/how-to-tell-git-which-private-key-to-use
        // (cf. définition de la variable git_server)
        sh "git push --set-upstream origin $branch_name"
        logDebug.debug("Modifications pushées dans le repo git remote.")
    } else {
        echo "MODE SIMULATION : Push non effectué."
    }
}

/**
 * Merge d'une branche source dans le repo courant.
 * @param branch_source
 * @param branche_cible
 * @param simulation Oui pour ne pas réaliser le push ('add' et 'commit' seront réalisés).
 * @return
 */
def gitMerge(branch_source, branche_cible, simulation) {
    sh "git merge --no-edit $branch_source"
    echo "Merge de la branche $branch_source."

    if ( simulation.equalsIgnoreCase('Non') ) {
        sh "git push --set-upstream origin $branche_cible"
        echo "Modifications pushées dans le repo git remote."
    } else {
        echo "Push non effectué car on est en mode simulation"
    }
}

/**
 * extract conflict files.
 * @return
 */
def gitConflicts() {
    echo "extract conflict files"
    def result = sh(
                    script: "( git diff --name-only --diff-filter=U )",
                    returnStdout: true
                 )
    return result
}

/**
 * extract log infos.
 * @param affected file
 * @param option
 * @return
 */
def gitLogs(file, options) {
    echo "extract conflict causer (name and mail)"
    def cmd = "git log -n 1 ${options} ${file}"
    def result = sh(
                    script: cmd,
                    returnStdout: true
                 ).trim()
    return result
}

/**
 * extract log infos.
 * @param affected file
 * @param option
 * @return
 */
def gitImpexLogs(file, options) {
    echo "extract impexes related to the release"
    def cmd = "git log -n 1 ${options} | grep ${file} | awk '{print \$1}'"
    def result = sh(
                    script: cmd,
                    returnStdout: true
                 ).trim()
    return result
}


/**
 * revert conflicted merge.
 */
def gitAbortMerge() {
    echo "Revert merge"
    sh script: "git merge --abort"
}

/**
 * change branch.
 */
def gitChangeBranch(branch) {
    sh script: "git checkout "+ branch
}

/**
 * GetLastCommitNumber.
 */
def gitGetLastCommitNumber() {
    def result = sh(
                    script: "git log -1 | head -1 | awk -F ' ' '{ print \$2 }'",
                    returnStdout: true
                 ).trim()
    return result
}

/**
 * Affichage de toutes les variables envoyées par Gitlab.
 * @return
 */
def debugGitlabVars(logDebug) {
    if (logDebug.isDebugEnabled()) {
        sh script: 'env | grep "^git" | awk \'{print "DEBUG : "$0}\''
    }
}

def gitPushChanges(buildNbr, userName, userEmail, commitMsg, branch='master') {
     sh "git config --global user.email '${userEmail}'"
     sh "git config --global user.name '${userName}'"
     sh "git add *"
     sh "git commit -m'[jenkins-${BUILD_NUMBER}]: ${commitMsg}'"
     sh "git push --set-upstream origin ${branch}"
}
