import org.manci.Table
import org.manci.GiteeApi
import org.manci.Logger
import org.manci.Utils
import org.manci.Event
import org.manci.Group
import org.manci.WarningException


class ManCI implements Serializable {
    transient Map<String, List<Map<String, Object>>> stagesInfo = [:]
    public String CIName = "ManCI V1"
    Table table = null
    transient Exception error = null
    Logger logger
    GiteeApi giteeApi
    String runModel = "parallel" // single, parallel
    String instructionPrefix = "run"
    transient public List<Map<String, Object>> parameters
    public String projectDescription = """
## 指令说明
**指令**: `${instructionPrefix} [stageName [stageName...]|groupName [groupName...]|failure] [env1=value1 env2=value2...] [withAlone=true|false]`
**描述**:
> * 无参数时，按照提交代码时的行为重新构建。
> * 指定一个或多个 `stage name`（空格分隔），重建指定阶段。
> * 指定一个或多个 `group name`（空格分隔），可以触发一组相关的 stage 进行重建。
> * 使用 `${instructionPrefix} failure` 重建所有失败阶段。
> * 可选地附加一组环境变量设置（键值对，等号分隔），在重建过程中将其注入到运行时环境中。
> * 通过添加参数 withAlone=true，确保仅执行当前指定的 stage，而不执行其关联的其他 stage。默认情况下，withAlone=false，即可能执行与指定 stage 关联的其他 stage。
> * 在本条评论下回复指令，可以在 `OnUpdate` 事件触发时按照回复的指令进行构建。
"""
    Map<String, Map<String, Object>> paramsDescriptionMap = [:]
    transient def script
    public String SSH_SECRET_KEY
    public String GITEE_ACCESS_TOKEN_KEY
    Utils utils
    boolean failFast = true
    Event event
    boolean allStageOnComment = false  // 是否允许所有阶段都通过评论触发
    Group group = null
    String jobTriggerType = "pullRequest" // 触发类型，目前支持：pullRequest, push, manual
    boolean autoTest = false

    ManCI(script, String loggerLevel = "info", String CIName = null, String instructionPrefix = "run") {
        this.script = script
        if (CIName) {
            this.CIName = CIName
        }
        this.script.env.LOGGER_LEVEL = loggerLevel
        this.logger = new Logger(script)
        this.utils = new Utils(script)
        if (instructionPrefix) {
            this.instructionPrefix = instructionPrefix
        }

        this.event = new Event(script, this.instructionPrefix)
        group = new Group()

    }

