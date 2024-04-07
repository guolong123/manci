package org.manci

import java.text.SimpleDateFormat
import groovy.json.JsonSlurperClassic

class Utils {
    def script
    Logger logger

    Utils(script) {
        this.script = script
        logger = new Logger(script)
    }

    static getNowTime() {
        def now = new Date()
        def formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return formatter.format(now)
    }

    boolean needRunStage(String stageName, String ciType="gitee", List<String> trigger,Map<String, Object> envMatches = [:],
                         String  fileMatches = "",
                         List<String> noteMatches = [], List<String> failureStages = []) {
        /* trigger options:
           always: 总是触发
           pr_merge: 当 PR 合并时触发
           pr_open: 当 PR 打开时触发
           pr_close: 当 PR 关闭时触发
           pr_push: 当 PR 推送时触发
           pr_tested: 当 PR 测试通过时触发
           pr_approved: 当 PR 评审通过时触发
           env_match: 当环境匹配时触发
           pr_note: 评论时触发
        */
        boolean needRun = false
        if (ciType == "gitee"){
            needRun = giteeCITrigger(stageName, trigger, envMatches, fileMatches, noteMatches, failureStages)
        }
        return needRun
    }

    static rexContains(list, str){
        for (l in list){
            if (str.matches(l)){
                return true
            }
        }
        return false
    }

