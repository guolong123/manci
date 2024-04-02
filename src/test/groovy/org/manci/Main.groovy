package org.manci




static void main(String[] args) {
//    println "Hello world!"
//    GiteeApiTest test = new GiteeApiTest()
//    test.getRepo()
//    test.getRepoBranches()
//    test.getPullRequests()
    testTable()
}


def testTable() {
    def table = new Table("CIName", "", "", stageNames)
    table.addColumn([["test1", table.SUCCESS_LABEL, "1min", "1", "2023-10-11 19:31:02", "", "备注"],
                     ["test2", table.SUCCESS_LABEL, "1min", "1", "2023-10-11 19:31:02", "", "备注"],
                     ["test3", table.FAILURE_LABEL, "1min", "1", "2023-10-11 19:31:02", "", "备注"],])

    def mkd = table.tableCreate()

    def tab = table.tableParse(mkd)
    println(mkd)
    println(tab)
}