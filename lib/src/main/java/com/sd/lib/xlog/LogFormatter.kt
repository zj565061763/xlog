package com.sd.lib.xlog

/**
 * 日志格式化
 */
interface FLogFormatter {
  fun format(record: FLogRecord): String
}

internal fun defaultLogFormatter(): FLogFormatter = LogFormatterImpl()

private class LogFormatterImpl : FLogFormatter {
  override fun format(record: FLogRecord): String = with(record) {
    buildString {
      append(LogTime.create(millis).timeString)
      append("[")
      append(level.displayName())
      if (!isMainThread) append("|").append(threadID)
      append("]")
      append(" ")
      append(msg)
      append("\n")
    }
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