// vars/loadUtils.groovy

def jenkinsfile(Map vars = [:]) {
    nodeUtils.nodeCode('project': env.JOB_NAME,
            'nodeName': vars.get('nodeName', 'master'),
            'notify': vars.get('notify', false),
            'to': vars.get('to', null)) {
        loadScript(env.JOB_NAME, "Jenkinsfile")
    }
}

def prepare() {
    return this.loadScript(env.JOB_NAME, "prepare.groovy")
}

def buildImage() {
    return this.loadScript(env.JOB_NAME, "build_image.groovy")
}

def deploy() {
    return this.loadScript(env.JOB_NAME, "deploy.groovy")
}

def fitnesse() {
    return this.loadScript(env.JOB_NAME, "fitnesse.groovy")
}

def publish() {
    return this.loadScript(env.JOB_NAME, "publish.groovy")
}

private loadScript(project, script) {
    def pipeline = load "${getPathLibs()}/workflows/${project.toLowerCase()}/${script}"
    return pipeline
}

def getPathLibs() {
    return "${env.JENKINS_HOME}/workspace/${env.JOB_NAME}@libs/eboutique.jenkins"
}
