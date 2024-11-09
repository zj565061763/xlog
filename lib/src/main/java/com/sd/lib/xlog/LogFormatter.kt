package com.sd.lib.xlog

/**
 * 日志格式化
 */
interface FLogFormatter {
    fun format(record: FLogRecord): String
}

internal fun defaultLogFormatter(): FLogFormatter = LogFormatterImpl()

private class LogFormatterImpl : FLogFormatter, AutoCloseable {
    private val _list = mutableListOf<String>()

    /** 上一次打印日志的tag */
    private var _lastLogTag = ""

    override fun format(record: FLogRecord): String {
        val logTime = LogTime.create(record.millis)
        return buildString {
            append(logTime.timeString)

            _list.clear()

            val finalTag = if (record.tag == _lastLogTag) "" else record.tag
            if (finalTag.isNotEmpty()) {
                _list.add(finalTag)
            }
            _lastLogTag = record.tag

            _list.add(record.level.displayName())

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

    override fun close() {
        _lastLogTag = ""
    }
}

private fun FLogLevel.displayName(): String {
    return when (this) {
        FLogLevel.Verbose -> "V"
        FLogLevel.Debug -> "D"
        FLogLevel.Info -> "I"
        FLogLevel.Warning -> "W"
        FLogLevel.Error -> "E"
        else -> toString()
    }
}