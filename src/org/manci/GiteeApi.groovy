package org.manci

class GiteeApi implements Serializable {
    String baseUrl = "https://gitee.com"
    String token
    String repoPath
    String pullRequestID
    String CICommentID
    String CICommentTag
    String CICommentBody
    String CICommentUrl
    def script
    Logger logger
    public labelSuccess = 'ci-success'
    public labelFailure = 'ci-failure'
    public labelWaiting = 'ci-waiting'
    public labelRunning = 'ci-running'
    public labelAbort = 'ci-abort'


    GiteeApi(script = null, String token = null, String repoPath, String pullRequestID, String CICommentTag) {
        if (token == null) {
            this.token = System.getenv("GITEE_TOKEN")
        } else {
            this.token = token
        }
        this.repoPath = repoPath
        this.pullRequestID = pullRequestID
        this.CICommentTag = CICommentTag
        this.script = script
        logger = new Logger(script)
    }

    def client = new HttpClient(script, baseUrl, ["Authorization": "token ${token}"])

    @NonCPS
    def getRepo() {
        def url = "/api/v5/repos/${repoPath}"
        def response = client.get(url)
        return response
    }

    @NonCPS
    def getRepoBranches() {
        def url = "/api/v5/repos/${repoPath}/branches"
        def response = client.get(url)
        return response
    }

    @NonCPS
    def getPullRequests() {
        def url = "/api/v5/repos/${repoPath}/pulls"
        def response = client.get(url)
        return response
    }

    @NonCPS
    def getReplyComment() {
        logger.debug("getReplyComment: ...")
        def commentInfo = [:]
        Map<String, String> queryParams = [
                page    : "1",
                per_page: "100"
        ] as Map<String, String>
        def resp = client.get("/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments", queryParams)
        for (element in resp) {
            logger.debug("element.in_reply_to_id: ${element.in_reply_to_id}, CICommentID: ${CICommentID}")
            if ("${element.in_reply_to_id}" == "${CICommentID}") {
                commentInfo.put("url", element.url)
                commentInfo.put("body", element.body)
                commentInfo.put("id", element.id)
                commentInfo.put("in_reply_to_id", element.in_reply_to_id)
                break
            }
        }
        logger.debug("commentInfo: ${commentInfo}")
        return commentInfo
    }

