@Library('manci')_
manci = ManCI(isCI = true, CIName = "ManCI")

projectDescription = "test"

params = [
        [name: "PRNumber", defaultValue: "", description: "pr number"],
        [name: "Branch", defaultValue: "develop", description: "repo branch"],
        [name: "NeedBuild", defaultValue: "true", description: "是否需要构建"],
        [name: "NeedTest", defaultValue: "true", description: "是否需要测试"],
        [name: "NeedDeploy", defaultValue: "true", description: "是否需要部署"],
        [name: "NeedRelease", defaultValue: "true", description: "是否需要发布"],
]

manci.setParams(params)
manci.setDescription(projectDescription)

node(){
    manci.mstage("build", [group: "group1"]){
        echo "hello world 1"
    }
    manci.mstage("deploy", [group: "group2"]){
        echo "hello world 2"
    }
    manci.mstage("test", [group: "group3"]){
        echo "hello world 3"
    }
    manci.mstage("release", [group: "group3"]){
        echo "hello world 3"
    }
    manci.run()
}