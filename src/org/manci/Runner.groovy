package org.manci

class Runner {

    def GitType
    def GitUrl
    def GitToken
    def GitBranch
    def GitRepo
    def GitNamespace
    def client

    def Runner() {
        GitType = System.getenv("GIT_TYPE") // gitlab or github or gitee
        GitUrl = System.getenv("GIT_URL")
        GitToken = System.getenv("GIT_TOKEN")
        GitBranch = System.getenv("GIT_BRANCH")
        GitRepo = System.getenv("GIT_REPO")
        GitNamespace = System.getenv("GIT_NAMESPACE")

    }
    def Run() {
        if (GitType == "gitlab") {
            println("gitlab")
        } else if (GitType == "github") {
            println("github")
        } else if (GitType == "gitee") {
            println("gitee")
            client = new GiteeApi()
        } else {
            println("unknown")
        }
    }
}
