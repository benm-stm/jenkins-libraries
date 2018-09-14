// vars/deployUtils.groovy

/**
 * Lancement du processus complet de build :
 * - compilation de la branche passée en paramètre
 * - déploiement sur masterbuild
 * - tests fitnesse sur masterbuild
 * - déploiement sur la plateforme cible
 *
 * @param params les paramètres du job tels que fournis par Jenkins
 * @param branche la branche à compiler
 * @param machine la machine sur laquelle déployer si fitnesse est ok
 * @return
 */
def buildAll(params) {
    def branche = params.BRANCHE
    def machine = params.PLATEFORME
    def plateformeCible = machine as ENUM_PLATEFORMES

    // Compilation de la branche demandée
    def version = compiler(params)

    if ( params.FORCE.equalsIgnoreCase('Oui') ) {
        nodeUtils.stageWithTryCatch('stageName': ENUM_STAGES.DEPLOYER_INT.nom) {
            echo "Le déploiement a été forcé, on ne déploie pas sur Masterbuild"
        }
    } else {
        // Lancement du déploiement sur masterbuild pour exécuter les tests
        deployer(params, version, ENUM_PLATEFORMES.INT, ENUM_STAGES.DEPLOYER_INT)
    }

    // Lancement des tests fitnesse sur masterbuild
    def testToLaunch = ENUM_TEST_FITNESSE.DEPLOIEMENT
    def deployRecette = testFitnesse(params, ENUM_PLATEFORMES.INT, testToLaunch, ENUM_STAGES.TEST_PLATEFORME_INT, 80.0, true)

    if (deployRecette) {
        testToLaunch = ENUM_TEST_FITNESSE.SIMPLE
        deployRecette = testFitnesse(params, ENUM_PLATEFORMES.INT, testToLaunch, ENUM_STAGES.TEST_SIMPLE_INT, 80.0, false)
    } else {
        remonteErreur(params, "Pas de déploiement de la branche " + branche + " en version " + version + " sur " + machine + " car les tests fitnesse (" + testToLaunch.getDebugString() + ") ne sont pas concluants sur Masterbuild.")
    }
    echo "Continuer la livraison sur " + machine + " : " + deployRecette

    if (deployRecette) {
        // Lancement du déploiement sur la plateforme cible
        deployer(params, version, ENUM_STAGES.DEPLOYER)

        // Lancement des tests fitnesse sur la plateforme cible
        testToLaunch = ENUM_TEST_FITNESSE.DEPLOIEMENT
        def testOk = testFitnesse(params, plateformeCible, testToLaunch, ENUM_STAGES.TEST_PLATEFORME, 80.0, true)

        if (testOk) {
            testToLaunch = ENUM_TEST_FITNESSE.SIMPLE
            testOk = testFitnesse(params, plateformeCible, testToLaunch, ENUM_STAGES.TEST_SIMPLE, 70.0, false)

            if (testOk) {
                echo "La plateforme " + machine + " a été correctement déployée avec la version " + version + " de la branche " + branche
            } else {
                remonteErreur(params, "Les tests fitnesse (" + testToLaunch.getDebugString() + ") ont remontés des erreurs sur " + machine)
            }
        } else {
            remonteErreur(params, "Les tests fitnesse (" + testToLaunch.getDebugString() + ") ont remontés des erreurs sur " + machine)
        }
    } else {
        remonteErreur(params, "Pas de déploiement de la branche " + branche + " en version " + version + " sur " + machine + " car les tests fitnesse (" + testToLaunch.getDebugString() + ") ne sont pas concluants sur Masterbuild.")
    }
}

/**
 * Lancement du processus de build hotfix :
 * - compilation de la branche passée en paramètre
 * - déploiement sur la plateforme cible
 *
 * @param params les paramètres du job tels que fournis par Jenkins
 * @param branche la branche à compiler
 * @param machine la machine sur laquelle déployer si fitnesse est ok
 * @return
 */
