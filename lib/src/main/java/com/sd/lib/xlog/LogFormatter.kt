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

    /** 上一次打印日志的tag */
    private var _lastLogTag = ""

    override fun format(record: FLogRecord): String {
        val logTime = _logTimeFactory.create(record.millis)
        return buildString {
            append(logTime.timeString)

            _list.clear()

            val finalTag = if (record.tag == _lastLogTag) "" else record.tag
            if (finalTag.isNotEmpty()) {
                _list.add(finalTag)
            }
            _lastLogTag = record.tag

            if (record.level != FLogLevel.Info) {
                _list.add(record.level.displayName())
            }

            if (!record.isMainThread) {
                _list.add(record.threadID)
            }

            if (_list.isNotEmpty()) {
                append(_list.joinToString(prefix = "[", postfix = "]", separator = "|"))
            }

            append(" ")
            append(record.msg)
            append("\n")
        }
    }
}

private fun FLogLevel.displayName(): String {
    return when (this) {
        FLogLevel.All -> "A"
        FLogLevel.Verbose -> "V"
        FLogLevel.Debug -> "D"
        FLogLevel.Info -> ""
        FLogLevel.Warning -> "W"
        FLogLevel.Error -> "E"
        FLogLevel.Off -> "O"
    }
}