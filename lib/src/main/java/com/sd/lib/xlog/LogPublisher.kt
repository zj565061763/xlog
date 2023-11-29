package com.sd.lib.xlog

import android.os.Looper
import android.os.MessageQueue.IdleHandler
import java.io.File

internal interface LogPublisher : AutoCloseable {
    /**
     * 发布日志记录
     */
    fun publish(record: FLogRecord)

    /**
     * 关闭
     */
    override fun close()
}

internal interface DirectoryLogPublisher : LogPublisher {
    /** 日志文件目录 */
    val directory: File

    /** 限制每天日志文件大小(单位B)，小于等于0表示不限制大小 */
    val limitPerDay: Long

    /** 日志格式化 */
    val formatter: FLogFormatter

    /** 日志仓库工厂 */
    val storeFactory: FLogStore.Factory

    /** 日志文件名 */
    val filename: LogFilename
}

internal fun defaultPublisher(
    /** 日志文件目录 */
    directory: File,

    /** 限制每天日志文件大小(单位B)，小于等于0表示不限制大小 */
    limitPerDay: Long,

    /** 日志格式化 */
    formatter: FLogFormatter,

    /** 日志仓库工厂 */
    storeFactory: FLogStore.Factory,

    /** 日志文件名 */
    filename: LogFilename,
): DirectoryLogPublisher {
    return LogPublisherImpl(
        directory = directory,
        limitPerDay = limitPerDay,
        formatter = formatter,
        storeFactory = storeFactory,
        filename = filename,
    )
}

private class LogPublisherImpl(
    override val directory: File,
    override val limitPerDay: Long,
    override val formatter: FLogFormatter,
    override val storeFactory: FLogStore.Factory,
    override val filename: LogFilename,
) : DirectoryLogPublisher {

    private data class DateInfo(
        val date: String,
        val file: File,
        val store: FLogStore,
    )

    private var _dateInfo: DateInfo? = null
    private val _logFileChecker = SafeIdleHandler {
        checkLogFileExist()
        false
    }

    override fun publish(record: FLogRecord) {
        val log = formatter.format(record)
        val dateInfo = getDateInfo(record)

        // 保存日志
        dateInfo.store.append(log)

        // 检查日志大小
        checkLimit(dateInfo)

        // 检查日志文件是否存在
        if (_logFileChecker.register()) {
            // 任务提交成功
        } else {
            checkLogFileExist()
        }
    }

    override fun close() {
        _dateInfo?.let {
            it.store.close()
            _dateInfo = null
            fDebug { "lib publisher close date info ${it.date} ${this@LogPublisherImpl}" }
        }
    }

    private fun getDateInfo(record: FLogRecord): DateInfo {
        val date = filename.filenameOf(record.millis).also { check(it.isNotEmpty()) }
        val dateInfo = _dateInfo
        if (dateInfo == null || dateInfo.date != date) {
            close()
            val file = directory.resolve("${date}.log")
            _dateInfo = DateInfo(
                date = date,
                file = file,
                store = storeFactory.create(file).safeStore(),
            )
            fDebug { "lib publisher create date info $date ${this@LogPublisherImpl}" }
        }
        return checkNotNull(_dateInfo)
    }

    /**
     * 检查日志大小
     */
    private fun checkLimit(dateInfo: DateInfo) {
        if (limitPerDay <= 0) {
            // 不限制大小
            return
        }
        if (dateInfo.store.size() > (limitPerDay / 2)) {
            dateInfo.store.close()
            val file = dateInfo.file
            val oldFile = file.resolveSibling("${file.name}.old")
            if (file.renameTo(oldFile)) {
                fDebug { "lib publisher log file rename success ${this@LogPublisherImpl}" }
            } else {
                fDebug { "lib publisher log file rename failed ${this@LogPublisherImpl}" }
            }
        }
    }

    /**
     * 检查日志文件是否存在
     */
    private fun checkLogFileExist() {
        synchronized(FLog) {
            _dateInfo?.let { info ->
                if (info.file.isFile) {
                    // 文件存在
                } else {
                    // 文件不存在，关闭后会重新创建
                    info.store.close()
                }
            }
        }
    }
}

private class SafeIdleHandler(private val block: () -> Boolean) {
    private var _idleHandler: IdleHandler? = null

    fun register(): Boolean {
        Looper.myLooper() ?: return false
        synchronized(this@SafeIdleHandler) {
            _idleHandler?.let { return true }
            IdleHandler {
                block().also { sticky ->
                    synchronized(this@SafeIdleHandler) {
                        if (!sticky) {
                            _idleHandler = null
                        }
                    }
                }
            }.also {
                _idleHandler = it
                Looper.myQueue().addIdleHandler(it)
            }
            return true
        }
    }
}