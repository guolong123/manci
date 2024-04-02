@Library('manci')_

manci = new ManCI(this)

ManCIProjectDescription = """
## 使用小窍门
* 可以点击状态图标进入构建日志界面
* 评论`rebuild all`可以运行所有步骤
* 评论`rebuild failure`可以运行所有失败的步骤
* 评论`rebuild`将以提交时默认行为进行构建
"""

ManCIParams = [
        [defaultValue: "false", description: '是否是 CI 模式，CI 模式将会与接受来自 git 平台的 webhook 事件，且会将构建结果推送到 git 平台', name: 'CI', type: 'boolean'],
        [defaultValue: "ManCI V1", description: 'CI的名称，显示在状态表格中', name: 'CIName', type: 'string'],
        [defaultValue: "", description: 'ketaops库pr号，多个pr使用空格分割', name: 'KETADB_PR', type: 'string'],
        [defaultValue: false, description: '是否编译KETADB前端项目', name: 'KETA_FRONTEND_BUILD', type: 'boolean'],
        [choices: ['linux-amd64', 'all', 'linux-arm64', 'linux-i386', 'windows-amd64', 'windows-i386', 'darwin-amd64', 'darwin-arm64', 'none'], name: 'PLATFORMS', description: 'keta-agent编译版本', type: 'choice'],
        [defaultValue: "ketabot-access-token", description: 'gitee平台用来调用 v5 api 的 token', name: 'GITEE_TOKEN', type: 'credentials', credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl', required: false],
]




manci.withRun(){
    // 同一个 group 下的 stage 会顺序执行，不同的 group 将会并发执行
    manci.stage("build", [group: "group1"]){
//        docker.image("alpine:3.12").inside {
//            echo "hello world 1"
//        }
        echo "stage 1"
    }
    manci.stage("deploy", [group: "group2"]){
        echo "hello world 2"
    }
    manci.stage("test", [group: "group3"]){
        echo "hello world 3"
    }
    manci.stage("release", [group: "group3"]){
        echo "hello world 3"
    }
}
