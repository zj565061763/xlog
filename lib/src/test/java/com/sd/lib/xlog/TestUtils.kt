package com.sd.lib.xlog

/** 测试日志记录的时间戳 */
internal const val RECORD_MILLIS = 1_700_000_000_000L

/** 测试用的日志记录，默认参数格式化之后约61字节 */
internal fun testLogRecord(
  tag: String = "T",
  level: FLogLevel = FLogLevel.Info,
  msg: String = "0123456789012345678901234567890123456789",
  millis: Long = RECORD_MILLIS,
  isMainThread: Boolean = false,
): FLogRecord = object : FLogRecord {
  override val logger: Class<out FLogger> = RecordLogger::class.java
  override val level: FLogLevel = level
  override val tag: String = tag
  override val msg: String = msg
  override val millis: Long = millis
  override val isMainThread: Boolean = isMainThread
  override val threadID: String = "1"
}

private interface RecordLogger : FLogger
