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

    override fun format(record: FLogRecord): String = with(record) {
        _list.clear()
        buildString {
            append(LogTime.create(millis).timeString)

            val logTag = if (tag == _lastLogTag) "" else tag
            if (logTag.isNotEmpty()) _list.add(logTag)
            _lastLogTag = tag

            _list.add(level.displayName())

            if (!isMainThread) {
                _list.add(threadID)
            }

            if (_list.isNotEmpty()) {
                append(_list.joinToString(prefix = "[", postfix = "]", separator = "|"))
            }

            append(" ")
            append(msg)
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