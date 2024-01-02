package com.sd.lib.xlog

import android.os.Looper

interface FLogRecord {
    /** 日志标识 */
    val logger: Class<out FLogger>

    /** 日志标志 */
    val tag: String

    /** 日志内容 */
    val msg: String

    /** 日志等级 */
    val level: FLogLevel

    /** 日志生成的时间戳 */
    val millis: Long

    /** 日志是否在主线程生成 */
    val isMainThread: Boolean

    /** 日志生成的线程ID */
    val threadID: String
}

internal fun newLogRecord(
    logger: Class<out FLogger>,
    tag: String,
    msg: String,
    level: FLogLevel,
): FLogRecord {
    return LogRecordDefault(
        logger = logger,
        tag = tag,
        msg = msg,
        level = level,
        millis = System.currentTimeMillis(),
        isMainThread = Looper.getMainLooper() === Looper.myLooper(),
        threadID = Thread.currentThread().id.toString(),
    )
}

private data class LogRecordDefault(
    override val logger: Class<out FLogger>,
    override val tag: String,
    override val msg: String,
    override val level: FLogLevel,
    override val millis: Long,
    override val isMainThread: Boolean,
    override val threadID: String,
) : FLogRecord