@Library('manci')_

manci = new ManCI(this)

manci.projectDescription = """
## 使用小窍门
* 可以点击状态图标进入构建日志界面
* 评论`rebuild all`可以运行所有步骤
* 评论`rebuild failure`可以运行所有失败的步骤
* 评论`rebuild`将以提交时默认行为进行构建
"""

manci.parameters = [
        [defaultValue: "ManCI V1", description: 'CI的名称，显示在状态表格中', name: 'CIName', type: 'string'],
        [choices: ['main', 'develop'], description: '选择要部署的分支', name: 'BRANCH_NAME', type: 'choice']
]

manci.SSH_SECRET_KEY = "3ee85ad2-4f01-40f3-930f-64fcd4f3fbfc"

manci.GITEE_ACCESS_TOKEN_KEY = 'guolong-gitee-access-token'

manci.DEBUG = "true"

manci.withRun(){
    // 同一个 group 下的 stage 会顺序执行，不同的 group 将会并发执行
    manci.stage("pr_note", [group: "group1", trigger: ["pr_note"], fileMatches: "'.*'" ]){
        echo "stage 1"
        sh "sleep 1"
    }
    manci.stage("pr_merge", [group: "group2", trigger: ["pr_merge", "pr_note"], fileMatches: "'Jenkinsfile.groovy'"]){
        echo "hello world 2"
        sh 'sleep 3'
    }
    manci.stage("pr_push", [group: "group3", trigger: ["pr_push", "pr_note"], fileMatches: "'.*'"]){
        sh "sleep 1"
    }
    manci.stage("env_match", [group: "group3", trigger: ["env_match"],envMatches: ["BRANCH_NAME": "main"]]){
        sh "sleep 1"
    }
    manci.stage("pr_close", [group: "group3", trigger: ["pr_close"]]){
        sh "sleep 1"
    }
}
