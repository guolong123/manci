package org.manci

class Logger {
    def script

    Logger(script=null) {
        this.script = script
    }

    @NonCPS
    def info(String msg) {
        if (script) {
            script.echo msg
        } else {
            println msg
        }
    }
}
