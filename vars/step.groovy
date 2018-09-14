// vars/config.groovy
//Deprecated

def loadJenkinsfile(project, nodeName = 'master', notify = false, to = null, action = '') {
    nodeUtils.nodeCode('nodeName': nodeName, 'notify': notify, 'to': to) {
        gitUtils.gitCallWithGroup("master", "devops", "eboutique.jenkins")
        loadScript(project + this.updateAction(action), "Jenkinsfile")
    }
}

def loadJenkinsfileParams(Map vars) {
    vars = vars ?: [:]
    nodeUtils.nodeCode('project': vars.get('project', JOB_NAME),
                       'nodeName': vars.get('nodeName', 'master'),
                       'notify': vars.get('notify', false),
                       'to': vars.get('to', null)) {
        gitUtils.gitCallWithGroup("master", "devops", "eboutique.jenkins")
        loadScriptParams(vars.get('project', JOB_NAME) + this.updateAction(vars.get('action')), "Jenkinsfile", vars)
    }
}

def loadJenkinsfile(Map vars) {
    this.loadJenkinsfile(vars.get('project'), vars.getOrDefault('nodeName', 'master'),
                         vars.getOrDefault('notify', false), vars.getOrDefault('to', null),
                         vars.getOrDefault('action', null))
}

def loadPrepare(action = '') {
    return this.loadScript(JOB_NAME + this.updateAction(action), "prepare.groovy")
}

def loadBuildImage(action = '') {
    return this.loadScript(JOB_NAME + this.updateAction(action), "build_image.groovy")
}

def loadDeploy(action = '') {
    return this.loadScript(JOB_NAME + this.updateAction(action), "deploy.groovy")
}

def loadFitnesse(action = '') {
    return this.loadScript(JOB_NAME + this.updateAction(action), "fitnesse.groovy")
}

private def loadScript(project, script) {
    def pipeline = load "workflows/${project.toLowerCase()}/${script}"
    return pipeline
}

private def loadScriptParams(project, script, vars) {
    def pipeline = load "workflows/${project.toLowerCase()}/${script}"
    return pipeline(vars)
}

private def updateAction(action) {
    if(action != '') {
        action = '/' + action
    }
    return action
}

