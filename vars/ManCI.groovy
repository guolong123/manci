import org.manci.Table
import org.manci.GiteeApi

import java.text.SimpleDateFormat

class ManCI {
    Map<String, List<Map<String, Object>>> stages = [:]
    boolean isCI = false
    public String CIName = "ManCI V1"
    Table table
    GiteeApi giteeApi
    public List<Map<String, Object>> parameters
    public String projectDescription
    List<String> paramsDescription = []
    def script
    def scmVars
    public String SSH_SECRET_KEY
    public String GITEE_ACCESS_TOKEN_KEY

    ManCI(script, String CIName = null) {
        this.script = script
        this.CIName = CIName ?: (script.params.CIName ? script.env.CINAME : "ManCI")
    }

    def setParams() {
        List<Object> propertiesParams = []
        this.parameters.each {
            paramsDescription.add("* `${it.name}`: ${it.description}")
            if (it.type == "choice") {
                propertiesParams.add(script.choice(name: it.name, description: it.description, choices: it.choices))
            } else if (it.type == "string") {
                propertiesParams.add(script.string(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "boolean") {
                propertiesParams.add(script.booleanParam(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "password") {
                propertiesParams.add(script.password(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "textarea") {
                propertiesParams.add(script.textarea(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "file") {
                propertiesParams.add(script.file(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "credentials") {
                propertiesParams.add(script.credentials(name: it.name, description: it.description,
                                defaultValue: it.defaultValue, credentialType: it.credentialType, required: it.required)
                )
            }
        }
        script.properties([script.parameters(propertiesParams)])
    }

    static getNowTime() {
        def now = new Date()
        def formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return formatter.format(now)
    }

    def stage(String stageName, Map<String, Object> stageConfig, Closure body) {
        def groupName = stageConfig.get("group", "default")
        if (!stages.containsKey(groupName)) {
            stages[groupName] = []
        }
        stages[groupName].add([
                "name": stageName,
                "body": body
        ])
    }

    static String timestampConvert(timestamp) {
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
        return "${min}min${s}s" as String
    }

    def withRun(String nodeLabels = null, Closure body) {
        if ("${script.env.ref}" != "") {
            this.isCI = true
        }
        script.echo "SSH_SECRET_KEY: ${SSH_SECRET_KEY}"
        script.node(nodeLabels) {
            script.stage("checkout") {
                if (this.isCI){
                    script.echo "checkout: url ${script.env.giteeSourceRepoSshUrl}, branch: ${script.env.ref}"
                    script.checkout([$class: 'GitSCM', branches: [[name: script.env.ref]], extensions: [],
                              userRemoteConfigs: [[credentialsId: SSH_SECRET_KEY,
                                                   url: "${script.env.giteeSourceRepoSshUrl}"]]])
                }else {
                    script.checkout script.scm
                }
            }
            body.call()
            this.run()
        }
    }

    def run() {
        Map<String, Closure> parallelStage = [:] as Map<String, Closure>
        if (isCI) {
            List<String> stageNames = [] as List<String>
            stages.each {
                it.value.each {
                    stageNames.add(it.name as String)
                }
            }
            table = new Table(CIName, "", projectDescription + "\n<details>\n<summary>参数说明:</summary>\n\n" + paramsDescription.join("\n") + "\n</details>", stageNames)
            script.withCredentials([script.string(credentialsId: GITEE_ACCESS_TOKEN_KEY, variable: "GITEE_ACCESS_TOKEN")]){
                String repoPath = script.env.giteeSourceNamespace + '/' + script.env.giteeSourceRepoName
                giteeApi = new GiteeApi(GITEE_ACCESS_TOKEN, repoPath, script.env.giteePullRequestIid, CIName)
            }

            stages.each { group, v ->
                parallelStage[group] = {
                    v.each {
                        def startTime = System.currentTimeMillis()
                        Integer buildResult // 0: success, 1: failure, 2: aborted
                        try {
                            script.stage(it.name) {
                                it.body.call()
                            }
                            buildResult = 0
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException ignored) {
                            buildResult = 2
                        } catch (InterruptedException ignored) {
                            buildResult = 2
                        } catch (Exception ignored) {
                            buildResult = 1
                        }
                        String elapsedTime = timestampConvert(System.currentTimeMillis() - startTime)
                        def nowTime = getNowTime()
                        Integer runCnt = table.getStageRunTotal(it.name as String) + 1
                        if (buildResult == 0) {
                            table.addColumns([[it.name, group, table.SUCCESS_LABEL, elapsedTime, runCnt, nowTime, "", ""]])

                        } else if (buildResult == 1) {
                            table.addColumns([[it.name, group, table.FAILURE_LABEL, elapsedTime, runCnt, nowTime, "", ""]])
                        } else if (buildResult == 2) {
                            table.addColumns([[it.name, group, table.ABORTED_LABEL, elapsedTime, runCnt, nowTime, "", ""]])
                        }
                        script.echo table.text
                        giteeApi.comment(table.text)
                    }
                }
            }
        } else {
            stages.each { k, v ->
                parallelStage[k] = {
                    v.each {
                        script.stage(it.name) {
                            it.body.call()
                        }
                    }
                }
            }
        }
        script.parallel parallelStage
        script.echo "ManCI Finished"
    }
}