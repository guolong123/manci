package org.manci

import java.text.SimpleDateFormat
import groovy.json.JsonSlurperClassic

class Utils implements Serializable {
    def script
    Logger logger

    Utils(script) {
        this.script = script
        logger = new Logger(script)
    }

    @NonCPS
    static getNowTime() {
        def now = new Date()
        def formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return formatter.format(now)
    }

    @NonCPS
    static rexContains(list, str) {
        for (l in list) {
            if (str.matches(l)) {
                return true
            }
        }
        return false
    }

    @NonCPS
    static Map<String, Object> commandParse(String command) {
        /* 这个方法将一段命令行语句解析出来
        例如：
        input: "rebuild stage-test KETADB_PR=1234"
        return {"flag": "rebuild", "args": ["stage-test"], "kwargs": {"KETADB_PR": "1234"}}
        */
        def parts = command.replaceAll(/"([^"]*)"/) { match ->
            match.get(1).replaceAll(" ", "<<>>")
        }.split()

        String flag = parts[0]
        List<String> args = []
        Map<String, String> kwargs = [:]
        parts = parts.drop(1)

        parts.each { part ->
            if (part.contains('=')) {
                def keyValue = part.split('=', 2)
                kwargs[keyValue[0]] = keyValue[1].replaceAll("<<>>", " ")
            } else {
                args.add(part.replaceAll("<<>>", " "))
            }
        }

        return [flag: flag, args: args, kwargs: kwargs]
    }

    @NonCPS
    LinkedHashMap<String, Object> jsonParse(String json) {
        def parsedJson = new groovy.json.JsonSlurperClassic().parseText(json)
        if (parsedJson instanceof groovy.json.internal.LazyMap) {
            return new LinkedHashMap<>(parsedJson as Map<? extends String, ?>)
        }
        def linkedJson = new LinkedHashMap<>(parsedJson)
        return linkedJson as LinkedHashMap<String, Object>
    }

    @NonCPS
    static String timestampConvert(long timestamp) {
        Integer minutes = 0
        Integer seconds = 0


        if (timestamp >= 60000) { // 1 minute in milliseconds
            minutes = (int) (timestamp / 60000)
            timestamp %= 60000
        }

        if (timestamp >= 1000) { // 1 second in milliseconds
            seconds = (int) (timestamp / 1000)
        }

        // Format the result string with leading zeros for consistency
        String formattedMinutes = minutes.toString().padLeft(1, '0')
        String formattedSeconds = seconds.toString().padLeft(1, '0')

        return "${formattedMinutes}m${formattedSeconds}s" as String
    }

    @NonCPS
    static long reverseTimestampConvert(String formattedTimestamp) {
        def parts = formattedTimestamp =~ /\d+/ // 使用正则表达式匹配数字序列

        // 确保输入格式正确，包含至少小时、分钟和秒
        if (parts.size() != 2) {
            throw new IllegalArgumentException("Invalid formatted timestamp: ${formattedTimestamp}")
        }

        int minutes = parts[0].toInteger()
        int seconds = parts[1].toInteger()

        // 计算总毫秒数
        long totalMilliseconds = minutes * 60000 + seconds * 1000

        return totalMilliseconds
    }
}
