// vars/sonarUtils.groovy

// Recopie et adaptation du job existant de l'ancien Jenkins.
// TODO : à améliorer.

def analyseSonar(branche, version) {
    // Exécution des traitements
    nodeUtils.nodeCode('project': env.JOB_NAME, 'nodeName': 'ansible-java8') {
        nodeUtils.stageWithTryCatch('stageName': 'Préparer') {
            preparer(branche)
        }

        nodeUtils.stageWithTryCatch('stageName': 'Analyser') {
            analyser(version)
        }
    }
}

private preparer(branche) {
    sh 'mkdir -p ./sources'

    dir('sources/') {

        echo "Suppression des anciens fichiers de build."
        sh 'rm -Rf *'

        echo "Récupération de la branche $branche."
        gitUtils.gitCallWithMapLpfr(branch: branche, project: "laposte.fr", server: gitUtils.urlExploitGit())

    }
}

private analyser(version) {
    def url_sonar = "http://sonarsonaqube-lts.marathon.l4lb.thisdcos.directory:9000/sonar-lts"

    dir('sources/laposte.fr-env'){
        withEnv(["PATH=${tool 'ant-1.9.1'}/bin:${env.PATH}"]) {
            // execute sonar scanner (-Dsonar.scm.disabled=True)
            sh "ant sonar -Dsonar.host.url=$url_sonar -Dsonar.login=lpfr -Dsonar.password=changeit -Dsonar.projectVersion=${version}"
        }
    }
}
