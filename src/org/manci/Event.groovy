package org.manci

class Event {
    def script
    Logger logger
    String instructionPrefix

    Event(script, String instructionPrefix = null) {
        this.script = script
        logger = new Logger(script)
        if (instructionPrefix){
            this.instructionPrefix = instructionPrefix
        } else {
            this.instructionPrefix = "run"
        }
    }

    def utils = new Utils(script)

    boolean needRunStage(Map<String, Object> stage, String ciType = "gitee", List<String> failureStages, Exception error = null) {
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
        if (ciType == "gitee") {
            needRun = giteeCITrigger(stage, failureStages, error)
        }
        return needRun
    }
    static String getStageTrigger(List<String> triggers, GiteeApi giteeApi, Map<String, Object> stage) {
        String runStrategy = ""

        triggers.each { trigger ->
            if (trigger == "OnComment") {
                runStrategy += "[:fa-pencil:](#note_${giteeApi.CICommentID} \"该Stage可通过评论${stage.noteMatches.collect { it.replace('|', '\\|') }}触发\") "
            }
            if (trigger == "OnPush") {
                runStrategy += "[:fa-paypal:](#note_${giteeApi.CICommentID} \"该Stage可在推送代码匹配正则${stage.fileMatches.replace('|', '\\|')}时自动触发\") "
            }
            if(trigger == "OnUpdate"){
                runStrategy += "[:fa-refresh:](#note_${giteeApi.CICommentID} \"该Stage可在代码更新时并且修改的文件路径匹配正则：[${stage.fileMatches.replace('|', '\\|')}] 时自动触发\") "
            }
            if (trigger == "OnMerge") {
                runStrategy += "[:fa-maxcdn:](#note_${giteeApi.CICommentID} \"该Stage可在代码合并时并且修改的文件路径匹配正则：[${stage.fileMatches.replace('|', '\\|')}] 时自动触发\") "
            }
            if (trigger == "OnEnv") {
                runStrategy += "[:fa-list:](#note_${giteeApi.CICommentID} \"该Stage可在环境变量匹配时触发: ${stage.envMatches}\") "
            }
            if (trigger == "Always") {
                runStrategy += "[:fa-font:](#note_${giteeApi.CICommentID} \"该Stage无论如何都会触发\") "
            }
            if (trigger == "OnOpen") {
                runStrategy += "[:fa-toggle-on:](#note_${giteeApi.CICommentID} \"该Stage会在 PR 打开时触发(不包括 reopen)\") "
            }
            if (trigger == "OnClose") {
                runStrategy += "[:fa-times-circle:](#note_${giteeApi.CICommentID} \"该Stage会在 PR 关闭时触发\") "
            }
            if (trigger == "OnTestPass") {
                runStrategy += "[:fa-check:](#note_${giteeApi.CICommentID} \"该Stage会在 PR 测试通过时触发\") "
            }
            if (trigger == "OnApproved") {
                runStrategy += "[:fa-eye:](#note_${giteeApi.CICommentID} \"该Stage会在 PR 审核通过时触发\") "
            }
            if (trigger == "OnBuildPass") {
                runStrategy += "[:fa-check-square-o:](#note_${giteeApi.CICommentID} \"该 stage 会在之前所有 stage 都执行成功时触发\") "
            }
            if (trigger == "OnBuildFailure") {
                runStrategy += "[:fa-bug:](#note_${giteeApi.CICommentID} \"该Stage会在之前任意 stage 执行失败时触发\") "
            }
        }
        return runStrategy
    }
    boolean eventHandlerMerge(String fileMatches = "", String commitNumber = null, String targetBranch = "", String sourceBranch = null) {
        if (!fileMatches) {
            logger.debug("fileMatches is empty, skip eventHandlerMerge")
            return true
        }
        boolean needRun = false
        String cnt = "0"
        try {
            if (commitNumber) {
                logger.debug("eventHandlerMerge: merge")
                cnt = this.script.sh(script: "git diff --name-only ${targetBranch}@{${commitNumber}}...${targetBranch} | grep -c ${fileMatches} | xargs echo", returnStdout: true)
            } else {
                logger.debug("eventHandlerMerge: push")
                cnt = this.script.sh(script: "git diff --name-only ${targetBranch}...${sourceBranch} | grep -c ${fileMatches} | xargs echo", returnStdout: true)
            }
        } catch (Exception e) {
            logger.error("${e}")
        }
        if (Integer.parseInt(cnt.strip()) > 0) {
            needRun = true
        }
        return needRun
    }
    boolean eventHandlerNote(List<String> triggers, String stageName, String groupName, List<String> noteMatches = [], List<String> failureStages = [], String fileMatches = "", String targetBranch = null, String sourceBranch = null) {
        boolean needRun = false
        Map<String, Object> commandParse = utils.commandParse(this.script.env.noteBody as String)
        logger.debug("eventHandlerNote: noteMatches: ${noteMatches}, failureStages: ${failureStages}, fileMatches: ${fileMatches}, targetBranch: ${targetBranch}, sourceBranch: ${sourceBranch}, stageName: ${stageName}, groupName: ${groupName}")

        def stageNames = commandParse.get("args") as List<String>
        noteMatches.add(stageName)
        for (sn in stageNames) {
            if (stageName.contains(sn.strip())) {
                logger.debug("eventHandlerNote: stageName contains ${sn}")
                needRun = true
                return needRun
            }
        }
        if (script.env.withAlone == "true") {
            return false
        }
        def flag = commandParse.get("flag") as String
        if (flag != instructionPrefix) {
            logger.debug("eventHandlerNote: flag is not ${instructionPrefix}, skip eventHandlerNote")
            return false
        }
        if (stageNames.size() == 0) {
            // 当直接评论 ${instructionPrefix} 时，会重新以代码提交的事件重新运行
            logger.debug("eventHandlerNote: stageNames is empty, fileMatches: ${fileMatches}, targetBranch: ${targetBranch}, stageName: ${stageName}")
            if (triggers.contains("pr_push")) {
                needRun = eventHandlerMerge(fileMatches, null, targetBranch, sourceBranch)
            } else {
                needRun = false
            }

        } else if (utils.rexContains(noteMatches, this.script.env.noteBody)) {
            // 当 ${instructionPrefix} 后面的名称与当前不一致，但runCommands参数中包含评论内容时
            logger.debug("eventHandlerNote: stageNames contains note")
            needRun = true
        } else if (stageNames.contains("failure")) {
            if (failureStages.contains(stageName)) {
                logger.debug("eventHandlerNote: stageNames contains failure")
                needRun = true
            }
        } else if (stageNames.contains(groupName)) {
            needRun = true
        }
        return needRun
    }
    def eventHandlerEnv(Map<String, Object> envMatches) {
        boolean needRun = true
        String role = envMatches.get('role', 'and')
        Map<String, Object> condition = envMatches.get("condition") as Map<String, Object>

        if (!condition) {
            // 根据业务逻辑，这里可以记录日志，或者采取其他措施
            logger.debug("Condition is empty, skipping execution.")
            return needRun// 如果没有必要继续执行，可以提前返回
        }

        boolean shouldRun = role == "and";
        condition.each { k, v ->
            Object envValue = script.env.getAt(k)
            if (envValue == null || envValue != v) {
                logger.info("${envValue} == ${v}")
                // 如果envValue为null，或者不等于v，则根据role决定是否终止检查
                if (shouldRun) {
                    needRun = false
                    return needRun
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
    boolean needRunStageForPush(Map<String, Object> stage, Exception error = null) {
        // 非 CI 场景下 trigger 为 OnEnv、 Always 、 OnManual、OnBuildPass、OnBuildFailure 时，需要执行
        List<String> trigger = stage.get("trigger") as List<String>

        boolean needRun = false
        String fileMatches = stage.get("fileMatches", "") as String
        if (trigger.contains("OnPush") && this.script.env.giteeActionType && this.script.env.giteeActionType == "PUSH") {
            Map<String, Object> jsonBody = utils.jsonParse(this.script.env.jsonbody as String) as Map<String, Object>
            String afterCommit = "${jsonBody.after}"
            String beforeCommit = "${jsonBody.before}"
            needRun = eventHandlerMerge(fileMatches, null, afterCommit, beforeCommit)
            logger.debug("eventHandlerMerge: push, needRun: ${needRun}")
        }
        if (trigger.contains("OnEnv")) {
            needRun = eventHandlerEnv(stage.get("envMatches") as Map<String, Object>)
        }
        if (trigger.contains("Always")) {
            needRun = true
        }
        if (trigger.contains("OnBuildPass") && ! error) {
            needRun = true
        }
        if (trigger.contains("OnBuildFailure") && error) {
            needRun = true
        }
        return needRun
    }
    boolean needRunStageForManual(Map<String, Object> stage, Exception error = null) {
        // 非 CI 场景下 trigger 为 OnEnv、 Always 、 OnManual、OnBuildPass、OnBuildFailure 时，需要执行
        List<String> trigger = stage.get("trigger") as List<String>

        boolean needRun = false
        if (trigger.contains("OnEnv")) {
            needRun = eventHandlerEnv(stage.get("envMatches") as Map<String, Object>)
        }
        if (trigger.contains("Always")) {
            needRun = true
        }
        if (trigger.contains("OnBuildPass") && ! error) {
            needRun = true
        }
        if (trigger.contains("OnBuildFailure") && error) {
            needRun = true
        }
        if (trigger.contains("OnManual")){
            needRun = true
        }
        return needRun
    }
    boolean giteeCITrigger(Map<String, Object> stage, List<String> failureStages, Exception error=null) {
        boolean needRun = false
        List<String> trigger = stage.get("trigger") as List<String>
        String fileMatches = stage.get("fileMatches", "") as String
        String stageName = stage.get("name") as String
        String groupName = stage.get("group", "") as String
        List<String> noteMatches = stage.get("noteMatches", []) as List<String>
        Map<String, Object> envMatches = stage.get("envMatches", []) as Map<String, Object>
        Map<String, Object> jsonBody = utils.jsonParse(this.script.env.jsonbody as String) as Map<String, Object>
        if (trigger.contains("Always")) {
            needRun = true
        }
        if (trigger.contains("OnMerge")) {
            if (this.script.env.giteeActionType == "MERGE" && jsonBody.action == "merge") {
                def commitNumber = "${jsonBody.pull_request.commits}"
                def targetBranch = "origin/${this.script.env.giteeTargetBranch}"
                needRun = eventHandlerMerge(fileMatches, commitNumber, targetBranch, null)
                logger.debug("eventHandlerMerge: merge, commitNumber: ${commitNumber}, needRun: ${needRun}")
            }
        }
        if (trigger.contains("OnUpdate")) {
            if (this.script.env.giteeActionType == "MERGE" && (jsonBody.action == "update" || jsonBody.action == "open")) {
                String targetBranch = "origin/${this.script.env.giteeTargetBranch}"
                needRun = eventHandlerMerge(fileMatches, null, targetBranch, script.env.giteePullRequestLastCommit as String)
                logger.debug("eventHandlerMerge: update, needRun: ${needRun}")
            }
        }

        if (trigger.contains("OnClose")) {
            if (this.script.env.giteeActionType == "MERGE" && jsonBody.action == "close") {
                needRun = true
                logger.debug("eventHandlerMerge: close, needRun: ${needRun}")
            }
        }
        if (trigger.contains("OnOpen")) {
            if (this.script.env.giteeActionType == "MERGE" && jsonBody.action == "open") {
                needRun = true
                logger.debug("eventHandlerMerge: open, needRun: ${needRun}")
            }
        }
        if (trigger.contains("OnApproved")) {
            if (this.script.env.giteeActionType == "MERGE" && jsonBody.action == "approved") {
                needRun = true
                logger.debug("eventHandlerMerge: approved, needRun: ${needRun}")
            }
        }
        if (trigger.contains("OnTestPass")) {
            if (this.script.env.giteeActionType == "MERGE" && jsonBody.action == "tested") {
                needRun = true
                logger.debug("eventHandlerMerge: tested, needRun: ${needRun}")
            }
        }
        if (trigger.contains("OnComment")) {
            if (script.env.forceActionType == "NOTE" || (this.script.env.giteeActionType == "NOTE" && jsonBody.action == "comment")) {
                String targetBranch = "origin/${this.script.env.giteeTargetBranch}"
                needRun = eventHandlerNote(trigger, stageName, groupName, noteMatches, failureStages, fileMatches, targetBranch, script.env.giteePullRequestLastCommit as String)
                logger.debug("eventHandlerNote: note, needRun: ${needRun}")
            }
        }
        if (trigger.contains("OnEnv")) {
            needRun = eventHandlerEnv(envMatches)
            logger.debug("eventHandlerEnv: env_match, needRun: ${needRun}")
        }
        if (trigger.contains("OnBuildPass") && ! error) {
            needRun = true
        }
        if (trigger.contains("OnBuildFailure") && error) {
            needRun = true
        }

        return needRun

    }
}