def buildHotfix(params) {
    def branche = params.BRANCHE
    def machine = params.PLATEFORME
    def plateformeCible = machine as ENUM_PLATEFORMES
    def recette = params.PLATEFORME_CIBLE
    def hybris_synchro = params.HYBRIS_SYNCHRO
    def PF_recette = recette as ENUM_PLATEFORMES
    def revert_patch = params.REVERT_EXISTING_PATCH

    // Compilation de la branche demandée
    def version = compiler(params)
    echo ""
    // Lancement du déploiement sur la plateforme cible
    deployer(params, version, ENUM_STAGES.DEPLOYER)

    def result = build job: 'PATCHER_Try', parameters: [
            [$class: 'StringParameterValue', name: 'BRANCHE_RELEASE', value: branche],
            [$class: 'StringParameterValue', name: 'BRANCHE_HOTFIX', value: 'TOUS'],
            [$class: 'StringParameterValue', name: 'VERSION', value: version],
            [$class: 'StringParameterValue', name: 'PLATEFORME_UAT', value: plateformeCible],
            [$class: 'StringParameterValue', name: 'PLATEFORME_RECETTE', value: PF_recette],
            [$class: 'BooleanParameterValue', name: 'HYBRIS_SYNCHRO', value: hybris_synchro],
            [$class: 'BooleanParameterValue', name: 'REVERT_EXISTING_PATCH', value: revert_patch]
    ],
            propagate: true, // Si ce job plante, le job chapeau plante aussi.
            wait: true

}

/**
 * Lancement d'une suite de tests sur la plateforme en paramètre.
 *
 * @param plateforme plateforme sur laquelle lancer la suite de test
 * @param typeTest le type de test fitnesse à lancer
 * @param stage le nom du stage dans le CR Jenkins des pipelines
 * @param seuilOk le pourcentage indiquant la réussite d'un test
 * @param dWarmup true pour effectuer un pré-chargement des pages afin d'éviter les timeouts fitnesse
 * @return
 */
def testFitnesse(params, ENUM_PLATEFORMES plateforme, ENUM_TEST_FITNESSE typeTest, ENUM_STAGES stage, seuilOk, doWarmup) {
    if (doWarmup) {
        // Pré-chargement des pages afin d'éviter les timeouts
        warmup(params, plateforme)
    }

    nodeUtils.stageWithTryCatch('stageName': stage.nom) {

        if ( params.FORCE.equalsIgnoreCase('Oui') ) {
            echo "Le déploiement a été forcé, on n'exécute pas les tests fitnesse (" + typeTest.getDebugString() + ") sur " + plateforme
            return true
        }

        def message = "Lancement des tests fitnesse (" + typeTest.getDebugString() + ") sur " + plateforme
        notifier(params, stage.nom, message, "VERT", false)

        // Lancement du job fitnesse
        def result = build job: ENUM_JOBS.TEST_FITNESSE.jobName, parameters: [
                [$class: 'StringParameterValue', name: 'FITNESSE_HOST', value: 'boutique.uat.net3-courrier.extra.laposte.fr'],
                [$class: 'StringParameterValue', name: 'FITNESSE_PORT', value: '8082'],
                [$class: 'StringParameterValue', name: 'FITNESSE_TEST_NAME', value: typeTest.nom],
                [$class: 'StringParameterValue', name: 'FITNESSE_TEST_TYPE', value: 'suite'],
                [$class: 'StringParameterValue', name: 'FITNESSE_EBOUTIQUE_PART_SITE_URL', value: plateforme.urlFrontPart],
                [$class: 'StringParameterValue', name: 'FITNESSE_EBOUTIQUE_PRO_SITE_URL', value: plateforme.urlFrontPro],
                [$class: 'StringParameterValue', name: 'FITNESSE_BOUTIQUE_URL_PREFIX', value: 'https%3A%2F%2F'],
                [$class: 'StringParameterValue', name: 'FITNESSE_HOST_PROFILE', value: 'linux'],
                [$class: 'StringParameterValue', name: 'FITNESSE_PROXY_TYPE', value: '1'],
                [$class: 'StringParameterValue', name: 'FITNESSE_PROXY_HOST', value: 'localhost'],
                [$class: 'StringParameterValue', name: 'FITNESSE_PROXY_PORT', value: '8888'],
                [$class: 'StringParameterValue', name: 'FITNESSE_PROXY_IGNORE', value:  'localhost,127.0.0.1,*.net3-courrier.extra.laposte.fr,*.net2-courrier.extra.laposte.fr'],
                [$class: 'BooleanParameterValue', name: 'FITNESSE_PROXY_SHARE', value: true]
        ],
                propagate: false,
                wait: true

        message = "Fin du lancement des tests fitnesse (" + typeTest.getDebugString() + ") sur " + plateforme
        notifier(params, stage.nom, message, "VERT", false)

        // Récupération des résultats afin de savoir si on peut déployer sur la plateforme cible
        def jobNumber = result.getNumber()
        def fileToAnalyze = "/data/jenkins/jobs/" + ENUM_JOBS.TEST_FITNESSE.jobName + "/builds/" + jobNumber + "/build.xml"

        def result_ok = extractValues(fileToAnalyze, "right") as Integer
        echo "Nombre de tests ok : " + result_ok

        def result_ko = extractValues(fileToAnalyze, "wrong") as Integer
        echo "Nombre de tests ko : " + result_ko

        def result_ignored = extractValues(fileToAnalyze, "ignored") as Integer
        echo "Nombre de tests ignorés : " + result_ignored

        def result_exception = extractValues(fileToAnalyze, "exceptions") as Integer
        echo "Nombre d'exceptions : " + result_exception

        def all_tests = result_ok + result_ko + result_ignored + result_exception
        echo "Nombre total de tests : " + all_tests

        def percent_ok = Math.ceil((result_ok * 100) / all_tests)
        echo "%age de réussite : " + percent_ok
        echo "seuil défini : " + seuilOk

        def resultatTest = (percent_ok > seuilOk)
        echo "Résultat du test : " + resultatTest

        return resultatTest
    }
}

