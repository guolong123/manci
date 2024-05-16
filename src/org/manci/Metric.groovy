package org.manci

class Metric {
    String type = "gauge"
    Map<String, Object> tags = [:]
    Map<String, Object> fields = [:]
    String version = "v1"
    String serviceUrl = ""
    String serviceType = "ketaops" // 指标上报平台
    String token = ""
    def script
    Logger logger
    HttpClient client
    String repo
    String sourcetype

    Metric(script, Map dataRepoMap) {
        this.serviceUrl = dataRepoMap.get('serviceUrl')
        this.serviceType = dataRepoMap.get("serviceType", serviceType)
        this.token = dataRepoMap.get("token")
        this.script = script
        this.logger = new Logger(script)
        this.client = new HttpClient(script, serviceUrl, ["Authorization": "${token}"])
        this.repo = dataRepoMap.get("repo")
        this.sourcetype = dataRepoMap.get("sourcetype", "json")

    }

    def withMetricForJob(Closure closure) {
        this.tags["job_name"] = "${script.env.JOB_NAME}"
        this.tags["build_number"] = "${script.env.BUILD_NUMBER}"
        this.tags['build_url'] = "${script.env.BUILD_URL}"
        this.tags["pull_request_number"] = "${script.env.giteePullRequestIid}"
        this.tags["giteePullRequestTitle"] = "${script.env.giteePullRequestTitle}"
        this.tags["giteeTargetBranch"] = "${script.env.giteeTargetBranch}"
        this.tags["giteeTargetRepoHttpUrl"] = "${script.env.giteeTargetRepoHttpUrl}"
        this.tags["giteeSourceBranch"] = "${script.env.giteeSourceBranch}"
        this.tags["giteeSourceRepoHttpUrl"] = "${script.env.giteeSourceRepoHttpUrl}"
        this.tags["giteeUserEmail"] = "${script.env.giteeUserEmail}"
        this.tags["giteeTargetNamespace"] = "${script.env.giteeTargetNamespace}"
        this.tags["giteeTargetRepoName"] = "${script.env.giteeTargetRepoName}"
        this.tags["giteePullRequestState"] = "${script.env.giteePullRequestState}"
        this.tags["giteeUserName"] = "${script.env.giteeUserName}"
        def startTime = System.currentTimeMillis()
        this.tags['build_start_time'] = startTime
        try {
            closure(this)
            this.tags['build_status'] = "SUCCESS"
        } catch (Exception e) {
            this.tags['build_status'] = "FAILURE"
            this.tags['build_exception'] = e.getMessage()
            throw e
        }
        def endTime = System.currentTimeMillis()
        this.tags['build_end_time'] = endTime
        this.fields['jenkins_job_duration'] = endTime - startTime
        this.tags['node_name'] = "${script.env.NODE_NAME}"
        if (serviceType == "ketaops"){
            senderToKetaops()
        }
    }

    def withMetricForStage(Closure closure) {
        this.tags["job_name"] = "${script.env.JOB_NAME}"
        this.tags['stage_name'] = "${script.env.STAGE_NAME}"
        this.tags["build_number"] = "${script.env.BUILD_NUMBER}"
        this.tags['build_url'] = "${script.env.BUILD_URL}"
        this.tags["pull_request_number"] = "${script.env.giteePullRequestIid}"
        this.tags["giteePullRequestTitle"] = "${script.env.giteePullRequestTitle}"
        this.tags["giteeTargetBranch"] = "${script.env.giteeTargetBranch}"
        this.tags["giteeTargetRepoHttpUrl"] = "${script.env.giteeTargetRepoHttpUrl}"
        this.tags["giteeSourceBranch"] = "${script.env.giteeSourceBranch}"
        this.tags["giteeSourceRepoHttpUrl"] = "${script.env.giteeSourceRepoHttpUrl}"
        this.tags["giteeUserEmail"] = "${script.env.giteeUserEmail}"
        this.tags["giteeTargetNamespace"] = "${script.env.giteeTargetNamespace}"
        this.tags["giteeTargetRepoName"] = "${script.env.giteeTargetRepoName}"
        this.tags["giteePullRequestState"] = "${script.env.giteePullRequestState}"
        this.tags["giteeUserName"] = "${script.env.giteeUserName}"
        def startTime = System.currentTimeMillis()
        this.tags['build_start_time'] = startTime
        try {
            closure(this)
            this.tags['build_status'] = "SUCCESS"
        } catch (Exception e) {
            this.tags['build_status'] = "FAILURE"
            this.tags['build_exception'] = e.getMessage()
            throw e
        }
        def endTime = System.currentTimeMillis()
        this.tags['build_end_time'] = endTime
        this.fields['jenkins_job_stage_duration'] = endTime - startTime
        this.tags['node_name'] = "${script.env.NODE_NAME}"
        if (serviceType == "ketaops"){
            senderToKetaops()
        }

    }

    def getBody() {
        def body = [
                "repo"     : this.repo,
                "fields"   : this.fields,
                "tags"     : this.tags,
                "timestamp": System.currentTimeSeconds(),
                "type"     : this.type,
                "version"  : this.version
        ]

        return body
    }

    def senderToKetaops() {
        logger.info("Sending metric: ${this.getBody()}")
        client.post("/api/v1/data", [getBody()])
    }

}
