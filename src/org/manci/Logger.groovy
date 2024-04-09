package org.manci

class Logger {
    def script
    String logLevel

    Logger(script = null) {
        this.script = script
        this.logLevel = script.env.LOGGER_LEVEL ? script.LOGGER_LEVEL : "info"
    }

    @NonCPS
    def info(String msg) {
        if (logLevel.toLowerCase() == "info") {
            msg = "[INFO] " + msg
            if (script) {
                script.echo msg
            } else {
                println msg
            }
        }
    }

    @NonCPS
    def debug(String msg) {
        if (["error", "debug"].contains(logLevel.toLowerCase())) {
            msg = "[DEBUG] " + msg
            if (script) {
                script.echo msg
            } else {
                println msg
            }
        }
    }

    @NonCPS
    def error(String msg) {
        if (["info", "error", "debug"].contains(logLevel.toLowerCase())) {
            msg = "[ERROR] " + msg
            if (script) {
                script.echo msg
            } else {
                println msg
            }
        }
    }
}
