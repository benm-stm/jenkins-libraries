// vars/eboutiqueUtils.groovy

def initialiserImpex(version_impex) {
    // Modification du fichier config.json afin d'y définir le répertoire d'impex
    def commande = "sed -i '/eboutiqueinitialdata_importVersion/!b;n;c\\    \"$version_impex\\\"' hybris/config/config.json"
    sh commande
    echo "Déclaration du répertoire d'impex dans le fichier config.json effectuée."

    // Création d'une arboscence d'impex
    def impex_directory = "hybris/bin/custom/eboutique/eboutiqueinitialdata/resources/eboutiqueinitialdata/import/$version_impex"
    def message_git = "# File initialized to let git create the directory structure."

    sh "mkdir -p $impex_directory"
    sh "cp -R /data/eboutique.jenkins/libs/templates/impex/* $impex_directory"
    echo "Arborescence d'impex créée : $impex_directory"
}

def initialiserVersionGlobale(version_globale) {
    // Modification du fichier build.properties
    // Version globale
    def commande = "sed -i '/version\\.globale/c\\version\\.globale=$version_globale' build.properties"
    sh commande
    echo "Version globale modifiée dans le fichier build.properties : $version_globale"
}

def initialiserModules(version_module) {
    // Version des modules
    updateModuleVersion("u0", version_module)
    updateModuleVersion("uh", version_module)
    updateModuleVersion("uo", version_module)
}

def initialiserCaddi(version_caddi) {
    // Version du caddi
    def commande = "sed -i '/caddi\\.rpm\\.version/c\\caddi\\.rpm\\.version=$version_caddi' build.properties"
    sh commande
    commande = "sed -i '/caddi\\.rpm\\.release/c\\caddi\\.rpm\\.release=001' build.properties"
    sh commande
    echo "Version du caddi modifiée dans le fichier build.properties : $version_caddi release 001"
}

/**
 * Méthode créée pour faire passerelle entre l'ancien moyen de création de version et le nouveau.
 * Les paramètres simulation et debug ne sont pas au même format (ni même gérés) entre les deux versions.
 *
 * @param branch_name
 * @param simulation
 * @return
 */
def creerVersionJira(version, type_branche, simulation, logDebug) {
    type_branche.projects.each {
        logDebug.debug("Création de la version ${version} dans le projet ${it}")
        jiraApi.creerVersionJira(version, it, simulation, logDebug)
    }
}

private updateModuleVersion(module_name, module_version) {
    def commande = "sed -i '/i7h\\.$module_name\\.version/c\\i7h\\.$module_name\\.version=$module_version' build.properties"
    sh commande
    echo "Version du module $module_name modifiée dans le fichier build.properties : $module_version"
}