/**
 * Lecture d'une valeur de résultat de test particulière dans le compte-rendu de test fitnesse
 *
 * @param file le compte-rendu de test à analyser
 * @param balise la valeur à y lire
 * @return
 */
def extractValues(String file, String balise) {
    def XPATH_STATS_FITNESSE = "/build/actions/hudson.plugins.fitnesse.FitnesseResultsAction/results/pageCounts/"

    value = sh (
            script: "set +x && xmllint -xpath '" + XPATH_STATS_FITNESSE + balise + "' " + file + " | sed 's/<" + balise + ">//g' | sed 's/<\\/" + balise + ">//g'",
            returnStdout: true
    ).trim()

    return value
}

/**
 * Déploiement sur la plateforme choisie dans Jenkins.
 *
 * @param params les paramètres du job
 * @param version la version à déployer
 * @param plateforme la plateforme sur laquelle déployer (de type String)
 * @return
 */
def deployer(params, version, ENUM_STAGES stage) {
    ENUM_PLATEFORMES enumPlateforme = params.PLATEFORM as ENUM_PLATEFORMES

    deployer(params, version, enumPlateforme, stage)
}

/**
 * Déploiement sur la plateforme passée en paramètre.
 *
 * @param params les paramètres du job
 * @param version la version à déployer
 * @param plateforme la plateforme sur laquelle déployer (de type ENUM_PLATEFORMES)
 * @return
 */
def deployer(params, version, ENUM_PLATEFORMES enumPlateforme, ENUM_STAGES stage) {
    nodeUtils.stageWithTryCatch('stageName': stage.nom) {

        def message = "Début du déploiement de la branche " + params.BRANCHE + " en version " + version + " sur " + enumPlateforme
        notifier(params, stage.nom, message, "VERT", false)

        if (version) {
            echo 'Déploiement de la version ' + version
            def jobName = enumPlateforme.jobDeploiement.jobName

            if (params.SIMULATION == 'Non') {
                echo 'Lancement du déploiement avec le job ' + jobName
                build job: jobName, parameters: [
                        [$class: 'StringParameterValue', name: 'PLATEFORME', value: enumPlateforme.name()],
                        [$class: 'StringParameterValue', name: 'VERSION', value: version]
                ],
                        propagate: true, // Si ce job plante, le job chapeau plante aussi.
                        wait: true // On attend la fin de ce job avant de continuer
            } else {
                echo 'Déploiement de la version ' + version + ' non lancé car on est en mode simulation.'
            }
        } else {
            remonteErreur(params, "Déploiement non effectué car aucune version à déployer n'a pas été trouvée dans le fichier de résultat de la compilation.")
        }

        message = "Fin du déploiement de la branche " + params.BRANCHE + " en version " + version + " sur " + enumPlateforme
        notifier (params, stage.nom, message, "VERT", false)
    }

    // Attente du démarrage du serveur
    nodeUtils.stageWithTryCatch('stageName': ENUM_STAGES.ATTENDRE.nom) {
        def urlTest = enumPlateforme.urlFrontPart

        message = "Attente du démarrage des serveurs de la ${enumPlateforme} (branche ${params.BRANCHE} en version ${version})."
        notifier (params, stage.nom, message, "VERT", false)

        timeout(time: 15, unit: 'MINUTES') {
            waitUntil {
                echo 'Attente du démarrage des serveurs ...'
                def serveurOk = sh script: 'wget --no-proxy --no-check-certificate ' + urlTest + ' -O /dev/null', returnStatus: true
                return (serveurOk == 0)
            }
        }

        message = "Serveurs de la ${enumPlateforme} démarrés (branche ${params.BRANCHE} en version ${version})."
        notifier (params, stage.nom, message, "VERT", false)
    }
}

