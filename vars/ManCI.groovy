import org.manci.Table
import org.manci.GiteeApi
import org.manci.Logger
import org.manci.Utils


class ManCI {
    Map<String, List<Map<String, Object>>> stages = [:]
    boolean isCI = false
    public String CIName = "ManCI V1"
    Table table
    Logger logger
    GiteeApi giteeApi
    public List<Map<String, Object>> parameters
    public String projectDescription
    List<String> paramsDescription = []
    def script
    public String SSH_SECRET_KEY
    public String GITEE_ACCESS_TOKEN_KEY
    String DEBUG
    Utils utils


    ManCI(script, String CIName = null) {
        this.script = script
        this.CIName = CIName ?: (script.params.CIName ? script.env.CINAME : "ManCI")
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

    def stage(String stageName, Map<String, Object> stageConfig, Closure body) {
        def groupName = stageConfig.get("group", "default")
        def trigger = stageConfig.get("trigger", "always") // always, pr_merge, pr_open, pr_close, pr_push, pr_test_pass, pr_review_pass, env_match, file_match
        Map<String, Object> envMatches = stageConfig.get("envMatches", [:]) as Map<String, Object>
        String fileMatches = stageConfig.get("fileMatches", "") as String
        List<String> noteMatches = stageConfig.get("noteMatches", []) as List<String>
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
        ])
    }

    def withRun(String nodeLabels = null, Closure body) {
        this.script.env.DEBUG = DEBUG

        setParams()

        logger.info "script.env.ref: ${script.env.ref}"
        if ("${script.env.ref}" != "null") {
            this.isCI = true
        }
        logger.info "SSH_SECRET_KEY: ${SSH_SECRET_KEY}"
        logger.info "isCI: ${this.isCI}"
        script.node(nodeLabels) {
            script.stage("checkout") {
                script.sh 'env'
                def scmVars = script.checkout script.scm
                if (this.isCI) {
                    logger.info "checkout: url ${script.env.giteeSourceRepoSshUrl}, branch: ${script.env.ref}"
                    script.checkout([$class           : 'GitSCM', branches: [[name: script.env.ref]], extensions: [],
                                     userRemoteConfigs: [[credentialsId: SSH_SECRET_KEY,
                                                          url          : "${script.env.giteeSourceRepoSshUrl}"]]
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
                body.call()
                this.run()
            }
        }
    }

    def run() {
        Map<String, Closure> parallelStage = [:] as Map<String, Closure>
        Exception error = null
        if (isCI) {
            List<String> stageNames = [] as List<String>
            stages.each {
                it.value.each {
                    stageNames.add(it.name as String)
                }
            }
            table = new Table(script, CIName, "", projectDescription + "\n<details>\n<summary>参数说明:</summary>\n\n" + paramsDescription.join("\n") + "\n</details>", stageNames)
            script.withCredentials([script.string(credentialsId: GITEE_ACCESS_TOKEN_KEY, variable: "GITEE_ACCESS_TOKEN")]) {
                String repoPath = script.env.giteeSourceNamespace + '/' + script.env.giteeSourceRepoName
                logger.info "script.env.GITEE_ACCESS_TOKEN: ${script.env.GITEE_ACCESS_TOKEN}"
                giteeApi = new GiteeApi(script, "${script.env.GITEE_ACCESS_TOKEN}", repoPath, script.env.giteePullRequestIid, CIName)
            }
            table.text = giteeApi.initComment(table.text)
            table.tableParse()  // 从已有的评论中解析出table
            stages.each { group, st ->
                parallelStage[group] = {
                    st.each {
                        String runStrategy = ""
                        it.noteMatches.add("rebuild")
                        it.noteMatches.add("rebuild ${it.name}")
                        it.trigger.each {tg ->
                            if (tg == "pr_note"){
                                runStrategy += "[:fa-pencil:](#note_${giteeApi.CICommentID} \"该Stage可通过评论${it.noteMatches.collect { it.replace('|', '\\\\|') }}触发\") "
                            }
                            if (tg == "pr_push"){
                                runStrategy += "[:fa-paypal:](#note_${giteeApi.CICommentID} \"该Stage可在推送代码匹配正则${it.fileMatches.replace('\\|', '\\\\|')}时自动触发\") "
                            }
                            if (tg == "pr_merge"){
                                runStrategy += "[:fa-maxcdn:](#note_${giteeApi.CICommentID} \"该Stage可在合并代码匹配正则${it.fileMatches.replace('\\|', '\\\\|')}时自动触发\") "
                            }
                            if (tg == "env_match"){
                                runStrategy += "[:fa-th:](#note_${giteeApi.CICommentID} \"该Stage可在环境变量匹配时触发: ${it.envMatches}\") "
                            }
                            if (tg == "always"){
                                runStrategy += "[:fa-font:](#note_${giteeApi.CICommentID} \"该Stage无论如何都会触发\") "
                            }
                            if (tg == "pr_open"){
                                runStrategy += "[:fa-toggle-on:](#note_${giteeApi.CICommentID} \"该Stage会在 PR 打开时触发(不包括 reopen)\") "
                            }
                            if (tg == "pr_close"){
                                runStrategy += "[:fa-times-circle:](#note_${giteeApi.CICommentID} \"该Stage会在 PR 关闭时触发\") "
                            }
                            if (tg == "pr_tested"){
                                runStrategy += "[:fa-check:](#note_${giteeApi.CICommentID} \"该Stage会在 PR 测试通过时触发\") "
                            }
                            if (tg == "pr_approved"){
                                runStrategy += "[:fa-eye:](#note_${giteeApi.CICommentID} \"该Stage会在 PR 审核通过时触发\") "
                            }
                        }
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
                                script.echo "skip stage: ${it.name}"
                            }
                            buildResult = 3

                        }else{
                            logger.debug("needRunStage: ${it.name}")
                            try {
                                script.stage(it.name) {
                                    logger.debug("stage: ${it.name}")
                                    it.body.call()
                                    logger.debug("stage: ${it.name} done")
                                }
                                buildResult = 0
                                logger.debug("buildResult: ${buildResult}")
                            } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                                logger.error("[${it.name}]: ${e}")
                                buildResult = 2
                                error = e as Exception
                            } catch (InterruptedException e) {
                                logger.error("[${it.name}]: ${e}")
                                buildResult = 2
                                error = e as Exception
                            } catch (NotSerializableException e) {
                                logger.error("[${it.name}]: ${e}")
                                buildResult = 0
                                error = e as Exception
                            } catch (Exception e){
                                logger.error("[${it.name}]: ${e}")
                                buildResult = 1
                                error = e as Exception
                            }
                            long timeForOne = System.currentTimeMillis() - startTime
                            String OneTime = utils.timestampConvert(timeForOne)
                            nowTime = utils.getNowTime()
                            runCnt = table.getStageRunTotal(it.name as String) + 1
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
                            table.addColumns([[it.name, group, table.SUCCESS_LABEL, elapsedTime, runCnt, nowTime, runStrategy, ""]])
                        } else if (buildResult == 1) {
                            table.addColumns([[it.name, group, table.FAILURE_LABEL, elapsedTime, runCnt, nowTime, runStrategy, ""]])
                        } else if (buildResult == 2) {
                            table.addColumns([[it.name, group, table.ABORTED_LABEL, elapsedTime, runCnt, nowTime, runStrategy, ""]])
                        }else if (buildResult == 3) {
                            table.addColumns([[it.name, group, table.NOT_NEED_RUN_LABEL, elapsedTime, runCnt, nowTime, runStrategy, ""]])
                        }
                        giteeApi.comment(table.text)
                        logger.info(table.text)
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
        if (error){
            throw error
        }
        logger.info "ManCI Finished"
    }
}