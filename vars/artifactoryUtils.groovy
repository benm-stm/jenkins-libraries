// vars/artifactoryUtils.groovy

def download(remotePath, localPath) {
    sh "curl -s --noproxy --url http://boutique.masterbuild.net3-courrier.extra.laposte.fr/artifactory/simple/$remotePath --output $localPath"
}

/** Version pour les pipelines déclarative **/

/**
 * Permet d'effectuer des donwload, upload et récupérer des infos sur Artifactory.
 * @param action : valeur possible download, upload, info
 * @param pattern : source en local ou sur Artifactory suivant l'action
 * @param target : destination en local ou sur Artifactory suivant l'action
 */
def call(action, pattern, target = null, serverName = "masterbuild-artifactory-server") {
        def server = Artifactory.server(serverName)
        def spec = """{
                     "files": [ {
                        "pattern": "$pattern",
                        "target": "$target",
                        "flat": true
                      } ]
                    }"""
        // Si force == false alors on télécharge le fichier unqiuement s'il n'existe pas.
        if(action == "download") {
            //sh "mkdir -p $target"
            server.download(spec)
        }
        else if(action == "upload") {
            server.upload(spec)
        }
        else if(action == "exist") {
            return isExist(pattern)
        }
}

/**
 * Test si l'archive existe. On considère qu'une version SNAPSHOT n'existe pas.
 */
private boolean isExist(String pattern) {
    def listPattern = pattern.tokenize("/")

    def repo = listPattern.get(0)
    def file = listPattern.get(listPattern.size()-1)
    boolean exist = true
    if(file.indexOf("SNAPSHOT") == -1) {
        def url = "http://boutique.masterbuild.net3-courrier.extra.laposte.fr/artifactory/api/search/artifact?name=${file}&repos=${repo}"
        def curl = "curl --noproxy '*' -s '${url}'"
        def uris = sh(script: curl, returnStdout: true )
        def json = readJSON text:uris
        if(json.results.size() == 0) {
            exist = false
        }
    }
    return exist
}