/**
 * Compilation de la branche passée dans les paramètres Jenkins.
 *
 * @param params les paramètres du job
 * @return
 */
def compiler(params) {
    nodeUtils.stageWithTryCatch('stageName': ENUM_STAGES.COMPILER.nom) {

        def branche = params.BRANCHE
        def jobNumber = "lastSuccessfulBuild"
        def jobCompilation = ENUM_JOBS.COMPILER.jobName

        def message = "Début de la compilation de la branche " + branche
        notifier(params, ENUM_STAGES.COMPILER.nom, message, "VERT", false)

        if (params.SIMULATION == 'Non') {
            echo 'Lancement de la compilation via le job ' + jobCompilation
            def result = build job: jobCompilation, parameters: [
                    [$class: 'StringParameterValue', name: 'BRANCHE', value: branche],
                    [$class: 'StringParameterValue', name: 'MODULES', value: 'TOUS']
            ],
                    propagate: true, // Si ce job plante, le job chapeau plante aussi.
                    wait: true // On attend la fin de ce job avant de continuer

            jobNumber = result.getNumber()

        } else {
            echo 'Compilation non lancée car on est en mode simulation.'
        }

        echo 'Fin de la compilation.'

        // Détermination de la version à déployer.
        // On va aller chercher dans les logs du job 'COMPILER', lancé juste avant, la ligne "Version a publier : 05.02.00.001"
        def logFile = "/data/jenkins/jobs/" + jobCompilation + "/builds/" + jobNumber + "/log"
        File file = new File(logFile)
        def version = file.text.find(/(\d{2})\.(\w{2})\.(\w{2})\.(\d{3})/)

        message = "Fin de la compilation de la branche " + branche + ". Version : " + version
        notifier(params, ENUM_STAGES.COMPILER.nom, message, "VERT", false)

        return version
    }
}

/**
 * Appel de pages prédéfinies sur la plateforme afin de charger les caches, compiler les JSPs, ...
 *
 * @param params les paramètres du job
 * @param enumPlateforme la plateforme sur laquelle lancer le warmup
 * @return
 */
def warmup(params, ENUM_PLATEFORMES enumPlateforme) {
    nodeUtils.stageWithTryCatch('stageName': ENUM_STAGES.WARMUP.nom) {

        message = "Début du pré-loading des pages afin d'éviter les timeouts"
        notifier(params, ENUM_STAGES.WARMUP.nom, message, "VERT", false, true, true, false)

        // Création d'un script shell de warmup afin d'éviter la limitation de Groovy Pipeline avec des boucles qui appellent
        // des méthodes asynchrones (impossible d'appeler sh ou httpRequest dans une boucle).
        def fileName = workspace + "/warmup.sh"
        echo "Suppression du fichier " + fileName
        File file = new File(fileName)
        file.delete()
        echo "Création du fichier " + fileName
        file = new File(fileName)
        echo "Fichier " + fileName + " initialisé."

        // Pages de login
        addLoginCCU(file, enumPlateforme)

        // Pages particuliers
        for (ENUM_PAGES_WARMUP_PART pagePart : ENUM_PAGES_WARMUP_PART.values()) {
            addUrlToCall(file, enumPlateforme.urlFrontPart, pagePart, "-b loginCookiesPart")
        }
        // Pages pros
        for (ENUM_PAGES_WARMUP_PRO pagePro : ENUM_PAGES_WARMUP_PRO.values()) {
            addUrlToCall(file, enumPlateforme.urlFrontPro, pagePro, "-b loginCookiesPro")
        }

        // Lancement du script de warmup : si ça plante, on ne plante pas le job.
        sh 'sh ' + fileName + ' || true'

        message = "Fin du pré-loading des pages"
        notifier(params, ENUM_STAGES.WARMUP.nom, message, "VERT", false, true, true, false)
    }
}

