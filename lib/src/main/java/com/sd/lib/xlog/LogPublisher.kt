package com.sd.lib.xlog

import android.os.Looper
import android.os.MessageQueue.IdleHandler
import java.io.File

internal interface LogPublisher {
    /**
     * 发布日志记录
     */
    fun publish(record: FLogRecord)
}

internal interface DirectoryLogPublisher : LogPublisher {
    val formatter: FLogFormatter
    val filename: LogFilename
    val storeFactory: FLogStore.Factory
    fun close()
}

internal abstract class AbstractLogPublisher(
    /** 日志文件目录 */
    directory: File,

    /** 限制每天日志文件大小(单位MB)，小于等于0表示不限制大小 */
    limitMBPerDay: Long,
) : DirectoryLogPublisher {

    private data class DateInfo(
        val date: String,
        val file: File,
        val store: FLogStore,
    )

    private val _directory = directory
    private val _limit = limitMBPerDay * 1024 * 1024

    private var _dateInfo: DateInfo? = null
    private val _logFileChecker = LogFileChecker { checkLogFileExist() }

    final override fun publish(record: FLogRecord) {
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
            fDebug { "lib check log file post failed" }
            checkLogFileExist()
        }
    }

    final override fun close() {
        _dateInfo?.store?.close()
        _dateInfo = null
    }

    private fun getDateInfo(record: FLogRecord): DateInfo {
        val date = filename.filenameOf(record.millis).also { check(it.isNotEmpty()) }
        val dateInfo = _dateInfo
        if (dateInfo == null || dateInfo.date != date) {
            close()
            val file = _directory.resolve("${date}.log")
            _dateInfo = DateInfo(
                date = date,
                file = file,
                store = storeFactory.create(file).safeStore(),
            )
        }
        return checkNotNull(_dateInfo)
    }

    /**
     * 检查日志大小
     */
    private fun checkLimit(dateInfo: DateInfo) {
        if (_limit <= 0) {
            // 不限制大小
            return
        }
        if (dateInfo.store.size() > (_limit / 2)) {
            dateInfo.store.close()
            val file = dateInfo.file
            val oldFile = file.resolveSibling("${file.name}.old")
            if (file.renameTo(oldFile)) {
                fDebug { "lib log file rename success" }
            } else {
                fDebug { "lib log file rename failed" }
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
                    fDebug { "lib log file not found close store" }
                    info.store.close()
                }
            }
        }
    }

    private class LogFileChecker(private val block: () -> Unit) {
        private var _idleHandler: IdleHandler? = null

        fun register(): Boolean {
            Looper.myLooper() ?: return false
            _idleHandler?.let { return true }
            val idleHandler = IdleHandler {
                libTryRun { block() }
                _idleHandler = null
                false
            }
            _idleHandler = idleHandler
            Looper.myQueue().addIdleHandler(idleHandler)
            return true
        }
    }
}

internal class LogPublisherImpl(
    directory: File,
    limitMBPerDay: Long,
    override val formatter: FLogFormatter,
    override val filename: LogFilename,
    override val storeFactory: FLogStore.Factory,
) : AbstractLogPublisher(
    directory = directory,
    limitMBPerDay = limitMBPerDay,
)