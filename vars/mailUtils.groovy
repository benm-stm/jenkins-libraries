// vars/mailUtils.groovy

def sendFailedMessage(to, error) {
    sendMessage(to, 'failed', "\n\n ERROR: ${error.message}")
}

def sendStartMessage(to) {
    sendMessage(to, 'started', '')
}

def sendEndMessage(to) {
    sendMessage(to, 'finished', '')
}

private def sendMessage(to, message, error) {
    mail (
        subject: "Jenkins - Build ${message} in ${env.JOB_NAME} #${env.BUILD_NUMBER}",
        to: to,
        body: "Build URL: ${JENKINS_URL}/job/${env.JOB_NAME}/${env.BUILD_NUMBER}/ ${error}"
    )
}

def sendCustomMail(to, subject, content, cc = "") {
    def mailFooter = "<br>L’équipe <strong><span style='color: #ff6600;'>DEV</span><span style='color: #008080;'>OPS</span></strong><br><font size='1'>devops-bnum@laposte.net / <a href='https://bavardages.picbnum.fr/'>Bavardages ^</a></font>"
    def mailHeader = "<body style='font-size:11pt;font-family:Calibri'><h1><strong><span style='color: #ff6600;'>DEV</span><span style='color: #008080;'>OPS</span></strong></h1>"
    // En attendant de trouver pourquoi les personnes en cc ne reçoivent pas le mail
    def destinataires = "${to},${cc}"
    mail (
        subject: subject,
        to: destinataires,
        cc: cc,
        mimeType: 'text/html',
        body: mailHeader+"<p style='border: 15px solid;border-color: #00bff9;padding: 0 15px; width:60%'><br>"+content+"<br>"+mailFooter+"</p>"
    )
}

/**
 * Send notifications based on build status string
 */
def sendMessageDeclarative(String buildStatus='STARTED', String destinataire='ld-corp-devops-bnum@laposte.fr', String content='', String cc='') {
    // build status of null means successful
    buildStatus = buildStatus ?: 'SUCCESS'

    // Default values
    def colorName = 'RED'
    def colorCode = '#FF0000'

    def mailFooter = "<br>L’équipe <strong><span style='color: #ff6600;'>DEV</span><span style='color: #008080;'>OPS</span></strong><br><font size='1'>ld-corp-devops-bnum@laposte.fr / <a href='https://bavardages.picbnum.fr/'>Bavardages ^</a></font>"
    def mailHeader = "<body style='font-size:11pt;font-family:Calibri'><h1><strong><span style='color: #ff6600;'>DEV</span><span style='color: #008080;'>OPS</span></strong></h1>"
  
    //def destinataire = 'ld-corp-devops-bnum@laposte.fr',
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"

    if (content == "") {
        content = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':<br>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;<br><br>"
    }

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        colorName = 'YELLOW'
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESS') {
        colorName = 'GREEN'
        colorCode = '#00FF00'
    }

    emailext (
        to: destinataire,
        cc: cc,
        mimeType: 'text/html',
        subject: subject,
        body: mailHeader+"<p style='border: 15px solid;border-color: ${colorCode};padding: 0 15px; width:60%'><br>"+content+"<br>"+mailFooter+"</p>"
    )
}
