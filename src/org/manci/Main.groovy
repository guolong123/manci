package org.manci

static void main(String[] args) {
    println "Hello world!"
    // 使用示例
    def gitee = new GiteeApi()
    def resp = gitee.getPullRequest("xishuhq/ketaops", "1")
    println(resp)
}