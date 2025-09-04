package com.sd.lib.xlog

import android.os.Looper

interface FLogRecord {
  /** 日志标识 */
  val logger: Class<out FLogger>

  /** 日志等级 */
  val level: FLogLevel

  /** 日志标志 */
  val tag: String

  /** 日志内容 */
  val msg: String

  /** 日志生成的时间戳 */
  val millis: Long

  /** 日志是否在主线程生成 */
  val isMainThread: Boolean

  /** 日志生成的线程ID */
  val threadID: String
}

internal fun newLogRecord(
  logger: Class<out FLogger>,
  level: FLogLevel,
  tag: String,
  msg: String,
): FLogRecord {
  return LogRecordImpl(
    logger = logger,
    level = level,
    tag = tag,
    msg = msg,
    millis = System.currentTimeMillis(),
    isMainThread = Looper.myLooper() === Looper.getMainLooper(),
    threadID = Thread.currentThread().id.toString(),
  )
}

private data class LogRecordImpl(
  override val logger: Class<out FLogger>,
  override val level: FLogLevel,
  override val tag: String,
  override val msg: String,
  override val millis: Long,
  override val isMainThread: Boolean,
  override val threadID: String,
) : FLogRecord