def addLoginCCU(file, enumPlateforme) {
    // Positionnement du proxy pour les appels CCU
    file << "export http_proxy='http://221.128.58.183:3128'\n"
    file << "export https_proxy='http://221.128.58.183:3128'\n"
    file << "export no_proxy='" + getUrlNoProxy(enumPlateforme) + "'\n"
    file << "rm -f loginCookiesPart loginCookiesPro\n"

    file << "echo 'Appel de la page : " + enumPlateforme.urlLoginCCU + " pour un particulier.'\n"
    file << "curl -c loginCookiesPart -m 600 -L --insecure --data '" + enumPlateforme.paramLoginPart + "' " + enumPlateforme.urlLoginCCU + " > /dev/null\n"

    file << "echo 'Appel de la page : " + enumPlateforme.urlLoginCCU + " pour un pro.'\n"
    file << "curl -c loginCookiesPro -m 600 -L --insecure --data '" + enumPlateforme.paramLoginPro + "' " + enumPlateforme.urlLoginCCU + " > /dev/null\n"
}

def addUrlToCall(file, racine, pagePart, options) {
    def pageName = racine + pagePart.path
    file << "echo 'Appel de la page : " + pageName + "'\n"
    file << "curl " + options + " -m 600 -L --insecure " + pageName + " > /dev/null\n"
    echo "Ajout de l'url au script de warmup : " + pageName
}

/**
 * Les URLs part et pro de la plateforme en paramètre ne doivent pas passer par le proxy.
 * @param enumPlateforme
 * @return
 */
def getUrlNoProxy(enumPlateforme) {
    def matcher = enumPlateforme.urlFrontPart =~ '.+://(.+)/'
    def urlPart = matcher ? matcher[0][1] : null
    matcher = enumPlateforme.urlFrontPro =~ '.+://(.+)/'
    def urlPro = matcher ? matcher[0][1] : null

    return urlPart + "," + urlPro
}

/**
 * Arrêt du job suite à une erreur.
 *
 * @param message
 */
def remonteErreur(params, message) {
    notifier(params, 'Erreur', message, "ROUGE", true)
    throw new Exception(message)
}

/**
 * Envoi du message via les différents canaux de communication.
 *
 * @param params Les paramètres d'origine du job
 * @param titre Le titre du message (canal chriscom)
 * @param message Le message (canaux chriscom + mattermost)
 * @param couleur la couleur (canal chriscom)
 * @param buzz activer le bip ou non (canal chriscom)
 * @return
 */
def notifier(params, titre, message, couleur, buzz) {
    notifier (params, titre, message, couleur, buzz, true, true, true)
}

def notifier(params, titre, message, couleur, buzz, displayEcho, displayDevops, displayMattermost) {
    if (displayEcho && message) {
        echo message
    }
    if (displayDevops) {
        utils.sendMessageToDevops('Jenkins', titre, message, couleur, buzz)
    }
    if (displayMattermost) {
        utils.sendMessageToMattermost(message, params.PROJET)
    }
}

/**
 * Lancement de tests spécifiques sur une plateforme spécifique sans conditions particulières avec pour but d'afficher
 * les résultats dans bavardages.
 * @param params
 * @return
 */
def testFitnesseTemp(params) {
    def mattermost_id = utils.key_value_parser("DevOps/Deployment/mattermost.properties", params.PROJET)
    def delai = 3
    try {
        timeout(time: delai, unit: 'HOURS') {
            testFitnesseTempTimeout(params)
        }
    } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException timeoutReached) {
        def message = "Les tests fitnesse ont été annulés car ils n'étaient pas terminés au bout de ${delai} heures."
        //notifier(params, 'Fitnesse', message, "ROUGE", false, true, false, true)
        utils.sendMattermostMessage(message, mattermost_id)
    }
}

