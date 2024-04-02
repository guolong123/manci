import main.groovy.org.manci.Table

class ManCI {
    Map<String, List<Map<String, Object>>> stages
    boolean isCI = false
    String CIName = "ManCI V1"
    Table table

    def ManCI(boolean isCI = false, String CIName = null) {
        this.isCI = isCI ? isCI : System.getenv("CI") == "true"
        this.CIName = CIName ? CIName : System.getenv("CINAME") ? System.getenv("CINAME") : "ManCI V1"

    }

    def setParams(Map<String, Object> params) {
        this.params = params
    }

    def setDescription(String description) {
        this.description = description
    }

    def mstage(String stageName, Map<String, Object> stageConfig, Closure body) {
        def groupName = stageConfig.get("group", "default")
        if(!stages.containsKey(groupName)){
            stages[groupName] = []
        }
        stages[groupName].add([
                "name": stageName,
                "body": body
        ])
    }

    def static timestampConvert(timestamp) {
        def ms = 0
        def s = 0
        def min = 0
        ms = timestamp % 1000
        if (timestamp >= 1000) {
            s = (int) (timestamp / 1000) % 60
        }
        if (timestamp >= 60000) {
            min = (int) (timestamp / 1000 / 60) % 60
        }
        return "${min}min${s}s"
    }

    def run() {
        if (isCI) {
            table = new Table(CIName, "", "", stageNames)
            table.tableCreate()
            Map<String, Closure> stageRun = [:]
            stages.collectEntries {
                stageRun[it] = {
                    def stageName = it.value.name
                    def stageBody = it.value.body
                    def startTime = System.currentTimeMillis()
                    stage(stageName) {
                        stageBody.call()
                    }
                    def elapsedTime = timestampConvert(System.currentTimeMillis() - startTime)
                    table.addColumns([[stageName, table.SUCCESS_LABEL, elapsedTime, "", "", "", ""]])
                }
            }
            parallel(stageRun)
        }
    }
}