    @NonCPS
    def getPullRequest() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def response = client.get(url)
        return response
    }

    @NonCPS
    def initComment(String comment) {
        logger.debug("initComment: ...")
        def comments = getPullRequestComments()
        for (element in comments) {
            if (element.body.contains(CICommentTag)) {
                CICommentUrl = element.url
                CICommentBody = element.body
                CICommentID = CICommentUrl.split("/")[-1]
                break
            }
        }
        if (!CICommentID) {
            createPullRequestComment(comment)
            CICommentBody = comment
        }
        return CICommentBody
    }

    @NonCPS
    def comment(String comment) {
        logger.debug("comment: ...")
        def comments = getPullRequestComments()
        for (element in comments) {
            if (element.body.contains(CICommentTag)) {
                CICommentUrl = element.url
                CICommentBody = element.body
                CICommentID = CICommentUrl.split("/")[-1]
                break
            }
        }
        if (!CICommentID) {
            createPullRequestComment(comment)
            CICommentBody = comment
        } else {
            def resp = updatePullRequestComment(comment)
            CICommentBody = resp.body
        }
        return CICommentBody
    }

    @NonCPS
    def getPullRequestComments() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments"
        Map<String, String> queryParams = [
                page    : "1",
                per_page: "100"
        ] as Map<String, String>
        def response = client.get(url, queryParams)
        return response
    }

    @NonCPS
    def getPullRequestComment() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments/${CICommentID}"
        def response = client.get(url)
        return response
    }

    @NonCPS
    def createPullRequestComment(String comment) {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments"
        Map<String, String> data = [
                body: comment
        ]
        def response = client.post(url, data)
        return response
    }

    @NonCPS
    def updatePullRequestComment(String comment) {
        def url = "/api/v5/repos/${repoPath}/pulls/comments/${CICommentID}"
        Map<String, String> data = [
                body: comment
        ]
        def response = client.patch(url, data)
        return response
    }

    @NonCPS
    def deletePullRequestComment() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments/${CICommentID}"
        def response = client.delete(url)
        return response
    }

    @NonCPS
    def addLabel(labelName) {
        logger.debug("addLabel: ${labelName}")
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/labels"
        List<String> data = [
                labelName
        ]
        def response = client.post(url, data)
        return response
    }

    @NonCPS
    def deleteLabel(labelNames) {
        logger.debug("deleteLabel: ${labelNames}")
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/labels/${labelNames}"
        def response = client.delete(url)
        return response
    }

    @NonCPS
    def getLabels() {
        logger.debug("getLabels: ...")
        def tags = []
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/labels"
        Map<String, String> queryParams = [
                page    : "1",
                per_page: "100"
        ] as Map<String, String>
        def resp = client.get(url, queryParams)
        for (element in resp) {
            tags.add(element.name)
        }
        return tags
    }

    @NonCPS
    def label(labelName) {
        this.deleteLabel("${this.labelSuccess},${this.labelFailure},${this.labelWaiting},${this.labelRunning}")
        this.addLabel(labelName)
    }

    @NonCPS
    def testPass() {
        logger.debug("testPass: ...")
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/test"
        Map<String, Object> data = [
                force: false
        ]
        def response = client.post(url, data)
        return response
    }

    @NonCPS
    def review(String reviewers, Integer reviewerNumber = 1) {
        if (!this.selectReviewer()) {
            this.addReviewer(reviewers)
            this.setReviewerNumber(reviewerNumber)
        }
    }

    @NonCPS
    def selectReviewer() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def resp = client.get(url)
        def reviewers = []

        if (resp.assignees.size() > 0) {
            for (element in resp.assignees) {
                reviewers.add(element.login)
            }
        }
        return reviewers
    }

    @NonCPS
    def addReviewer(assignees) {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/assignees"
        Map<String, Object> data = [
                assignees: assignees
        ]
        return client.post(url, data)
    }

    @NonCPS
    def resetReviewer(assignees) {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/assignees?assignees=${assignees}"
        return client.delete(url)
    }

    @NonCPS
    def resetReview() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/assignees"
        Map<String, Object> data = [
                reset_all: false
        ]
        return client.patch(url, data)
    }

    @NonCPS
    def addTester(testers) {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/testers"
        Map<String, Object> data = [
                assignees: testers
        ]
        return client.post(url, data)
    }

    @NonCPS
    def resetTester(testers) {
        // 取消用户审查
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/testers?assignees=${testers}"
        return client.delete(url)
    }

    @NonCPS
    def resetTest() {
        logger.debug("resetTest: ...")
        // 取消用户审查状态（将审查通过改成未审查）
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/testers"
        Map<String, Object> data = [
                "reset_all": "false"
        ]
        return client.patch(url, data)
    }

    @NonCPS
    def setTesterNumber(testerNumber = 1) {
        // 修改测试通过最低数量
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        Map<String, Object> data = [
                "testers_number": testerNumber
        ]
        return client.patch(url, data)
    }

    @NonCPS
    def setReviewerNumber(reviewerNumber = 1) {
        // 修改审查通过最低数量
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        Map<String, Object> data = [
                "assignees_number": reviewerNumber
        ]
        return client.patch(url, data)
    }

    @NonCPS
    def getPrMergeStatus() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def resp = client.get(url)
        return resp.state
    }

    @NonCPS
    def updateInfo(text) {
        // 更新pr描述
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        Map<String, Object> data = [
                body: text
        ]
        return client.patch(url, data)
    }

    @NonCPS
    def getPrInfo() {
        // 获取描述信息
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def resp = client.get(url)
        def body = resp.body
        return body
    }
}
