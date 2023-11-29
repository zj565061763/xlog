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

internal fun defaultPublisher(
    /** 日志文件目录 */
    directory: File,

    /** 限制每天日志文件大小(单位B)，小于等于0表示不限制大小 */
    limitPerDay: Long,

    /** 日志格式化 */
    formatter: FLogFormatter,

    /** 日志文件名 */
    filename: LogFilename,

    /** 日志仓库工厂 */
    storeFactory: FLogStore.Factory
): LogPublisher {
    return LogPublisherImpl(directory).apply {
        this.setLimitPerDay(limitPerDay)
        this.setFormatter(formatter)
        this.setFilename(filename)
        this.setStoreFactory(storeFactory)
    }
}

private class LogPublisherImpl(directory: File) : LogPublisher {
    private data class DateInfo(
        val date: String,
        val file: File,
        val store: FLogStore,
    )

    /** 日志文件目录 */
    private val _directory = directory

    // 配置信息
    private var _limit: Long = 0
    private var _formatter: FLogFormatter = LogFormatterDefault()
    private var _filename: LogFilename = LogFilenameDefault()
    private var _storeFactory: FLogStore.Factory = FLogStore.Factory { defaultLogStore(it) }

    private var _dateInfo: DateInfo? = null
    private val _logFileChecker = SafeIdleHandler {
        checkLogFileExist()
        false
    }

    /**
     * 限制每天日志文件大小(单位B)，小于等于0表示不限制大小
     */
    fun setLimitPerDay(limit: Long) {
        _limit = limit
    }

    /**
     * 设置日志格式化
     */
    fun setFormatter(formatter: FLogFormatter) {
        _formatter = formatter
    }

    /**
     * 设置日志文件名
     */
    fun setFilename(filename: LogFilename) {
        _filename = filename
    }

    /**
     * 设置日志仓库工厂
     */
    fun setStoreFactory(factory: FLogStore.Factory) {
        _storeFactory = factory
    }

    override fun publish(record: FLogRecord) {
        val log = _formatter.format(record)
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
        _dateInfo?.store?.close()
        _dateInfo = null
    }

    private fun getDateInfo(record: FLogRecord): DateInfo {
        val date = _filename.filenameOf(record.millis).also { check(it.isNotEmpty()) }
        val dateInfo = _dateInfo
        if (dateInfo == null || dateInfo.date != date) {
            close()
            val file = _directory.resolve("${date}.log")
            _dateInfo = DateInfo(
                date = date,
                file = file,
                store = _storeFactory.create(file).safeStore(),
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
                    info.store.close()
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
}