// vars/action.groovy

class action implements Serializable {
    private String build = "build"
    private String buildImage = "build_image"
    private String deploy = "deploy"
    def getBuild() {
        build
    }
    def getBuildImage() {
        buildImage
    }
    def getDeploy() {
        deploy
    }
}