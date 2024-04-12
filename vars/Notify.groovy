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
        logger.debug("sendWechatMessage: ${message}")
        def body = [
                "msgtype" : "markdown",
                "markdown": ["content": "${message}"]
        ]
        httpClient.post("/cgi-bin/webhook/send", body, ["key": access_token])
    }

    def sendDingTalkMessage(message) {
        logger.debug("sendWechatMessage: ${message}")
        def body = [
                "msgtype" : "markdown",
                "markdown": ["text": "${message}"]
        ]
        httpClient.post("/robot/send", body, ["key": access_token])
    }

    def sendMessage(String message, String level="info", String title="CI检查结果", String linkText="", String user="") {
        message = messageFormat(message, level, title, linkText, user)
        logger.debug("sendMessage: ${message}")
        if (webhookType == "dingtalk") {
            sendDingTalkMessage(message)
        } else if (webhookType == "qyweixin") {
            sendWechatMessage(message)
        }
    }

    static String messageFormat(String message, String level="info", String title="", String linkText="", String user="") {
        String messageResult = ""
        if (title){
            messageResult += "<font color=\"${level}\">${title}</font>\n>"
        }
        messageResult += message + "\n"
        if (linkText){
            messageResult += linkText
        }
        if (user){
            messageResult += "<@${user}>"
        }
        return messageResult
    }
}



