package org.manci


class Table {
    String tableTag = "MANCI V1"
    def tableHeader = ["检查项", "分组", "检查状态", "执行耗时", "执行次数", "最后一次执行时间", "触发策略", "备注"]
    public String text = ""
    def commentBody = ""
    def commentInfo = ""

    public static final WAITING_LABEL = ":tw-23f3: waitting"
    public static final RUNNING_LABEL = ":tw-1f552: running"
    public static final SUCCESS_LABEL = ":white_check_mark: success"
    public static final FAILURE_LABEL = ":x: failure"
    public static final NOT_NEED_TUN_LABEL = ":white_large_square: skip"
    public static final ABORTED_LABEL = ":heavy_exclamation_mark: aborted"

    Map<String, List<String>> table

    Table(String tableTag = "", String commentBody = "", String commentInfo = "", List<String> stageList = []) {
        if (tableTag) {
            this.tableTag = tableTag
        }
        this.commentBody = commentBody
        this.commentInfo = commentInfo
        if (commentInfo) {
            this.commentInfo = commentInfo
        }
        if (this.commentBody) {
            // this.log.debug("commentBody: ${commentBody}")
            this.table = tableParse(this.commentBody)
            this.text = tableCreate(tableHeader, commentInfo, this.table)
        } else {
            // this.log.debug("stageList: ${stageList}")
            def columnList = []
            stageList.each { col ->
                columnList.add([col, "", WAITING_LABEL, "", "", "0", "", ""])
            }
            this.table = ["header": this.tableHeader, "columns": columnList]
            this.text = tableCreate(tableTag, commentInfo, this.table)
        }
    }



    def addColumns(List<List<String>> columnList) {
        columnList.eachWithIndex { row, rowIndex ->
            this.table.columns.eachWithIndex { t, tIndex ->
                if (row[0] == t[0]) {
                    if ((row[1].contains(NOT_NEED_TUN_LABEL) || row[1].contains(WAITING_LABEL)) && !t[1].contains(WAITING_LABEL)) {
                        // this.log.debug("stage ${row[0]} status does not need to be updated")
                        return
                    } else {
                        this.table.columns[tIndex] = row
                    }
                }
            }
        }
        this.text = tableCreate(tableTag, commentInfo, this.table)
    }

    def getStageRunTotal(String stageName) {
        def runTotal = "0"
        def num = 0
        this.table.columns.each { t ->
            if (t[0] == stageName) {
                runTotal = t[4]
            }
        }
        try {
            num = runTotal.toInteger()

        } catch (Exception e) {
            println "无法将字符串转换为数字：$e.message"
        }
        return num
    }

    def getFailureStages() {
        def failureStages = []
        this.table.columns.each { t ->
            if (t[1].contains(FAILURE_LABEL) || t[1].contains(ABORTED_LABEL)) {
                failureStages.add(t[0])
            }
        }
        return failureStages
    }

    @NonCPS
    static tableParse(String text) {
        /* 该方法解析表格为格式化数据
           例如：
           """
           # Gitee CI For Xishu

           | 检查项 | 检查状态 | 相关地址 | 执行耗时 |
           |--------|-------|-------|-------|
           | test-static-check | :x:unsuccessful | [构建地址](http://jenkins.ketaops.cc/job/ketaops-ci-ketadb/2220/display/redirect) | 0min42s |
           | test-backend-unittest | :x:unsuccessful | [单测报告](http://jenkins.ketaops.cc/job/ketaops-ci-ketadb/2225/display/redirect?page=tests) | 2min1s |

           ## 使用小窍门\n* 评论`deploy [dbtype]`可以指定部署时使用的数据库类型，默认`h2`，可选`mysql`和`dm`\n* 评论`active`可以将已休眠的环境激活，激活相对于重新部署会快很多，时间在2分钟以内\n* 评论`rebuild <stage name> [stage name] ...`可以运行多个构建步骤, `rebuild all`可以运行所有步骤\n* 评论`rebuild integration`可以运行编译、部署和集测步骤\n
           """
           将上述表格解析为:
           {
            "header": ["检查项", "检查状态", "相关地址", "执行耗时"],
            "column": [
                ["test-static-check", ":x:unsuccessful", "[构建地址](http://jenkins.ketaops.cc/job/ketaops-ci-ketadb/2220/display/redirect)", "0min42s"],
                ["test-backend-unittest", ":x:unsuccessful", "[单测报告](http://jenkins.ketaops.cc/job/ketaops-ci-ketadb/2225/display/redirect?page=tests)", "2min1s"],
            ]
           }
        */

        def tableData = text.split('\n\n')[1]  // 获取表格数据部分
        def tableLines = tableData.split('\n')

        // 提取表头
        def headerLine = tableLines[0]
        def header = headerLine.split(' \\| ')[1..-1].collect { it.trim() }
        // 提取表格数据
        def rows = tableLines[2..-1].collect { it.split(' \\| ')[1..-1].collect { it.trim().replace("\\", "\\\\") } }

        return [header: header, columns: rows] as Map<String, List<String>>

    }

    @NonCPS
    static tableCreate(tableTag, commentInfo, Map<String, Object> table) {
        /*
        该方法将接收一个map，转换为markdown格式的表格字符串，map结构如下：
        {
            "header": ["检查项", "检查状态", "相关地址", "执行耗时"],
            "columns": [
                ["test-static-check", ":x:unsuccessful", "[构建地址](http://jenkins.ketaops.cc/job/ketaops-ci-ketadb/2220/display/redirect)", "0min42s"],
                ["test-backend-unittest", ":x:unsuccessful", "[单测报告](http://jenkins.ketaops.cc/job/ketaops-ci-ketadb/2225/display/redirect?page=tests)", "2min1s"],
            ]
        }

        转换为
        | 检查项 | 检查状态 | 相关地址 | 执行耗时 |
        |--------|-------|-------|-------|
        | test-static-check | :x:unsuccessful | [构建地址](http://jenkins.ketaops.cc/job/ketaops-ci-ketadb/2220/display/redirect) | 0min42s |
        | test-backend-unittest | :x:unsuccessful | [单测报告](http://jenkins.ketaops.cc/job/ketaops-ci-ketadb/2225/display/redirect?page=tests) | 2min1s |
        */
        def header = table.get("header")
        def columns = table.get("columns")
        def tableStr = " | " + header.join(" | ") + " | \n | " + header.collect { "---" }.join(" | ") + " | \n"
        columns.each { row ->
            tableStr += " | " + row.join(" | ") + " | \n"
        }
        def text = "# " + tableTag + "\n\n" + tableStr + "\n\n" + commentInfo
        text = text.replace("\"", "\\\"")
        return text
    }
}