    static Map<String, Object> commandParse(String command) {
        /* 这个方法将一段命令行语句解析出来
        例如：
        input: "rebuild stage-test KETADB_PR=1234"
        return {"flag": "rebuild", "args": ["stage-test"], "kwargs": {"KETADB_PR": "1234"}}
        */
        def parts = command.replaceAll(/"([^"]*)"/) { match ->
            match.get(1).replaceAll(" ", "<<>>")
        }.split()

        def flag = parts[0]
        def args = []
        def kwargs = [:]
        parts = parts.drop(1)

        parts.each { part ->
            if (part.contains('=')) {
                def keyValue = part.split('=', 2)
                kwargs[keyValue[0]] = keyValue[1].replaceAll("<<>>", " ")
            } else {
                args.add(part.replaceAll("<<>>", " "))
            }
        }

        return [flag: flag, args: args, kwargs: kwargs]
    }

    @NonCPS
    public static jsonParse(String json) {
        return new groovy.json.JsonSlurperClassic().parseText(json)
    }

    boolean eventHandlerMerge(String fileMatches = "", String commitNumber=null, String targetBranch = "", String sourceBranch = null) {
        if (! fileMatches){
            logger.info("fileMatches is empty, skip eventHandlerMerge")
            return false
        }
        boolean needRun = false
        String cnt = "0"
        try{
            if(commitNumber){
                // merge
                logger.info("eventHandlerMerge: merge")
                cnt = this.script.sh(script:"git diff --name-only ${targetBranch}@{${commitNumber}}...${targetBranch} | grep -c ${fileMatches} | xargs echo", returnStdout: true)
            }else {
                logger.info("eventHandlerMerge: push")
                cnt = this.script.sh(script:"git diff --name-only ${targetBranch}...${sourceBranch} | grep -c ${fileMatches} | xargs echo", returnStdout: true)
            }
        }catch(Exception e){
            logger.info("${e}")
        }
        if (Integer.parseInt(cnt.strip()) > 0){
            needRun = true
        }
        return needRun
    }

    boolean eventHandlerNote(String stageName,List<String> noteMatches = [],List<String> failureStages = [], String fileMatches = "", String targetBranch=null, String sourceBranch = null) {
        boolean needRun = false
        Map<String, Object> commandParse = commandParse(this.script.env.noteBody as String)

        def stageNames = commandParse.get("args") as List<String>
        noteMatches.add(stageName)
        for (sn in stageNames){
            if (stageName.contains(sn.strip())){
                logger.info("eventHandlerNote: stageName contains ${sn}")
                needRun = true
                return needRun
            }
        }
        def flag = commandParse.get("flag") as String
        if (flag != "rebuild") {
            logger.info("eventHandlerNote: flag is not rebuild, skip eventHandlerNote")
            return false
        }
        if (stageNames.size() == 0) {
            // 当直接评论 rebuild时，会重新以代码提交的事件重新运行
            logger.info("eventHandlerNote: stageNames is empty, fileMatches: ${fileMatches}, targetBranch: ${targetBranch}, stageName: ${stageName}")
            needRun = eventHandlerMerge(fileMatches, null, targetBranch, sourceBranch)
        } else if (rexContains(noteMatches, this.script.env.noteBody)) {
            // 当rebuild后面的名称与当前不一致，但runCommands参数中包含评论内容时
            logger.info("eventHandlerNote: stageNames contains note")
            needRun = true
        } else if (stageNames.contains("failure")) {
            if (failureStages.contains(stageName)) {
                logger.info("eventHandlerNote: stageNames contains failure")
                needRun = true
            }
        } else if (stageNames.contains("all")) {
            logger.info("eventHandlerNote: stageNames contains all")
            needRun = true
        }

        return needRun
    }

    def eventHandlerEnv(Map<String, Object> envMatches){
        boolean needRun = true
        String role = envMatches.get('role', 'and')
        Map<String, Object> condition = envMatches.get("condition") as Map<String, Object>

        // 检查condition是否为空，避免不必要的操作
        if (! condition) {
            // 根据业务逻辑，这里可以记录日志，或者采取其他措施
            println("Condition is empty, skipping execution.")
            return needRun// 如果没有必要继续执行，可以提前返回
        }

        // 对于role的处理，如果未来有更多逻辑，考虑使用switch-case进行优化
        boolean shouldRun = role == "and";
        condition.each { k, v ->
            Object envValue = script.env.get(k)
            if (envValue == null || envValue != v) {
                // 如果envValue为null，或者不等于v，则根据role决定是否终止检查
                if (shouldRun) {
                    needRun = false
                    return needRun// 结束each循环
                }
            } else {
                // 如果envValue等于v，根据role决定是否可以设置needRun为true
                if (!shouldRun) {
                    needRun = true
                    return needRun// 结束each循环
                }
            }
        }
        return needRun
    }

    boolean giteeCITrigger(String stageName, List<String> trigger, Map<String, Object> envMatches = [:],
                           String  fileMatches = "",
                           List<String> noteMatches = [], List<String> failureStages = []) {
        boolean needRun = false
        Map<String, Object> jsonBody = jsonParse(this.script.env.jsonbody as String) as Map<String, Object>
        if (trigger.contains("always")){
            needRun = true
        }
        if (trigger.contains("pr_merge")){
            if(this.script.env.giteeActionType == "MERGE"  && jsonBody.action == "merge"){
                def commitNumber = "${jsonBody.pull_request.commits}"
                def targetBranch = "origin/${this.script.env.giteeTargetBranch}"
                needRun = eventHandlerMerge(fileMatches, commitNumber, targetBranch, null)
                logger.info("eventHandlerMerge: merge, commitNumber: ${commitNumber}, needRun: ${needRun}")
            }
        }
        if (trigger.contains("pr_push")){
            if(this.script.env.giteeActionType == "MERGE"  && (jsonBody.action == "update" || jsonBody.action == "open")){
                String targetBranch = "origin/${this.script.env.giteeTargetBranch}"
                needRun = eventHandlerMerge(fileMatches, null, targetBranch, script.env.giteePullRequestLastCommit as String)
                logger.info("eventHandlerMerge: push, needRun: ${needRun}")
            }
        }
        if (trigger.contains("pr_close")){
            if(this.script.env.giteeActionType == "MERGE"  && jsonBody.action == "close"){
                needRun = true
                logger.info("eventHandlerMerge: close, needRun: ${needRun}")
            }
        }
        if (trigger.contains("pr_open")){
            if(this.script.env.giteeActionType == "MERGE"  && jsonBody.action == "open"){
                needRun = true
                logger.info("eventHandlerMerge: open, needRun: ${needRun}")
            }
        }
        if (trigger.contains("pr_approved")){
            if(this.script.env.giteeActionType == "MERGE"  && jsonBody.action == "approved"){
                needRun = true
                logger.info("eventHandlerMerge: approved, needRun: ${needRun}")
            }
        }
        if (trigger.contains("pr_tested")){
            if(this.script.env.giteeActionType == "MERGE"  && jsonBody.action == "tested"){
                needRun = true
                logger.info("eventHandlerMerge: tested, needRun: ${needRun}")
            }
        }
        if (trigger.contains("pr_note")){
            if(this.script.env.giteeActionType == "NOTE"  && jsonBody.action == "comment"){
                String targetBranch = "origin/${this.script.env.giteeTargetBranch}"
                needRun = eventHandlerNote(stageName, noteMatches, failureStages, fileMatches, targetBranch, script.env.giteePullRequestLastCommit as String)
                logger.info("eventHandlerNote: note, needRun: ${needRun}")
            }
        }
        if (trigger.contains("env_match")) {
            needRun = eventHandlerEnv(envMatches)
            logger.info("eventHandlerEnv: env_match, needRun: ${needRun}")
        }
        return needRun

    }

    static String timestampConvert(long timestamp) {
        Integer minutes = 0
        Integer seconds = 0


        if (timestamp >= 60000) { // 1 minute in milliseconds
            minutes = (int) (timestamp / 60000)
            timestamp %= 60000
        }

        if (timestamp >= 1000) { // 1 second in milliseconds
            seconds = (int) (timestamp / 1000)
        }

        // Format the result string with leading zeros for consistency
        String formattedMinutes = minutes.toString().padLeft(1, '0')
        String formattedSeconds = seconds.toString().padLeft(1, '0')

        return "${formattedMinutes}min${formattedSeconds}s" as String
    }

    static long reverseTimestampConvert(String formattedTimestamp) {
        def parts = formattedTimestamp =~ /\d+/ // 使用正则表达式匹配数字序列

        // 确保输入格式正确，包含至少小时、分钟和秒
        if (parts.size() != 2) {
            throw new IllegalArgumentException("Invalid formatted timestamp: ${formattedTimestamp}")
        }

        int minutes = parts[0].toInteger()
        int seconds = parts[1].toInteger()

        // 计算总毫秒数
        long totalMilliseconds =  minutes * 60000 + seconds * 1000

        return totalMilliseconds
    }
}
