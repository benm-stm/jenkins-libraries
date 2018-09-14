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
        gitUtils.gitCallWithMap(branch: branche, project: "eboutique.custom", server: gitUtils.urlExploitGit())

        echo "Récupération des sources d'hybris"
        sh 'curl -o hybris-sources-6.4.0-p0-package.zip http://boutique.masterbuild.net3-courrier.extra.laposte.fr/artifactory/simple/libs-release-local/com/laposte/eboutique/hybris-sources/6.4.0-p0/hybris-sources-6.4.0-p0-package.zip'

        echo "Décompression des sources"
        sh 'unzip -n -q hybris-sources-6.4.0-p0-package.zip'

        echo "Suppression de l'archive"
        sh 'rm -Rf hybris-sources-6.4.0-p0-package.zip'

        dir('hybris/bin/platform'){
            withEnv(["PATH=${tool 'ant-1.9.1'}/bin:${env.PATH}"]) {
                // build and compile project
                sh 'ant clean'
                sh 'ant customize'
                sh 'ant all'
            }
        }

    }
}

private analyser(version) {
    def url_sonar = "http://sonarsonaqube-lts.marathon.l4lb.thisdcos.directory:9000/sonar-lts"

    dir('sources/hybris/bin/platform'){
        withEnv(["PATH=${tool 'ant-1.9.1'}/bin:${env.PATH}"]) {
            // execute sonar scanner (-Dsonar.scm.disabled=True)
            sh "ant sonar -Dsonar.host.url=$url_sonar -Dsonar.login=eboutique -Dsonar.password=changeit -Dsonar.projectVersion=${version}"
        }
    }
}
