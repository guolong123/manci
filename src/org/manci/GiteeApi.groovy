package org.manci

class GiteeApi {
    def baseUrl = "https://gitee.com"
    def token = System.getenv("GITEE_TOKEN")
    def client = new HttpClient(baseUrl, token)

    def getRepo(String repo) {
        def url = "/api/v5/repos/${repo}"
        def response = client.get(url)
        return response
    }
    def getRepoBranches(String repo) {
        def url = "/api/v5/repos/${repo}/branches"
        def response = client.get(url)
        return response
    }
    def getPullRequests(String repo) {
        def url = "/api/v5/repos/${repo}/pulls"
        def response = client.get(url)
        return response
    }
    def getPullRequest(String repo, String number) {
        def url = "/api/v5/repos/${repo}/pulls/${number}"
        def response = client.get(url)
        return response
    }
    def getPullRequestComments(String repo, String number) {
        def url = "/api/v5/repos/${repo}/pulls/${number}/comments"
        def response = client.get(url)
        return response
    }
    def getPullRequestComment(String repo, String number, String id) {
        def url = "/api/v5/repos/${repo}/pulls/${number}/comments/${id}"
        def response = client.get(url)
        return response
    }
    def createPullRequestComment(String repo, String number, String body) {
        def url = "/api/v5/repos/${repo}/pulls/${number}/comments"
        def data = [
            body: body
        ]
        def response = client.post(url, data)
        return response
    }
    def updatePullRequestComment(String repo, String number, String id, String body) {
        def url = "/api/v5/repos/${repo}/pulls/${number}/comments/${id}"
        def data = [
            body: body
        ]
        def response = client.patch(url, data)
        return response
    }
    def deletePullRequestComment(String repo, String number, String id) {
        def url = "/api/v5/repos/${repo}/pulls/${number}/comments/${id}"
        def response = client.delete(url)
        return response
    }

}
