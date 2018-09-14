// vars/jira/Constants.groovy

class jiraConstants implements Serializable {
    // Constantes
    private String jiraApiUrl = 'http://221.128.54.179:8081/jira/rest/api/2'
    private java.util.List jiraApiContentType = [[name: 'content-type', value: 'application/json;charset=UTF-8']]

    def String getJiraApiUrl() {
        jiraApiUrl
    }
    def java.util.List getJiraApiContentType() {
        jiraApiContentType
    }
}
