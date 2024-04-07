package org.manci

class Logger {
    def script

    Logger(script=null) {
        this.script = script

    }

    @NonCPS
    def info(String msg) {
        def debug = script.env.DEBUG?script.DEBUG:"false"
        if (debug == "true") {
            msg = "[INFO] " + msg
            if (script) {
                script.echo msg
            } else {
                println msg
            }
        }
    }
    @NonCPS
    def error(String msg) {
        def debug = script.env.DEBUG?script.DEBUG:"false"
        if (debug == "true") {
            msg = "[ERROR] " + msg
            if (script) {
                script.echo msg
            } else {
                println msg
            }
        }
    }
    @NonCPS
    def debug(String msg) {
        def debug = script.env.DEBUG?script.DEBUG:"false"
        if (debug == "true") {
            msg = "[DEBUG] " + msg
            if (script) {
                script.echo msg
            } else {
                println msg
            }
        }
    }
}
