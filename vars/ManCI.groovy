import org.manci.Table
import org.manci.GiteeApi
import org.manci.Logger
import org.manci.Utils

class ManCI implements Serializable{
    transient Map<String, List<Map<String, Object>>> stages = [:]
    boolean isCI = false
    public String CIName = "ManCI V1"
    Table table
    Logger logger
    GiteeApi giteeApi
    public List<Map<String, Object>> parameters
    public String projectDescription = """
## 使用小窍门
* 可以点击状态图标进入构建日志界面
* 评论`rebuild failure`可以运行所有失败的步骤
* 评论`rebuild`将以提交时默认行为进行构建
* 支持评论`rebuild <stageName> ...` 来指定阶段执行，可使用`withAlone=true`，使构建仅执行当前指定的 stage，而不执行其关联的其它 stage。比如镜像已经构建后，部署失败了需要重新部署时，可使用`rebuild deploy withAlone=true`来只运行部署步骤
"""
    List<String> paramsDescription = []
    transient def script
    public String SSH_SECRET_KEY
    public String GITEE_ACCESS_TOKEN_KEY
    Utils utils
    boolean failFast = true

    @Override
    String toString() {
        return CIName
    }

    ManCI(script, String loggerLevel = "info", String CIName = null) {
        this.script = script
        this.CIName = CIName ?: (script.params.CIName ? script.env.CINAME : "ManCI")
        this.script.env.LOGGER_LEVEL = loggerLevel
        this.logger = new Logger(script)
        this.utils = new Utils(script)
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
        def trigger = stageConfig.get("trigger", "always") // always, pr_merge, pr_open, pr_close, pr_push, pr_test_pass, pr_review_pass, env_match, file_match
        Map<String, Object> envMatches = stageConfig.get("envMatches", [:]) as Map<String, Object>
        String fileMatches = stageConfig.get("fileMatches", "") as String
        List<String> noteMatches = stageConfig.get("noteMatches", []) as List<String>
        String mark = stageConfig.get("mark", "") as String
        if (!stages.containsKey(groupName)) {
            stages[groupName as String] = []
        }
        stages[groupName].add([
                "name": stageName,
                "trigger": trigger,
                "body": body,
                "envMatches": envMatches,
                "fileMatches": fileMatches,
                "noteMatches": noteMatches,
                "mark": mark
        ] as Map<String, Object>)
    }