private testFitnesseTempTimeout(params) {
    def mattermost_id = utils.key_value_parser("DevOps/Deployment/mattermost.properties", params.PROJET)
    ENUM_PLATEFORMES plateforme = params.PLATEFORM as ENUM_PLATEFORMES
    ENUM_TEST_FITNESSE typeTest = ENUM_TEST_FITNESSE.DEPLOIEMENT

    def notifEcho = true
    def notifDevops = false
    def notifBavardages = true

    def message = "Début du lancement des tests fitnesse (${typeTest.getDebugString()}) sur ${plateforme}"
    utils.sendMattermostMessage(message, mattermost_id)

    // Lancement du job fitnesse
    def result = build job: ENUM_JOBS.TEST_FITNESSE.jobName, parameters: [
            [$class: 'StringParameterValue', name: 'FITNESSE_HOST', value: '215.5.112.86'],
            [$class: 'StringParameterValue', name: 'FITNESSE_PORT', value: '8183'],
            [$class: 'StringParameterValue', name: 'FITNESSE_TEST_NAME', value: typeTest.nom],
            [$class: 'StringParameterValue', name: 'FITNESSE_TEST_TYPE', value: 'suite'],
            [$class: 'StringParameterValue', name: 'FITNESSE_EBOUTIQUE_PART_SITE_URL', value: plateforme.urlFrontPart],
            [$class: 'StringParameterValue', name: 'FITNESSE_EBOUTIQUE_PRO_SITE_URL', value: plateforme.urlFrontPro],
            [$class: 'StringParameterValue', name: 'FITNESSE_BOUTIQUE_URL_PREFIX', value: 'https%3A%2F%2F'],
            [$class: 'StringParameterValue', name: 'FITNESSE_HOST_PROFILE', value: 'linux'],
            [$class: 'StringParameterValue', name: 'FITNESSE_PROXY_TYPE', value: '1'],
            [$class: 'StringParameterValue', name: 'FITNESSE_PROXY_HOST', value: '221.128.58.183'],
            [$class: 'StringParameterValue', name: 'FITNESSE_PROXY_PORT', value: '3128'],
            [$class: 'StringParameterValue', name: 'FITNESSE_PROXY_IGNORE', value:  'localhost,127.0.0.1,*.net3-courrier.extra.laposte.fr,*.net2-courrier.extra.laposte.fr,215.5.112.86'],
            [$class: 'BooleanParameterValue', name: 'FITNESSE_PROXY_SHARE', value: true]
    ],
            propagate: false,
            wait: true

    message = "Fin du lancement des tests fitnesse (${typeTest.getDebugString()}) sur ${plateforme}"
    utils.sendMattermostMessage(message, mattermost_id)

    // Récupération des résultats
    def jobNumber = result.getNumber()
    def fileToAnalyze = "/data/jenkins_master_volume/jobs/EBoutique/jobs/EBOUTIQUE_FITNESSE/builds/${jobNumber}/build.xml"

    def urlFitnesse = "https://tools.picbnum.fr/service/jenkins/job/EBoutique/job/EBOUTIQUE_FITNESSE/${jobNumber}/fitnesseReport/fitnesse-results-${jobNumber}.xml/"

    def result_ok = extractValues(fileToAnalyze, "right") as Integer
    def result_ko = extractValues(fileToAnalyze, "wrong") as Integer
    def result_ignored = extractValues(fileToAnalyze, "ignored") as Integer
    def result_exception = extractValues(fileToAnalyze, "exceptions") as Integer
    def all_tests = result_ok + result_ko + result_ignored + result_exception
    def percent_ok = Math.ceil((result_ok * 100) / all_tests)

    // Affichage des résultats
    def messageResultats = "[Bilan du lancement](${urlFitnesse}) de la suite de test ${typeTest.nom} sur la ${plateforme} :\n"
    messageResultats += "Sur ${all_tests} tests lancés, ${percent_ok}%25 de réussite :\n"
    messageResultats += "* ${result_ok} sont ok\n"
    messageResultats += "* ${result_ko} sont ko\n"
    messageResultats += "* ${result_ignored} ont été ignorés\n"
    messageResultats += "* ${result_exception} ont renvoyé une exception\n"
    messageResultats += "Fin des tests fitnesse.\n"
    utils.sendMattermostMessage(messageResultats, mattermost_id)
}

enum ENUM_JOBS {
    DEPLOYER_ANSIBLE('DEPLOYER_ANSIBLE'),
    DEPLOYER_PUPPET('DEPLOYER_PUPPET'),
    DEPLOYER_PUPPET_V2('DEPLOYER_PUPPET_V2'),
    COMPILER('COMPILER'),
    TEST_FITNESSE('/EBoutique/EBOUTIQUE_FITNESSE')

