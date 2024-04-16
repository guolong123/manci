@Library('manci') _
import org.manci.WarningException

manci = new ManCI(this, "debug", "ManCI V1", "rebuild")
tools = new Tools(this)
//notify = new Notify(this, "qyweixin", "2d5f4257-9b32-4211-93ec-4d266cd83bbb")


// 定义参数，这些参数会显示在 jenkins 的参数化构建页面；同时也会显示到 CI 表格下方
manci.parameters = [
        [defaultValue: "ManCI V1", description: 'CI的名称，显示在状态表格中', name: 'CIName', type: 'string'],
        [choices: ['main', 'develop'], description: '选择要部署的分支', name: 'BRANCH_NAME', type: 'choice'],
        [defaultValue: true, description: '退出状态码(stage 测试用)', name: 'TEST_BOOLEAN', type: 'boolean'],
        [type: 'text', description: '自定义测试用', name: 'TEST_TEXT', defaultValue: ''],
        [type: 'file', description: '自定义测试用', name: 'TEST_FILE'],
        [type: 'credentials', description: '自定义测试用', name: 'TEST_CREDENTIALS', credentialType:'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl', defaultValue: '', required: false],
        [type: 'password', description: '自定义测试用', name: 'TEST_PASSWORD'],
        [defaultValue: "${env.giteeUserName}", description: 'PR 提交者', name: 'QW_WEBHOOK_USER', type: 'string'],
]

// 定义 ssh 密钥，用来拉取git仓库代码。此密钥必须在 Jenkins 的 Credentials 中存在，类型为 ssh username with private key
manci.SSH_SECRET_KEY = "3ee85ad2-4f01-40f3-930f-64fcd4f3fbfc"

// 定义 gitee 的 access token，用来访问 gitee 相关的 api 接口，此密钥必须在 Jenkins 的 Credentials 中存在，类型为 secret text
manci.GITEE_ACCESS_TOKEN_KEY = 'guolong-gitee-access-token'

manci.failFast = true  // 是否失败即停止

manci.allStageOnComment = true // 所有 stage 都支持评论触发，触发指令为 rebuild <stageName>

manci.withRun() {

    manci.stage("check-pr-title", [group: "before", trigger: ["OnComment", "OnUpdate"], mark: "[访问地址](#)"]) {
        tools.checkPrTitle(env.giteePullRequestTitle as String)
    }

    manci.stage("check-commit", [group: "before", trigger: ["OnComment", "OnUpdate"], mark: "[访问地址](#)"]) {
        String commitStr = sh(script: "git log --left-right --format=%s origin/${env.giteeTargetBranch}...${env.ref}", returnStdout: true)
        List<String> commits = commitStr.split("\n")
        tools.checkPrCommits(commits.subList(1, commits.size()))
    }

    manci.stage("pr_note", [group: "group1", trigger: ["OnComment", "OnManual"], mark: "[访问地址](#)"]) {
        sh 'sleep 1'
    }
    manci.stage("pr_merge", [group: "group2", trigger: ["OnMerge", "OnComment", "OnManual"], fileMatches: "'Jenkinsfile.groovy'", mark: "[访问地址](#)"]) {
        sh 'sleep 1'
    }
    manci.stage("pr_update", [group: "group3", trigger: ["OnUpdate", "OnComment", "OnManual"], fileMatches: "'.*'"]) {
        echo "Pr 更新时触发"
        if (env.TEST_BOOLEAN == "false") {
            sh "exit 1"
        }
        throw new WarningException("警告信息，将不会导致失败")
    }
    manci.stage("push", [group: "group3", trigger: ["OnPush", "OnComment", "OnManual"], fileMatches: "'.*'"]) {
        echo "代码推送时触发"
    }
    manci.stage("env_match", [group: "group3", trigger: ["OnEnv", "OnManual"], envMatches: [role: "and", condition: ["BRANCH_NAME": "main"]]]) {
            sh 'sleep 1'
    }
    manci.stage("pr_close", [group: "group3", trigger: ["OnClose"]]) {
        sh 'sleep 1'
    }
    manci.stage("pr_tested", [group: "group1", trigger: ["OnTestPass"]]) {
        sh 'sleep 1'
    }
    manci.stage("OnApproved", [group: "group2", trigger: ["OnApproved"]]) {
        sh 'sleep 1'
    }
    manci.stage("pr_open", [group: "group3", trigger: ["OnOpen"]]) {
        sh 'sleep 1'
    }
    manci.stage("always", [group: "group3", trigger: ["Always"]]) {
        echo "always 组中的 stage 总是执行1"

        echo "always 组中的 stage 总是执行2"
    }
    manci.stage("on-pass", [group: "after", trigger: ["OnBuildPass"], fastFail: false]) {
        // 当阶段执行成功时执行此阶段，此处设置测试状态为通过；
        // 注意⚠️：当你同时设置了测试通过事件来触发 CI 时，可能会与此处逻辑发生死循环问题。具体流程是：
        // 1. 当 CI 执行成功时，会使 PR 测试状态通过
        // 2. 测试通过时会触发 CI ，此时会再次触发 PR 测试状态通过，从而导致死循环，建议 这两者不要同时使用。
        // 关闭执行测试通过事件需要去 Jenkins Job 设置中关闭
        if (manci.jobTriggerType == "pullRequest") {
            manci.giteeApi.testPass()
//            notify.sendSuccessMessage()
        }

    }
    manci.stage("on-failure", [group: "after", trigger: ["OnBuildFailure"], fastFail: false]) {
        // 当阶段执行失败时执行此阶段，可以用来做通知告警等
        echo "run failure"
//        if (manci.jobTriggerType == "pullRequest") {
//            notify.sendFailureMessage()
//        }
    }
}

