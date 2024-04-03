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
    manci.stage("build", [group: "group1"]){
        echo "stage 1"
        sh "git branch"
    }
    manci.stage("deploy", [group: "group2"]){
        echo "hello world 2"
    }
    manci.stage("test", [group: "group3"]){
        echo "hello world 3"
    }
    manci.stage("release", [group: "group3"]){
        echo "exit 1"
    }
}
