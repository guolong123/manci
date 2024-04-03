package org.manci

static void main(String[] args) {
    println "Hello world!"
    // 使用示例
    def gitee = new GiteeApi(null, "a7145574cb3688f44dde322cdb50a68e", "guojongg/manci", "3", "ManCI")
    def resp = gitee.comment("test-c")
    println(resp)
}