    String jobName

    ENUM_JOBS(String jobName) {
        this.jobName = jobName
    }
}

enum ENUM_PLATEFORMES {
    INT(ENUM_JOBS.DEPLOYER_ANSIBLE, "http://boutique.masterbuild.net3-courrier.extra.laposte.fr/", "http://boutique.pro.masterbuild.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=http%3A%2F%2Fboutique.masterbuild.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=http%3A%2F%2Fboutique.masterbuild.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclientpro_TA%40yopmail.com&password=hardis01&tunnelSteps=&client_id=eboutique&usertype=PRO&callback_url=http%3A%2F%2Fboutique.pro.masterbuild.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=http%3A%2F%2Fboutique.pro.masterbuild.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check"),
    PF5(ENUM_JOBS.DEPLOYER_ANSIBLE, "https://www.internet.eboutique-ca.net3-courrier.extra.laposte.fr/", "https://www.internet.pro.eboutique-ca.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fwww.internet.eboutique-ca.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.eboutique-ca.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclientpro_TA%40yopmail.com&password=hardis01&tunnelSteps=&client_id=eboutique&usertype=PRO&callback_url=https%3A%2F%2Fwww.internet.pro.eboutique-ca.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.pro.eboutique-ca.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check"),
    PF6(ENUM_JOBS.DEPLOYER_ANSIBLE, "https://www.internet.eboutique-cb.net3-courrier.extra.laposte.fr/", "https://www.internet.pro.eboutique-cb.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fwww.internet.eboutique-cb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.eboutique-cb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclientpro_TA%40yopmail.com&password=hardis01&tunnelSteps=&client_id=eboutique&usertype=PRO&callback_url=https%3A%2F%2Fwww.internet.pro.eboutique-cb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.pro.eboutique-cb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check"),
    UAT1(ENUM_JOBS.DEPLOYER_ANSIBLE, "https://boutique.uat1.net3-courrier.extra.laposte.fr/", "https://boutique.pro.uat1.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fboutique.uat1.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fboutique.uat1.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclientpro_TA%40yopmail.com&password=hardis01&tunnelSteps=&client_id=eboutique&usertype=PRO&callback_url=https%3A%2F%2Fboutique.pro.uat1.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fboutique.pro.uat1.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check"),
    UAT2(ENUM_JOBS.DEPLOYER_ANSIBLE, "https://boutique.uat2.net3-courrier.extra.laposte.fr/", "https://boutique.pro.uat2.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fboutique.uat2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fboutique.uat2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclientpro_TA%40yopmail.com&password=hardis01&tunnelSteps=&client_id=eboutique&usertype=PRO&callback_url=https%3A%2F%2Fboutique.pro.uat2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fboutique.pro.uat2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check"),
    PF1A(ENUM_JOBS.DEPLOYER_PUPPET, "https://www.internet.int-eboutique2.net3-courrier.extra.laposte.fr/", "https://www.internet.pro.int-eboutique2.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fwww.internet.int-eboutique2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.int-eboutique2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclientpro_TA%40yopmail.com&password=hardis01&tunnelSteps=&client_id=eboutique&usertype=PRO&callback_url=https%3A%2F%2Fwww.internet.pro.int-eboutique2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.pro.int-eboutique2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check"),
    PF1B(ENUM_JOBS.DEPLOYER_PUPPET, "https://www.internet.eboutique-ba.net3-courrier.extra.laposte.fr/", "https://www.internet.pro.eboutique-ba.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fwww.internet.eboutique-ba.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.eboutique-ba.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclientpro_TA%40yopmail.com&password=hardis01&tunnelSteps=&client_id=eboutique&usertype=PRO&callback_url=https%3A%2F%2Fwww.internet.pro.eboutique-ba.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.pro.eboutique-ba.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check"),
    PF2A(ENUM_JOBS.DEPLOYER_PUPPET, "https://www.internet.eboutique2.net3-courrier.extra.laposte.fr/", "https://www.internet.pro.eboutique2.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fwww.internet.eboutique2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.eboutique2.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fwww.internet.eboutique-bb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.eboutique-bb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check"),
    PF2B(ENUM_JOBS.DEPLOYER_PUPPET, "https://www.internet.eboutique-bb.net3-courrier.extra.laposte.fr/", "https://www.internet.pro.eboutique-bb.net3-courrier.extra.laposte.fr/", "https://integration.compte.laposte.fr/ccu/sso/login", "login-type=normal&username=compteclient_TA%40yopmail.com&password=hardis01&tunnelSteps=&usertype=PART&client_id=eboutique&callback_url=https%3A%2F%2Fwww.internet.eboutique-bb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.eboutique-bb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check", "login-type=normal&username=compteclientpro_TA%40yopmail.com&password=hardis01&tunnelSteps=&client_id=eboutique&usertype=PRO&callback_url=https%3A%2F%2Fwww.internet.pro.eboutique-bb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check&login_url=https%3A%2F%2Fwww.internet.pro.eboutique-bb.net3-courrier.extra.laposte.fr%2FeboutiqueCallback%2Flogin_check")

