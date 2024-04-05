package org.manci

import java.text.SimpleDateFormat

class Utils {
    def script

    Utils(script) {
        this.script = script
    }

    static getNowTime() {
        def now = new Date()
        def formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return formatter.format(now)
    }

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

        return "${formattedMinutes}min${formattedSeconds}s" as String
    }

    static long reverseTimestampConvert(String formattedTimestamp) {
        def parts = formattedTimestamp =~ /\d+/ // 使用正则表达式匹配数字序列

        // 确保输入格式正确，包含至少小时、分钟和秒
        if (parts.size() != 2) {
            throw new IllegalArgumentException("Invalid formatted timestamp: ${formattedTimestamp}")
        }

        int minutes = parts[0].toInteger()
        int seconds = parts[1].toInteger()

        // 计算总毫秒数
        long totalMilliseconds =  minutes * 60000 + seconds * 1000

        return totalMilliseconds
    }
}
