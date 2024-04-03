package org.manci

class GiteeApi {
    String  baseUrl = "https://gitee.com"
    String token
    String repoPath
    String pullRequestID
    String CICommentID
    String CICommentTag
    String CICommentBody
    String CICommentUrl

    GiteeApi(token=null, repoPath, pullRequestID, CICommentID=null, CICommentTag) {
        if (token == null) {
            this.token = System.getenv("GITEE_TOKEN")
        } else {
            this.token = token
        }
        this.repoPath = repoPath
        this.pullRequestID = pullRequestID
        this.CICommentID = CICommentID
        this.CICommentTag = CICommentTag
    }

    def client = new HttpClient(baseUrl, token)

    def getRepo() {
        def url = "/api/v5/repos/${repoPath}"
        def response = client.get(url)
        return response
    }
    def getRepoBranches() {
        def url = "/api/v5/repos/${repoPath}/branches"
        def response = client.get(url)
        return response
    }
    def getPullRequests(String repo) {
        def url = "/api/v5/repos/${repoPath}/pulls"
        def response = client.get(url)
        return response
    }
    def getPullRequest(String repo, String number) {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def response = client.get(url)
        return response
    }

    def comment(String comment){
        def comments = getPullRequestComments()
        def commentID = ""
        for (element in comments) {
            if (element.body.contains(CICommentTag)){
                CICommentUrl = element.url
                CICommentBody = element.body
                CICommentID = CICommentUrl.split("/")[-1]
                break
            }
        }
        if(! commentID){
            createPullRequestComment(comment)
        }

    }

    def getPullRequestComments() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments"
        def response = client.get(url)
        return response
    }
    def getPullRequestComment() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments/${CICommentID}"
        def response = client.get(url)
        return response
    }
    def createPullRequestComment(String comment) {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments"
        def data = [
            body: comment
        ]
        def response = client.post(url, data)
        return response
    }
    def updatePullRequestComment(String comment) {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments/${CICommentID}"
        def data = [
            body: comment
        ]
        def response = client.patch(url, data)
        return response
    }
    def deletePullRequestComment() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments/${CICommentID}"
        def response = client.delete(url)
        return response
    }

}
