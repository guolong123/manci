package org.manci

class Logger implements Serializable{
    def script
    String logLevel

    Logger(script = null) {
        this.script = script
        this.logLevel = script.env.LOGGER_LEVEL ? script.LOGGER_LEVEL : "info"
    }

    def info(String msg) {
        if (["info", "debug"].contains(logLevel.toLowerCase())) { // 修改了此处的条件判断
            msg = "[INFO] " + msg
            if (script) {
                script.echo msg
            } else {
                println msg
            }
        }
    }

    def debug(String msg) {
        if (logLevel.toLowerCase() == "debug") { // 修改了此处的条件判断
            msg = "[DEBUG] " + msg
            if (script) {
                script.echo msg
            } else {
                println msg
            }
        }
    }

    def error(String msg) {
        msg = "[ERROR] " + msg // 无需进行条件判断，无论日志级别如何，都应该输出错误日志
        if (script) {
            script.echo msg
        } else {
            println msg
        }
    }

}
