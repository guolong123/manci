import org.manci.Table
import org.manci.GiteeApi
import org.manci.Logger
import org.manci.Utils
import org.manci.Event

import java.util.concurrent.ConcurrentHashMap

class ManCI implements Serializable {
    transient Map<String, List<Map<String, Object>>> stages = [:]
    boolean isCI = false
    public String CIName = "ManCI V1"
    Table table
    Logger logger
    GiteeApi giteeApi
    public List<Map<String, Object>> parameters
    public String projectDescription = """
## 指令说明
**指令**: `rebuild [stageName [stageName...]|failure] [env1=value1 env2=value2...] [withAlone=true|false]`
**描述**:
> * 无参数时，按照提交代码时的行为重新构建。
> * 指定一个或多个 `stage name`（空格分隔），重建指定阶段。
> * 使用 `rebuild failure` 重建所有失败阶段。
> * 可选地附加一组环境变量设置（键值对，等号分隔），在重建过程中将其注入到运行时环境中。
> * 通过添加参数 withAlone=true，确保仅执行当前指定的 stage，而不执行其关联的其他 stage。默认情况下，withAlone=false，即可能执行与指定 stage 关联的其他 stage。
"""
    List<String> paramsDescription = []
    transient def script
    public String SSH_SECRET_KEY
    public String GITEE_ACCESS_TOKEN_KEY
    Utils utils
    boolean failFast = true
    Event event

