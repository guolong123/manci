package org.manci

class GiteeApi implements Serializable{
    String  baseUrl = "https://gitee.com"
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


    GiteeApi(script=null, String token=null, String repoPath, String pullRequestID, String CICommentTag) {
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
    def getPullRequests() {
        def url = "/api/v5/repos/${repoPath}/pulls"
        def response = client.get(url)
        return response
    }
    def getPullRequest() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def response = client.get(url)
        return response
    }

    String initComment(String comment){
        def comments = getPullRequestComments()
//        logger.debug("comments: ${comments}")
        for (element in comments) {
            if (element.body.contains(CICommentTag)){
                CICommentUrl = element.url
                CICommentBody = element.body
                CICommentID = CICommentUrl.split("/")[-1]
                break
            }
        }
        if(! CICommentID){
            createPullRequestComment(comment)
            CICommentBody = comment
        }
        return CICommentBody
    }

    String comment(String comment){
        def comments = getPullRequestComments()
        for (element in comments) {
            if (element.body.contains(CICommentTag)){
                CICommentUrl = element.url
                CICommentBody = element.body
                CICommentID = CICommentUrl.split("/")[-1]
                break
            }
        }
        if(! CICommentID){
            createPullRequestComment(comment)
            CICommentBody = comment
        }else{
            def resp = updatePullRequestComment(comment)
            CICommentBody = resp.body
        }
        return CICommentBody
    }

    String getPullRequestComments() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments"
        Map<String,String > queryParams = [
            page: "1",
            per_page: "100"
        ] as Map<String, String>
        def response = client.get(url, queryParams)
        return response
    }
    String getPullRequestComment() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments/${CICommentID}"
        def response = client.get(url)
        return response
    }
    String createPullRequestComment(String comment) {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments"
        Map<String, String> data = [
            body: comment
        ]
        def response = client.post(url, data)
        return response
    }
    String updatePullRequestComment(String comment) {
        def url = "/api/v5/repos/${repoPath}/pulls/comments/${CICommentID}"
        Map<String, String> data = [
            body: comment
        ]
        def response = client.patch(url, data)
        return response
    }
    String deletePullRequestComment() {
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/comments/${CICommentID}"
        def response = client.delete(url)
        return response
    }

    def addLabel(labelName){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/labels"
        List<String> data = [
                labelName
        ]
        def response = client.post(url, data)
        return response
    }

    def deleteLabel(labelNames){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/labels/${labelNames}"
        def response = client.delete(url)
        return response
    }

    def getLabels(){
        def tags = []
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/labels"
        Map<String, String> queryParams = [
            page: "1",
            per_page: "100"
        ] as Map<String, String>
        def resp = client.get(url, queryParams)
        for (element in resp) {
            tags.add(element.name)
        }
        return tags
    }

    def label(labelName){
        this.deleteLabel("${this.labelSuccess},${this.labelFailure},${this.labelWaiting},${this.labelRunning}")
        this.addLabel(labelName)
    }

    def testPass(){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/test"
        Map<String, Object> data = [
            force: false
        ]
        def response = client.post(url, data)
        return response
    }

    def review(String reviewers, Integer reviewerNumber = 1){
        if(! this.selectReviewer()){
            this.addReviewer(reviewers)
            this.setReviewerNumber(reviewerNumber)
        }
    }

    def selectReviewer(){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def resp = client.get(url)
        def reviewers = []

        if(resp.assignees.size() > 0){
            for (element in resp.assignees) {
                reviewers.add(element.login)
            }
        }
        return reviewers
    }

    def addReviewer(assignees){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/assignees"
        Map<String, Object> data = [
            assignees: assignees
        ]
        return client.post(url, data)
    }

    def resetReviewer(assignees){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/assignees?assignees=${assignees}"
        return client.delete(url)
    }

    def resetReview(){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/assignees"
        Map<String, Object> data = [
             reset_all: false
        ]
        return client.patch(url, data)
    }

    def addTester(testers){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/testers"
        Map<String, Object> data = [
            assignees: testers
        ]
        return client.post(url, data)
    }

    def resetTester(testers){
        // 取消用户审查
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/testers?assignees=${testers}"
        return client.delete(url)
    }

    def resetTest(){
        // 取消用户审查状态（将审查通过改成未审查）
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}/testers"
        Map<String, Object> data = [
                "reset_all": "false"
        ]
        return client.patch(url, data)
    }

    def setTesterNumber(testerNumber=1){
        // 修改测试通过最低数量
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        Map<String, Object> data = [
                "testers_number": testerNumber
        ]
        return client.patch(url, data)
    }

    def setReviewerNumber(reviewerNumber=1){
        // 修改审查通过最低数量
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        Map<String, Object> data = [
                "assignees_number": reviewerNumber
        ]
        return client.patch(url, data)
    }

    def getPrMergeStatus(){
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def resp = client.get(url)
        return resp.state
    }

    def updateInfo(text){
        // 更新pr描述
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        Map<String, Object> data = [
                body: text
        ]
        return client.patch(url, data)
    }

    def getPrInfo(){
        // 获取描述信息
        def url = "/api/v5/repos/${repoPath}/pulls/${pullRequestID}"
        def resp = client.get(url)
        def body = resp.body
        return body
    }
}
