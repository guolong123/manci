@Library('manci')_

manci = new ManCI(this, "debug", "ManCI V1", "rebuild")
notify = new Notify(this, "qyweixin", "2d5f4257-9b32-4211-93ec-4d266cd83bbb")

// 定义参数，这些参数会显示在 jenkins 的参数化构建页面；同时也会显示到 CI 表格下方
manci.parameters = [
        [defaultValue: "ManCI V1", description: 'CI的名称，显示在状态表格中', name: 'CIName', type: 'string'],
        [choices: ['main', 'develop'], description: '选择要部署的分支', name: 'BRANCH_NAME', type: 'choice'],
        [defaultValue: true, description: '退出状态码(stage 测试用)', name: 'TEST_BOOLEAN', type: 'boolean'],
        [type: 'text', description: '自定义测试用', name: 'TEST_TEXT', defaultValue: ''],
        [type: 'file', description: '自定义测试用', name: 'TEST_FILE', defaultValue: ''],
        [type: 'credentials', description: '自定义测试用', name: 'TEST_CREDENTIALS', credentialType:'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl', defaultValue: '', required: false],
        [type: 'password', description: '自定义测试用', name: 'TEST_PASSWORD', defaultValue: ''],
        [defaultValue: "${env.giteeUserName}", description: 'PR 提交者', name: 'QW_WEBHOOK_USER', type: 'string'],
]

// 定义 ssh 密钥，用来拉取git仓库代码。此密钥必须在 Jenkins 的 Credentials 中存在，类型为 ssh username with private key
manci.SSH_SECRET_KEY = "3ee85ad2-4f01-40f3-930f-64fcd4f3fbfc"

// 定义 gitee 的 access token，用来访问 gitee 相关的 api 接口，此密钥必须在 Jenkins 的 Credentials 中存在，类型为 secret text
manci.GITEE_ACCESS_TOKEN_KEY = 'guolong-gitee-access-token'

manci.failFast = true  // 是否失败即停止

PR_TITLE_CHECK_REX = /(\[)(feat|fix|build|docs|style|refactor|perf|test|revert|chore|upgrade|devops)((\(.+\))?)\](:)( )(.{1,50})([\s\S]*)$/

manci.withRun(){

    manci.stage("check-pr-title", [group: "before", trigger: ["pr_note", "pr_open"], mark: "[访问地址](#)" ]){
        def matcher = (env.giteePullRequestTitle ==~ PR_TITLE_CHECK_REX)
        if (matcher != true){
            throw new Exception("PR提交不规范, 内容: ${giteePullRequestTitle}")
        }else {
            echo "PR提交符合规范"
        }
    }

    manci.stage("check-commit", [group: "before", trigger: ["pr_note", "pr_open"], mark: "[访问地址](#)" ]){
        commits = sh(script:"git log --left-right --format=%s origin/${env.giteeTargetBranch}...${env.ref}",returnStdout: true)
        echo "commits: ${commits}"
        def matcher = (env.giteePullRequestTitle ==~ PR_TITLE_CHECK_REX)
        if (matcher != true){
            throw new Exception("commit message 提交不规范, 内容: ${commits}")
        } else{
            echo "commit message 符合规范"
        }
    }

    manci.stage("pr_note", [group: "group1", trigger: ["OnComment"], fileMatches: "'.*'", mark: "[访问地址](#)" ]){
        sh 'sleep 1'
    }
    manci.stage("pr_merge", [group: "group2", trigger: ["OnMerge", "OnComment"], fileMatches: "'Jenkinsfile.groovy'", mark: "[访问地址](#)"]){
        sh 'sleep 1'
    }
    manci.stage("pr_update", [group: "group3", trigger: ["OnUpdate", "OnComment"], fileMatches: "'.*'"]){
        echo "Pr 更新时触发"
        if (env.TEST_BOOLEAN == "false"){
            sh "exit 1"
        }
    }
    manci.stage("push", [group: "group3", trigger: ["OnPush", "OnComment"], fileMatches: "'.*'"]){
        echo "代码推送时触发"
        if (env.TEST_BOOLEAN == "false"){
            sh "exit 1"
        }
    }
    manci.stage("env_match", [group: "group3", trigger: ["OnEnv"],envMatches: [role: "and", condition: ["BRANCH_NAME": "main"]]]){
        sh 'sleep 1'
    }
    manci.stage("pr_close", [group: "group3", trigger: ["OnClose"]]){
        sh 'sleep 1'
    }
    manci.stage("pr_tested", [group: "group1", trigger: ["OnTestPass"]]){
        sh 'sleep 1'
    }
    manci.stage("OnApproved", [group: "group2", trigger: ["OnApproved"]]){
        sh 'sleep 1'
    }
    manci.stage("pr_open", [group: "group3", trigger: ["OnOpen"]]){
        sh 'sleep 1'
    }
    manci.stage("always", [group: "group4", trigger: ["Always"]]){
        sh 'sleep 1'
        echo "always 组中的 stage 总是执行"
    }
    manci.stage("on-pass", [group: "after", trigger: ["OnBuildPass"], fastFail: false]){
        // 当阶段执行成功时执行此阶段，此处设置测试状态为通过；
        // 注意⚠️：当你同时设置了测试通过事件来触发 CI 时，可能会与此处逻辑发生死循环问题。具体流程是：
        // 1. 当 CI 执行成功时，会使 PR 测试状态通过
        // 2. 测试通过时会触发 CI ，此时会再次触发 PR 测试状态通过，从而导致死循环，建议 这两者不要同时使用。
        // 关闭执行测试通过事件需要去 Jenkins Job 设置中关闭
        if (manci.isCI){
            manci.giteeApi.testPass()
        }
        notify.sendMessage("构建耗时: ${currentBuild.durationString}", "info",
                "[PR]: ${env.giteePullRequestTitle} 检查成功",
                "[查看控制台](${BUILD_URL})", "${env.QW_WEBHOOK_USER}")

    }
    manci.stage("on-failure", [group: "after", trigger: ["OnBuildFailure"], fastFail: false]){
        // 当阶段执行失败时执行此阶段，可以用来做通知告警等
        echo "run failure"
        notify.sendMessage("构建耗时: ${currentBuild.durationString}", "warning",
                "[PR]: ${env.giteePullRequestTitle} 检查失败",
                "[查看控制台](${BUILD_URL})", "${env.QW_WEBHOOK_USER}")
    }
}