    ManCI(script, String loggerLevel = "info", String CIName = null) {
        this.script = script
        this.CIName = CIName ?: (script.params.CIName ? script.env.CINAME : "ManCI")
        this.script.env.LOGGER_LEVEL = loggerLevel
        this.logger = new Logger(script)
        this.utils = new Utils(script)
        this.event = new Event(script)
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
            } else if (it.type == "text") {
                propertiesParams.add(script.text(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "file") {
                propertiesParams.add(script.file(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "credentials") {
                propertiesParams.add(script.credentials(name: it.name, description: it.description,
                        defaultValue: it.defaultValue, credentialType: it.credentialType, required: it.required)
                )
            }
        }
        // withAlone
        propertiesParams.add(script.booleanParam(name: 'withAlone', description: '独立执行指定的 stage，不执行期依赖的 stage', defaultValue: 'false'))
        script.properties([script.parameters(propertiesParams)])
    }

    def stage(String stageName, Map<String, Object> stageConfig, Closure body) {
        def groupName = stageConfig.get("group", "default")
        def trigger = stageConfig.get("trigger", "always")
        // always, pr_merge, pr_open, pr_close, pr_push, pr_test_pass, pr_review_pass, env_match, file_match
        Map<String, Object> envMatches = stageConfig.get("envMatches", [:]) as Map<String, Object>
        String fileMatches = stageConfig.get("fileMatches", "") as String
        List<String> noteMatches = stageConfig.get("noteMatches", []) as List<String>
        String mark = stageConfig.get("mark", "") as String
        boolean fastFail = stageConfig.get("fastFail", true) as boolean
        if (!stages.containsKey(groupName)) {
            stages[groupName as String] = []
        }
        stages[groupName].add([
                "name"       : stageName,
                "trigger"    : trigger,
                "body"       : body,
                "envMatches" : envMatches,
                "fileMatches": fileMatches,
                "noteMatches": noteMatches,
                "mark"       : mark,
                "fastFail"   : fastFail
        ] as Map<String, Object>)
    }


    def withRun(String nodeLabels = null, Closure body) {
        setParams()

        if (this.script.env.noteBody) {
            // 当存在这个环境变量时则解析这个 comment，注入 kv到环境变量
            logger.debug("noteBody: ${this.script.env.noteBody}")
            def commands = this.utils.commandParse(this.script.env.noteBody as String)
            def envArgs = commands.get("kwargs")
            envArgs.each { key, value ->
                logger.debug("${key}: ${value}")
                this.script.env.putAt(key, value)
            }
        }
        script.withCredentials([script.string(credentialsId: GITEE_ACCESS_TOKEN_KEY, variable: "GITEE_ACCESS_TOKEN")]) {
            String repoPath = script.env.giteeTargetNamespace + '/' + script.env.giteeTargetRepoName
            logger.debug "script.env.GITEE_ACCESS_TOKEN: ${script.env.GITEE_ACCESS_TOKEN}"
            giteeApi = new GiteeApi(script, "${script.env.GITEE_ACCESS_TOKEN}", repoPath, script.env.giteePullRequestIid, CIName)
        }
        logger.debug "script.env.ref: ${script.env.ref}"
        if ("${script.env.ref}" != "null") {
            this.isCI = true
        }
        logger.debug "SSH_SECRET_KEY: ${SSH_SECRET_KEY}"
        logger.debug "isCI: ${this.isCI}"
        giteeApi.label(giteeApi.labelWaiting)
        script.node(nodeLabels) {
            giteeApi.label(giteeApi.labelRunning)
            script.stage("checkout") {
                if (script.env.LOGGER_LEVEL && script.env.LOGGER_LEVEL.toLowerCase() == "debug") {
                    script.sh 'env'
                }
                def scmVars = script.checkout script.scm
                if (this.isCI) {
                    logger.debug "checkout: url ${script.env.giteeTargetRepoSshUrl}, branch: ${script.env.ref}"
                    script.checkout([$class           : 'GitSCM', branches: [[name: script.env.ref]], extensions: [],
                                     userRemoteConfigs: [[credentialsId: SSH_SECRET_KEY,
                                                          url          : "${script.env.giteeTargetRepoSshUrl}"]]
                    ])
                } else {
                    if (script.env.BRANCH_NAME) {
                        def gitUrl = scmVars.GIT_URL
                        script.checkout([$class           : 'GitSCM',
                                         branches         : [[name: script.env.BRANCH_NAME]], extensions: [],
                                         userRemoteConfigs: [[credentialsId: SSH_SECRET_KEY,
                                                              url          : gitUrl]]
                        ])
                    }
                }
            }
            body.call()
            try {
                this.run()
            } catch (Exception e) {
                giteeApi.label(giteeApi.labelFailure)
                throw e
            }
            giteeApi.label(giteeApi.labelSuccess)
            giteeApi.testPass()
        }
    }


    def stageRun(String stageName, Closure body) {
        logger.debug("stageRun: ${stageName} start")
        script.stage(stageName) {
            body.call()
        }
        logger.debug("stageRun: ${stageName} done")
    }

    def run() {
        ConcurrentHashMap<String, Object> parallelStage = [:] as ConcurrentHashMap<String, Object>
        Exception error = null
        if (isCI) {
            List<String> stageNames = [] as List<String>
            stages.each {
                it.value.each {
                    stageNames.add(it.name as String)
                }
            }
            table = new Table(script, CIName, "", projectDescription + "\n<details>\n<summary><b>参数说明</b>(点击展开)</summary>\n\n" + paramsDescription.join("\n") + "\n</details>", stageNames)

            table.text = giteeApi.initComment(table.text)
            table.tableParse()  // 从已有的评论中解析出table
            stages.each { group, st ->
                parallelStage[group] = {
                    st.each {
                        it.noteMatches.add("rebuild ${it.name}")
                        String runStrategy = event.getStageTrigger(it.trigger as List<String>, giteeApi as GiteeApi, it as Map<String, Object>)
                        String elapsedTime = ""
                        String nowTime = ""
                        Integer runCnt = 0
                        Integer buildResult // 0: success, 1: failure, 2: aborted, 3: skip
                        List<String> failureStages = table.getFailureStages()
                        long startTime = System.currentTimeMillis()
                        logger.debug("error: ${error}")
                        def needRun = event.needRunStage(it, "gitee", failureStages as List<String>, error)
                        if (!needRun) {
                            script.stage(it.name) {
                                // 标记 stage 为跳过
                                org.jenkinsci.plugins.pipeline.modeldefinition.Utils.markStageSkippedForConditional(it.name)
                            }
                            buildResult = 3
                        } else {
                            nowTime = utils.getNowTime()
                            runCnt = table.getStageRunTotal(it.name as String) + 1
                            logger.debug("needRunStage: ${it.name}")
                            try {
                                table.addColumns([[it.name, group,
                                                   "[${table.RUNNING_LABEL}](${script.env.RUN_DISPLAY_URL} \"点击跳转到 jenkins 构建页面\")",
                                                   "0min0s" + "/" + table.getStageRunTotalTime(it.name as String), runCnt, nowTime, runStrategy, it.mark]])
                                stageRun(it.name as String, it.body as Closure)
                                buildResult = 0
                                logger.debug("[${it.name}] 204: buildResult: ${buildResult}")
                            } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                                logger.error("[${it.name}]: ${e}")
                                buildResult = 2
                                error = e as Exception
                            } catch (InterruptedException e) {
                                logger.error("[${it.name}]: ${e}")
                                buildResult = 2
                                error = e as Exception
                            } catch (NotSerializableException e) {
                                logger.error("[${it.name}] 213: ${e}")
                                buildResult = 1
                                error = e as Exception
                            } catch (Exception e) {
                                logger.error("[${it.name}] 217: ${e}")
                                buildResult = 1
                                error = e as Exception
                            }
                            long timeForOne = System.currentTimeMillis() - startTime
                            String OneTime = utils.timestampConvert(timeForOne)
                            String runTotalTimeStr
                            long runTotalTime
                            if (runCnt > 1) {
                                runTotalTimeStr = table.getStageRunTotalTime(it.name as String)
                                runTotalTime = utils.reverseTimestampConvert(runTotalTimeStr) + timeForOne

                            } else {
                                runTotalTimeStr = OneTime
                                runTotalTime = utils.reverseTimestampConvert(runTotalTimeStr)

                            }
                            elapsedTime = utils.timestampConvert(timeForOne) + "/" + utils.timestampConvert(runTotalTime)
                        }

                        if (buildResult == 0) {
                            table.addColumns([[it.name, group, "[${table.SUCCESS_LABEL}](${script.env.RUN_DISPLAY_URL} \"点击跳转到 jenkins 构建页面\")", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        } else if (buildResult == 1) {
                            table.addColumns([[it.name, group, "[${table.FAILURE_LABEL}](${script.env.RUN_DISPLAY_URL} \"点击跳转到 jenkins 构建页面\")", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        } else if (buildResult == 2) {
                            table.addColumns([[it.name, group, "[${table.ABORTED_LABEL}](${script.env.RUN_DISPLAY_URL} \"点击跳转到 jenkins 构建页面\")", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        } else if (buildResult == 3) {
                            table.addColumns([[it.name, group, "[${table.NOT_NEED_RUN_LABEL}](${script.env.RUN_DISPLAY_URL} \"点击跳转到 jenkins 构建页面\")", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        }
                        giteeApi.comment(table.text)
                        if (error && it.fastFail as boolean) {
                            logger.error("[${it.name}] 258: ${error}")
                            throw error
                        }
                    }
                }
            }
        } else {
            stages.each { k, v ->
                parallelStage[k] = {
                    v.each {
                        def needRun = event.needRunStageNotCI(it, error)
                        script.stage(it.name) {
                            if (needRun) {
                                try {
                                    stageRun(it.name as String, it.body as Closure)
                                } catch (Exception e) {
                                    error = e as Exception
                                    logger.debug("[${it.name}] 202: ${e}")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (failFast) {
            parallelStage['failFast'] = true
        }

        Closure beforeStage = parallelStage.get("before") as Closure
        Closure afterStage = parallelStage.get("after") as Closure
        if (beforeStage) {
            parallelStage.remove("before")
            beforeStage.call()
        }
        if (afterStage) {
            parallelStage.remove("after")
        }


        try {
            script.parallel parallelStage
        } catch (Exception e) {
            logger.error("parallel with error: ${e}")
            error = e as Exception
        }
        if (!error && !table.isSuccessful()) {
            error = new Exception("存在未成功的 stage，此次构建将以失败状态退出")
        }

        if (afterStage) {
            afterStage.call()
        }
        if (error) {
            logger.error("ManCI Finished with error: ${error}")
            throw error
        }
        logger.info "ManCI Finished"
    }
}