    def setParams() {
        List<Object> propertiesParams = []
        logger.debug("params: ${parameters[0].getClass()}")
        if (this.parameters) {
            this.parameters = this.parameters as List<Map<String, Object>>
        }
        logger.debug("params: ${parameters[0].getClass()}")
        this.parameters.each {
            paramsDescriptionMap.put(it.name as String, ["type": it.type, "description": it.description, "defaultValue": it.defaultValue, "choices": it.choices])
            if (it.type == "choice") {
                propertiesParams.add(script.choice(name: it.name, description: it.description, choices: it.choices))
            } else if (it.type == "string") {
                propertiesParams.add(script.string(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "boolean") {
                propertiesParams.add(script.booleanParam(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "password") {
                propertiesParams.add(script.password(name: it.name, description: it.description))
            } else if (it.type == "text") {
                propertiesParams.add(script.text(name: it.name, description: it.description, defaultValue: it.defaultValue))
            } else if (it.type == "file") {
                propertiesParams.add(script.file(name: it.name, description: it.description))
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
        if (!stagesInfo.containsKey(groupName)) {
            stagesInfo[groupName as String] = []
        }
        stagesInfo[groupName].add([
                "name"       : stageName,
                "trigger"    : trigger,
                "envMatches" : envMatches,
                "fileMatches": fileMatches,
                "noteMatches": noteMatches,
                "mark"       : mark,
                "fastFail"   : fastFail,
                "group"      : groupName
        ])
        group.addStage(stageName, body)
    }

    def setEnvironment(Map<String, String> envArgs) {
        envArgs.each { key, value ->
            logger.debug("${key}: ${value}")
            this.script.env.putAt(key, value)
        }
    }

    def withRun(String nodeLabels = null, Closure body) {
        setParams()
        logger.info("action type: ${script.env.giteeActionType}")
        if (this.script.env.noteBody) {
            // 当存在这个环境变量时则解析这个 comment，注入 kv到环境变量
            logger.info("noteBody: ${this.script.env.noteBody}")
            def commands = this.utils.commandParse(this.script.env.noteBody as String)
            def envArgs = commands.get("kwargs")
            setEnvironment(envArgs as Map<String, String>)
        }

        if ("${script.env.ref}" != "null" && script.env.giteeActionType != "PUSH") {
            this.jobTriggerType = "pullRequest"
        } else if (script.env.giteeActionType == "PUSH") {
            this.jobTriggerType = "push"
        } else {
            this.jobTriggerType = "manual"
        }
        script.withCredentials([script.string(credentialsId: GITEE_ACCESS_TOKEN_KEY, variable: "GITEE_ACCESS_TOKEN")]) {
            String repoPath = script.env.giteeTargetNamespace + '/' + script.env.giteeTargetRepoName
            logger.debug "script.env.GITEE_ACCESS_TOKEN: ${script.env.GITEE_ACCESS_TOKEN}"
            giteeApi = new GiteeApi(script, "${script.env.GITEE_ACCESS_TOKEN}", repoPath, script.env.giteePullRequestIid, CIName)
        }
        if (jobTriggerType == "pullRequest") {
            if(autoTest){
                giteeApi.resetTest()
            }
            giteeApi.label(giteeApi.labelWaiting)
        }
        script.node(nodeLabels) {
            if (jobTriggerType == "pullRequest") {
                giteeApi.label(giteeApi.labelRunning)
            }

            def checkoutStage = {
                if (script.env.LOGGER_LEVEL && script.env.LOGGER_LEVEL.toLowerCase() == "debug") {
                    script.sh 'env'
                }
                def scmVars = script.checkout script.scm
                scmVars.each {
                    logger.debug("${it.key}: ${it.value}")
                }
                if (jobTriggerType == "pullRequest") {
                    logger.debug "checkout: url ${script.env.giteeTargetRepoSshUrl}, branch: ${script.env.ref}"

                    script.checkout([$class           : 'GitSCM', branches: [[name: script.env.ref]], extensions: [],
                                     userRemoteConfigs: [[credentialsId: SSH_SECRET_KEY,
                                                          url          : "${script.env.giteeTargetRepoSshUrl}"]]])


                } else {
                    if (!script.env.BRANCH_NAME) {
                        script.env.BRANCH_NAME = scmVars.GIT_BRANCH
                    }
                    def gitUrl = scmVars.GIT_URL
                    script.checkout([$class           : 'GitSCM',
                                     branches         : [[name: script.env.BRANCH_NAME]], extensions: [],
                                     userRemoteConfigs: [[credentialsId: SSH_SECRET_KEY,
                                                          url          : gitUrl]]
                    ])
                }
            }

            stagesInfo["before"] = []
            stagesInfo["before"].add([
                    "name": "checkout",
                    "trigger"    : ["Always"],
                    "envMatches" : [:],
                    "fileMatches": "",
                    "noteMatches": [],
                    "mark"       : "[:fa-git-square:](# \"代码检出，这是一个内置的 stage\")",
                    "fastFail"   : true,
                    "group"      : "before"
            ])
            group.addStage("checkout", checkoutStage)

            body.call()
            try {
                this.run()
            } catch (Exception e) {
                if (jobTriggerType == "pullRequest") {
                    giteeApi.label(giteeApi.labelFailure)
                }
                throw e
            }
            if (jobTriggerType == "pullRequest") {
                giteeApi.label(giteeApi.labelSuccess)
                if(autoTest){
                    giteeApi.testPass()
                }
            }
        }
    }

    Exception runStage(String name, boolean needRun) {
        Exception error = null
        if (needRun) {
            try {
                script.stage(name, group.getStage(name as String))
            } catch (Exception e) {
                error = e
                logger.error("stage [${name}] run with error: ${e}")
            }
        } else {
            script.stage(name) {
                org.jenkinsci.plugins.pipeline.modeldefinition.Utils.markStageSkippedForConditional(name)
            }
        }
        return error
    }


    def run() {
        if (jobTriggerType == "pullRequest") {
            def paramsDescription = [] as List<String>
            paramsDescriptionMap.each { name, thisObject ->
                def defaultValue = ""
                if(thisObject.get("type") as String == "string"){
                    defaultValue = "默认值：${thisObject.get("defaultValue")},"
                }else if (thisObject.get("type") == "choice"){
                    defaultValue = "可选项：${thisObject.get("choices").toString()},"
                }else if (thisObject.get("type") == "boolean"){
                    defaultValue = "默认值：${thisObject.get("defaultValue") == true ? "true" : "false"},"
                }

                paramsDescription.add("- **${name}**: ${thisObject.description}, 类型：`${thisObject.get("type")}`, ${defaultValue} 当前值: `${script.env.getAt(name)}`")
            }
            table = new Table(script, CIName, "", projectDescription + "\n<details>\n<summary><b>参数说明</b>(点击展开)</summary>\n\n" + paramsDescription.join("\n") + "\n</details>")

            List<List<Object>> stageNames = []
            stagesInfo.each {
                it.value.each {
                    stageNames.add([it.get('name'), it.get('group'), table.WAITING_LABEL, "0m0s/0m0s", "0", "", "", ""])
                }
            }
            table.init(stageNames)

            table.text = giteeApi.initComment(table.text)
            table.tableParse()  // 从已有的评论中解析出table
            def replyComment = giteeApi.getReplyComment()
            Map<String, Object> jsonBody = utils.jsonParse(this.script.env.jsonbody as String) as Map<String, Object>
            if (script.env.giteeActionType == "MERGE" && jsonBody.get("action") == "update" && replyComment && replyComment.get("body").toString().startsWith(instructionPrefix)) {
                logger.debug "replyComment: ${replyComment}"
                script.env.noteBody = replyComment.get("body")
                script.env.forceActionType = "NOTE"
                def commands = this.utils.commandParse(this.script.env.noteBody as String)
                def envArgs = commands.get("kwargs") as Map<String, String>
                setEnvironment(envArgs)
            }

            stagesInfo.each { groupName, stg ->
                def body = {
                    stg.each {
                        if (allStageOnComment && !it.trigger.contains("OnComment")) {
                            it.trigger.add("OnComment")
                        }
                        Exception localError
                        it.get('noteMatches').add("rebuild ${it.name}")
                        String runStrategy = event.getStageTrigger(it.trigger as List<String>, giteeApi as GiteeApi, it as Map<String, Object>)
                        Integer buildResult = 0 // 0: success, 1: failure, 2: aborted, 3: skip
                        List<String> failureStages = table.getFailureStages()
                        long startTime = System.currentTimeMillis()
                        boolean needRun = event.needRunStage(it as Map<String, Object>, "gitee", failureStages as List<String>, error)
                        String stageUrl = "${script.env.RUN_DISPLAY_URL} \"点击跳转到 jenkins 构建页面\""
                        String nowTime = utils.getNowTime()
                        Integer runCnt = table.getStageRunTotal(it.name as String) + 1
                        if (needRun) {
                            boolean needUpdate = table.addColumns([
                                    [it.name, groupName,
                                     "[${table.RUNNING_LABEL}](${stageUrl})",
                                     "0m0s" + "/" + table.getStageRunTotalTime(it.name as String),
                                     runCnt, nowTime, runStrategy, it.mark]
                            ])
                            if (needUpdate) {
                                giteeApi.comment(table.text)
                            }
                        }
                        localError = runStage(it.name as String, needRun)
                        String errorMessage = ""
                        if(localError != null){
                            errorMessage = localError.getMessage()
                        }

                        if (!needRun) {
                            buildResult = 3
                        } else if (localError instanceof WarningException) {
                            logger.warn("warning info: ${localError.getMessage().replace('\n', ' ')}")
                            stageUrl = "${script.env.RUN_DISPLAY_URL} \"${localError.getMessage().replace('\n', ' ')}\""
                            script.currentBuild.result = "UNSTABLE"
                            localError = null
                            buildResult = 4
                        } else if (localError instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException) {
                            buildResult = 2
                            if (!errorMessage){
                                errorMessage = "build aborted"
                            }
                        } else if (localError instanceof InterruptedException) {
                            buildResult = 2
                            if (!errorMessage){
                                errorMessage = "build aborted"
                            }
                        } else if (localError instanceof NotSerializableException) {
                            buildResult = 1
                            if (!errorMessage){
                                errorMessage = "序列化错误。尽管该项目中已经尽可能避免 Jenkins 序列化错误，可能还是会存在漏掉的情况，遇到此情况请上报 issue"
                            }
                        } else if (localError instanceof Exception) {
                            buildResult = 1
                            if (!errorMessage){
                                errorMessage = "其它错误，请检查stage 内相关代码逻辑"
                            }
                        }

                        if (localError) {
                            stageUrl = "${script.env.RUN_DISPLAY_URL} \"${errorMessage.replace('\n', ' ')}\""
                            logger.info("stageUrl: ${stageUrl}")
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
                        String elapsedTime = utils.timestampConvert(timeForOne) + "/" + utils.timestampConvert(runTotalTime)

                        if (buildResult == 0) {
                            table.addColumns([[it.name, groupName, "[${table.SUCCESS_LABEL}](${stageUrl})", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        } else if (buildResult == 1) {
                            table.addColumns([[it.name, groupName, "[${table.FAILURE_LABEL}](${stageUrl})", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        } else if (buildResult == 2) {
                            table.addColumns([[it.name, groupName, "[${table.ABORTED_LABEL}](${stageUrl})", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        } else if (buildResult == 3) {
                            table.addColumns([[it.name, groupName, "[${table.NOT_NEED_RUN_LABEL}](${stageUrl})", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        } else if (buildResult == 4) {
                            table.addColumns([[it.name, groupName, "[${table.WARNING_LABEL}](${stageUrl})", elapsedTime, runCnt, nowTime, runStrategy, it.mark]])
                        }
                        giteeApi.comment(table.text)
                        if (localError && it.fastFail as boolean) {
                            throw localError
                        } else if (localError) {
                            this.error = localError
                        }
                    }
                }
                group.addGroup(groupName as String, body)
                logger.debug("group: \${group.getGroup(groupName)} add group: ${groupName} success")
            }
        } else if (jobTriggerType == "push") {
            stagesInfo.each { k, v ->
                group.addGroup(k as String) {
                    Exception localError
                    v.each {
                        def needRun = event.needRunStageForPush(it as Map<String, Object>, error)
                        localError = runStage(it.name as String, needRun)
                        if (localError && it.fastFail as boolean) {
                            throw localError
                        } else if (localError) {
                            this.error = localError
                        }
                    }
                }
            }
        } else {
            stagesInfo.each { k, v ->
                group.addGroup(k as String) {
                    Exception localError
                    v.each {
                        def needRun = event.needRunStageForManual(it as Map<String, Object>, error)
                        localError = runStage(it.name as String, needRun)
                        if (localError && it.fastFail as boolean) {
                            throw localError
                        } else if (localError) {
                            this.error = localError
                        }
                    }
                }
            }
        }

        if (runModel == "parallel") {
            parallelRun()
        } else {
            signalRun()
        }

        if (!error && table && !table.isSuccessful()) {
            error = new Exception("存在未成功的 stage，此次构建将以失败状态退出")
        }

        if (error) {
            logger.error("ManCI Finished with error: ${error}")
            throw error
        }
    }

    def signalRun() {
        group.groups.each {
            if (it.value instanceof Closure) {
                it.value.call()
            }
        }
    }

    def parallelRun() {
        group.groups.put('failFast', failFast)
        Closure beforeStage = group.groups.get("before") as Closure
        Closure afterStage = group.groups.get("after") as Closure
        if (beforeStage instanceof Closure) {
            group.groups.remove("before")
            beforeStage.call()
        }
        if (afterStage) {
            group.groups.remove("after")
        }

        try {
            script.parallel(group.groups)
        } catch (Exception e) {
            error = e
        }

        if (afterStage instanceof Closure) {
            logger.debug("afterStage.delegate.error: ${afterStage.delegate.getAt("error")}")
            afterStage.call()
        }
    }
}