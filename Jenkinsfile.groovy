@Library('manci')_

manci = new ManCI(this, "debug")

// 定义参数，这些参数会显示在 jenkins 的参数化构建页面；同时也会显示到 CI 表格下方
manci.parameters = [
        [defaultValue: "ManCI V1", description: 'CI的名称，显示在状态表格中', name: 'CIName', type: 'string'],
        [choices: ['main', 'develop'], description: '选择要部署的分支', name: 'BRANCH_NAME', type: 'choice']
]

// 定义 ssh 密钥，用来拉取git仓库代码。此密钥必须在 Jenkins 的 Credentials 中存在，类型为 ssh username with private key
manci.SSH_SECRET_KEY = "3ee85ad2-4f01-40f3-930f-64fcd4f3fbfc"

// 定义 gitee 的 access token，用来访问 gitee 相关的 api 接口，此密钥必须在 Jenkins 的 Credentials 中存在，类型为 secret text
manci.GITEE_ACCESS_TOKEN_KEY = 'guolong-gitee-access-token'

PR_TITLE_CHECK_REX = /(\[)(feat|fix|build|docs|style|refactor|perf|test|revert|chore|upgrade|devops)((\(.+\))?)\](:)( )(.{1,50})([\s\S]*)$/

manci.withRun(){
    // 同一个 group 下的 stage 会顺序执行，不同的 group 将会并发执行
    manci.stage("init", [group: "before", trigger: ["pr_note", "pr_open"], mark: "[访问地址](#)" ]){
        manci.giteeApi.resetTest()
    }

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

    manci.stage("pr_note", [group: "group1", trigger: ["pr_note"], fileMatches: "'.*'", mark: "[访问地址](#)" ]){
        sh 'sleep 1'
    }
    manci.stage("pr_merge", [group: "group2", trigger: ["pr_merge", "pr_note"], fileMatches: "'Jenkinsfile.groovy'", mark: "[访问地址](#)"]){
        sh 'sleep 1'
    }
    manci.stage("pr_push", [group: "group3", trigger: ["pr_push", "pr_note"], fileMatches: "'.*'"]){
        sh 'exit 1'
    }
    manci.stage("env_match", [group: "group3", trigger: ["env_match"],envMatches: [role: "and", condition: ["BRANCH_NAME": "main"]]]){
        sh 'sleep 1'
    }
    manci.stage("pr_close", [group: "group3", trigger: ["pr_close"]]){
        sh 'sleep 1'
    }
    manci.stage("pr_tested", [group: "group1", trigger: ["pr_tested"]]){
        sh 'sleep 1'
    }
    manci.stage("pr_approved", [group: "group2", trigger: ["pr_approved"]]){
        sh 'sleep 1'
    }
    manci.stage("pr_open", [group: "group3", trigger: ["pr_open"]]){
        sh 'sleep 1'
    }
    manci.stage("always", [group: "group4", trigger: ["always"]]){
        sh 'sleep 1'
        echo "always 组中的 stage 总是执行"
    }
    manci.stage("on-pass", [group: "after", trigger: ["onPass"], fastFail: false]){
        manci.giteeApi.testPass()
    }
    manci.stage("on-failure", [group: "after", trigger: ["onFailure"], fastFail: false]){
        echo "run failure"
    }
}
