package com.sd.lib.xlog

/**
 * 日志格式化
 */
interface FLogFormatter {
  fun format(record: FLogRecord): String
}

internal fun defaultLogFormatter(): FLogFormatter = LogFormatterImpl()

private class LogFormatterImpl : FLogFormatter, AutoCloseable {
  /** 上一次打印日志的tag */
  private var _lastLogTag = ""

  override fun format(record: FLogRecord): String = with(record) {
    buildString {
      append(LogTime.create(millis).timeString)
      append("[")

      val logTag = if (tag == _lastLogTag) "" else tag
      _lastLogTag = tag

      if (logTag.isNotEmpty()) append(logTag).append("|")
      append(level.displayName())
      if (!isMainThread) append("|").append(threadID)

      append("]")
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