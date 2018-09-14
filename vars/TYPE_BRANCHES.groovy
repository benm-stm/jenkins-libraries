// vars/jira/TYPE_BRANCHES.groovy

enum TYPE_BRANCHES {
    RELEASE("release_", "Release", "\\d{1,2}\\.\\d{1,2}", false, [JIRA_PROJECTS.BTKBACKLOG, JIRA_PROJECTS.BTKTMA, JIRA_PROJECTS.TESTS_AUTO]),
    PROJET("projet_", "Projet", "\\d{2}\\.\\w{2}\\.\\w{2}", false, [JIRA_PROJECTS.BTKBACKLOG]),
    DETTE("dette_", "Dette", "\\d{2}\\.\\d{2}\\.\\d{2}", true, [JIRA_PROJECTS.BTKBACKLOG, JIRA_PROJECTS.BTKTMA]),
    TRAIN("train_", "Train", "\\d{2}\\.\\d{2}\\.\\d{2}", true, [JIRA_PROJECTS.BTKBACKLOG, JIRA_PROJECTS.BTKTMA]),
    HOTFIX("hotfix_", "Hotfix", "\\d{2}\\.\\d{2}\\.\\d{2}", true, [JIRA_PROJECTS.BTKBACKLOG, JIRA_PROJECTS.BTKTMA])

    String prefix
    String name
    String patternVersion
    boolean pushBranchOnly
    java.util.List projects

    TYPE_BRANCHES(String prefix, String name, String patternVersion, boolean pushBranchOnly, java.util.List projects) {
        this.prefix = prefix
        this.name = name
        this.projects = projects
        this.pushBranchOnly = pushBranchOnly
        this.patternVersion = patternVersion
    }
}
