// vars/jira/JIRA_PROJECTS.groovy

enum JIRA_PROJECTS {
    BTKBACKLOG(10001, "BTKBACKLOG", "BTKBACKLOG"),
    BTKTMA(10100, "BTKTMA", "BTKTMA"),
    TESTS_AUTO(10500, "TESTSAUTO", "TESTS-AUTO")

    Integer id
    String key
    String name

    JIRA_PROJECTS(Integer id, String key, String name) {
        this.id = id
        this.key = key
        this.name = name
    }
}
