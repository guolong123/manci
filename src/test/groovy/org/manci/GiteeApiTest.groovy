package org.manci
import org.manci.GiteeApi

import static org.junit.jupiter.api.Assertions.assertEquals

class GiteeApiTest {
    def api = new GiteeApi()
    def repoName = "xishuhq/ketaops"

    def getRepo() {
        def repo = api.getRepo(repoName)
        assertEquals(repo.name, "ketaops")
    }

    def getRepoBranches() {
        def branches = api.getRepoBranches(repoName)
        println(branches)
    }

    def getPullRequests() {
        def commits = api.getPullRequests(repoName)
        println(commits)
    }
}
