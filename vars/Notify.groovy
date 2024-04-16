import org.manci.HttpClient
import org.manci.Logger
import org.manci.Utils


class Notify {
    def script
    def logger
    def utils
    def httpClient
    String access_token
    String webhookType

    Notify(script, webhookType = "qyweixin", access_token) {
        this.script = script
        logger = new Logger(script)
        utils = new Utils(script)
        if (webhookType == "qyweixin") {
            httpClient = new HttpClient(script, "https://qyapi.weixin.qq.com")
        } else if (webhookType == "dingtalk") {
            httpClient = new HttpClient(script, "https://oapi.dingtalk.com")
        }
        this.access_token = access_token
        this.webhookType = webhookType

    }

    def sendWechatMessage(message) {
        def body = [
                "msgtype" : "markdown",
                "markdown": ["content": "${message}"]
        ]
        httpClient.post("/cgi-bin/webhook/send", body, ["key": access_token])
    }

    def sendDingTalkMessage(message) {
        def body = [
                "msgtype" : "markdown",
                "markdown": ["text": "${message}"]
        ]
        httpClient.post("/robot/send", body, ["key": access_token])
    }

    def sendMessage(String level = "info", String message = null, String title = "", String linkText = "", String user = "") {
        message = messageFormat(message, level, title, linkText, user)
        logger.info("sendMessage: ${message}")
        if (webhookType == "dingtalk") {
            sendDingTalkMessage(message)
        } else if (webhookType == "qyweixin") {
            sendWechatMessage(message)
        }
    }

    def sendFailureMessage(String message = null, String title = "", String linkText = "", String user = "") {
        if (!title) {
            title = "[PR]: [${script.env.giteePullRequestTitle}](https://gitee.com/${script.env.giteeTargetNamespace}/${script.env.giteeTargetRepoName}/pulls/${script.env.giteePullRequestIid}) Failure"
        }
        sendMessage("warning", message, title, linkText, user)
    }

    def sendSuccessMessage(String message = null, String title = "", String linkText = "", String user = "") {
        if (!title) {
            title = "[PR]: [${script.env.giteePullRequestTitle}](https://gitee.com/${script.env.giteeTargetNamespace}/${script.env.giteeTargetRepoName}/pulls/${script.env.giteePullRequestIid}) Success"
        }
        sendMessage("info", message, title, linkText, user)
    }

    String messageFormat(String message = null, String level = "info", String title = "", String linkText = "", String user = "") {
        String messageResult = ""
        if (!message) {
            message = "构建耗时: ${script.currentBuild.durationString.replace("and counting", "")}"
        }
        if (!title) {
            title = "[PR]: [${script.env.giteePullRequestTitle}](https://gitee.com/${script.env.giteeTargetNamespace}/${script.env.giteeTargetRepoName}/pulls/${script.env.giteePullRequestIid})"
        }
        messageResult += "<font color=\"${level}\">${title}</font>\n>"
        messageResult += message + "\n"
        if (!linkText) {
            linkText = "[查看控制台](${script.env.BUILD_URL})"
        }
        messageResult += linkText
        if (!user && "${script.env.giteeUserName}") {
            user = "${script.env.giteeUserName}"
            messageResult += "<@${user}>"
        }
        return messageResult
    }
}