    def withRun(String nodeLabels = null, Closure body) {
        setParams()

        if(this.script.env.noteBody){
            // 当存在这个环境变量时则解析这个 comment，注入 kv到环境变量
            logger.debug("noteBody: ${this.script.env.noteBody}")
            def commands = this.utils.commandParse(this.script.env.noteBody as String)
            def envArgs = commands.get("kwargs")
            envArgs.each{key, value ->
                logger.debug("${key}: ${value}")
                this.script.env.putAt(key, value)
            }
        }
        script.withCredentials([script.string(credentialsId: GITEE_ACCESS_TOKEN_KEY, variable: "GITEE_ACCESS_TOKEN")]) {
            String repoPath = script.env.giteeTargetNamespace + '/' + script.env.giteeTargetRepoName
            logger.debug "script.env.GITEE_ACCESS_TOKEN: ${script.env.GITEE_ACCESS_TOKEN}"
            giteeApi = new GiteeApi(script, "${script.env.GITEE_ACCESS_TOKEN}", repoPath, script.env.giteePullRequestIid, CIName)
        }
        giteeApi.label(giteeApi.labelWaiting)

        logger.debug "script.env.ref: ${script.env.ref}"
        if ("${script.env.ref}" != "null") {
            this.isCI = true
        }
        logger.debug "SSH_SECRET_KEY: ${SSH_SECRET_KEY}"
        logger.debug "isCI: ${this.isCI}"
        script.node(nodeLabels) {
            giteeApi.label(giteeApi.labelRunning)
            script.stage("checkout") {
                if (script.env.LOGGER_LEVEL && script.env.LOGGER_LEVEL.toLowerCase() == "debug"){
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
            try{
                this.run()
            }catch (Exception e){
                giteeApi.label(giteeApi.labelFailure)
                throw e
            }
            giteeApi.label(giteeApi.labelSuccess)
            giteeApi.testPass()
        }
    }


    def stageRun(String stageName, Closure body) {
        logger.debug("stageRun: ${stageName} start")
        script.stage(stageName){
            body.call()
        }
        logger.debug("stageRun: ${stageName} done")
    }

    def run() {
        HashMap<String, Object> parallelStage = [:] as HashMap<String, Object>
        Exception error = null
        if (isCI) {
            List<String> stageNames = [] as List<String>
            stages.each {
                it.value.each {
                    stageNames.add(it.name as String)
                }
            }
            table = new Table(script, CIName, "", projectDescription + "\n<details>\n<summary>参数说明:</summary>\n\n" + paramsDescription.join("\n") + "\n</details>", stageNames)

            table.text = giteeApi.initComment(table.text)
            table.tableParse()  // 从已有的评论中解析出table
            stages.each { group, st ->
                parallelStage[group] = {
                    st.each {
                        it.noteMatches.add("rebuild ${it.name}")
                        String runStrategy = utils.getStageTrigger(it.trigger as List<String>, giteeApi as GiteeApi, it as Map<String, Object>)
                        String elapsedTime = ""
                        String nowTime = ""
                        Integer runCnt = 0
                        Integer buildResult // 0: success, 1: failure, 2: aborted, 3: skip
                        List<String> failureStages = table.getFailureStages()
                        long startTime = System.currentTimeMillis()
                        def needRun = utils.needRunStage(it.name as String, "gitee", it.trigger as List<String>,
                                it.envMatches as Map<String, Object>, it.fileMatches as String,
                                it.noteMatches as List<String>, failureStages as List<String>)
                        if(!needRun){
                            script.stage(it.name) {
                                // 标记 stage 为跳过
                                org.jenkinsci.plugins.pipeline.modeldefinition.Utils.markStageSkippedForConditional(it.name)
                            }
                            buildResult = 3
                        }else{
                            nowTime = utils.getNowTime()
                            runCnt = table.getStageRunTotal(it.name as String) + 1
                            logger.debug("needRunStage: ${it.name}")
                            try {
                                table.addColumns([[it.name, group,
                                                   "[${table.RUNNING_LABEL}](${script.env.RUN_DISPLAY_URL} \"点击跳转到 jenkins 构建页面\")",
                                                   "0min0s" + "/" + table.getStageRunTotalTime(it.name as String),
                                                   runCnt, nowTime, runStrategy, it.mark]])
                                try {
                                    stageRun(it.name as String, it.body as Closure)
                                }catch (NotSerializableException e) {
                                    error = e as Exception
                                    logger.debug("[${it.name}] 202: ${e}")
                                }

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
                            } catch (Exception e){
                                logger.error("[${it.name}] 217: ${e}")
                                buildResult = 1
                                error = e as Exception
                            }
                            long timeForOne = System.currentTimeMillis() - startTime
                            String OneTime = utils.timestampConvert(timeForOne)
                            String runTotalTimeStr
                            long runTotalTime
                            if (runCnt > 1){
                                runTotalTimeStr = table.getStageRunTotalTime(it.name as String)
                                runTotalTime = utils.reverseTimestampConvert(runTotalTimeStr) + timeForOne

                            }else{
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
                        }else if (buildResult == 3) {
                            table.addColumns([[it.name, group, "[${table.NOT_NEED_RUN_LABEL}](${script.env.RUN_DISPLAY_URL} \"点击跳转到 jenkins 构建页面\")", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        }
                        giteeApi.comment(table.text)
                        if (error){
                            throw error
                        }
                    }
                }
            }
        } else {
            stages.each { k, v ->
                parallelStage[k] = {
                    v.each {
                        def needRun = utils.needRunStageNotCI(it)
                        script.stage(it.name) {
                            if (needRun){
                                it.body.call()
                            }
                        }
                    }
                }
            }
        }
        if(failFast){
            parallelStage['failFast'] = true
        }
        logger.debug("parallelStage Type: ${parallelStage.getClass()}")
        parallelStage.each {
            logger.debug("group: ${it.key}, value: ${it.value.toString()}")
            if (! it.value instanceof boolean ){
                Closure body = it.value as Closure
                logger.debug("body.delegate: ${body.delegate.toString()}")
            }

        }
        Closure setupStage = parallelStage.get("setup") as Closure
        Closure teardownStage = parallelStage.get("teardown") as Closure
        if (setupStage){
            parallelStage.remove("setup")
            setupStage.call()
        }
        if (teardownStage){
            parallelStage.remove("teardown")
        }

        script.parallel parallelStage

        if (teardownStage){
            teardownStage.call()
        }

        if (!table.isSuccessful()){
            throw new Exception("存在未成功的 stage，此次构建将以失败状态退出")
        }
        logger.info "ManCI Finished"
    }
}