    ENUM_JOBS jobDeploiement
    String urlFrontPart
    String urlFrontPro
    String urlLoginCCU
    String paramLoginPart
    String paramLoginPro

    ENUM_PLATEFORMES(ENUM_JOBS jobDeploiement, String urlFrontPart, String urlFrontPro, String urlLoginCCU, String paramLoginPart, String paramLoginPro) {
        this.jobDeploiement = jobDeploiement
        this.urlFrontPart = urlFrontPart
        this.urlFrontPro = urlFrontPro
        this.paramLoginPart = paramLoginPart
        this.paramLoginPro = paramLoginPro
        this.urlLoginCCU = urlLoginCCU
    }
}

enum ENUM_PAGES_WARMUP_PART {
    TABLEAU_DE_BORD("tableau-de-bord"),
    HOME(""),
    AUTHENTIFICATION("authentification"),
    TELECHARGER_LRE("envoi-de-courrier-en-ligne/lettre-recommandee-en-ligne/telecharger-document"),
    TELECHARGER_LEE("envoi-de-courrier-en-ligne/lettre-en-ligne/telecharger-document"),
    REEXPEDITION("reexpedition-garde-de-courrier/reexpedition-temporaire-nationale/saisie-contrat"),
    VIGNETTE("affranchissement-a-domicile/vignette-recommandee-en-ligne/votre-envoi"),
    COLISSIMO("affranchissement-a-domicile/colissimo-en-ligne/votre-envoi"),
    MTEL("affranchissement-a-domicile/mon-timbre-en-ligne/personalisation?action=reprise")

    String path

    ENUM_PAGES_WARMUP_PART(String path) {
        this.path = path
    }
}

enum ENUM_PAGES_WARMUP_PRO {
    TABLEAU_DE_BORD("tableau-de-bord"),
    HOME(""),
    AUTHENTIFICATION("authentification"),
    AFFRANCHISSEMENT("affranchissement-en-entreprise/colissimo-en-ligne/votre-colis"),
    TELECHARGER_LRE("envoi-de-courrier-en-ligne/lettre-recommandee-en-ligne/telecharger-document"),
    TELECHARGER_LEE("envoi-de-courrier-en-ligne/lettre-en-ligne/telecharger-document"),
    MTEL("affranchissement-en-entreprise/mon-timbre-en-ligne/personalisation?action=reprise")

    String path

    ENUM_PAGES_WARMUP_PRO(String path) {
        this.path = path
    }
}

enum ENUM_TEST_FITNESSE {
    LOGIN("BoutiqueLaPoste.SuiteDeTestsDeploiement.TcaseVerificationAccesCompteClientPart"),
    DEPLOIEMENT("BoutiqueLaPoste.SuiteDeTestsDeploiement"),
    SIMPLE("BoutiqueLaPoste.SuiteDeTestsParcoursDeValidationTnr.TnrSimple")

    String nom

    ENUM_TEST_FITNESSE(String nom) {
        this.nom = nom
    }

    def getDebugString() {
        return this.name() + " : " + this.nom
    }
}

enum ENUM_STAGES {
    COMPILER("Compiler"),
    DEPLOYER("Deployer"),
    DEPLOYER_INT("DeployerMB"),
    ATTENDRE("Attendre"),
    TEST_PLATEFORME("Valider"),
    TEST_PLATEFORME_INT("ValiderMB"),
    TEST_SIMPLE("Tester"),
    TEST_SIMPLE_INT("TesterMB"),
    WARMUP("Préparer")

    String nom

    ENUM_STAGES(String nom) {
        this.nom = nom
    }
}
