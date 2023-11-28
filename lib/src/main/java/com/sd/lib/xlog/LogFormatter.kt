package com.sd.lib.xlog

/**
 * 日志格式化
 */
interface FLogFormatter {
    /**
     * 格式化
     */
    fun format(record: FLogRecord): String
}

/**
 * 默认的日志格式化
 */
internal class LogFormatterDefault : FLogFormatter {
    private val _logTimeFactory = LogTimeFactory()

    override fun format(record: FLogRecord): String {
        val logTime = _logTimeFactory.create(record.millis)
        return buildString {
            append(logTime.timeString)

            append("[")
            append(record.tag)

            if (record.level != FLogLevel.Info) {
                append(",")
                append(record.level.displayName())
            }

            if (!record.isMainThread) {
                append(",")
                append(record.threadID.toString())
            }

            append("]")
            append(" ")
            append(record.msg)
            append("\n")
        }
    }
}

private fun FLogLevel.displayName(): String {
    return when (this) {
        FLogLevel.Debug -> "D"
        FLogLevel.Info -> ""
        FLogLevel.Warning -> "W"
        FLogLevel.Error -> "E"
        FLogLevel.Off -> "O"
    }
}