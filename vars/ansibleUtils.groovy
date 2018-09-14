// vars/ansibleUtils.groovy

def playbook(book, inventory, variables="") {
    withEnv(["ANSIBLE_FORCE_COLOR=true"]) {
        sh "ansible-playbook $book --vault-password-file ~/.vault_pass.txt -i $inventory $variables -vv"
    }
}