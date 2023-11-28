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
    private val _list = mutableListOf<String>()

    override fun format(record: FLogRecord): String {
        val logTime = _logTimeFactory.create(record.millis)
        return buildString {
            append(logTime.timeString)

            _list.clear()
            if (record.tag.isNotEmpty()) {
                _list.add(record.tag)
            }

            if (record.level != FLogLevel.Info) {
                _list.add(record.level.displayName())
            }

            if (!record.isMainThread) {
                _list.add(record.threadID)
            }

            if (_list.isNotEmpty()) {
                append(_list.joinToString(prefix = "[", postfix = "]"))
            }

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