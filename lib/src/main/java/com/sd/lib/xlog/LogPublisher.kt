package com.sd.lib.xlog

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

    /** 日志文件名 */
    val filename: LogFilename

    /**
     * 限制每天日志文件大小(单位B)，小于等于0表示不限制大小
     */
    fun setLimitPerDay(limit: Long)

    /**
     * 调度器空闲回调
     */
    fun onIdle()
}

internal fun defaultLogPublisher(
    directory: File,
    filename: LogFilename,
    formatter: FLogFormatter,
    storeFactory: FLogStore.Factory,
): DirectoryLogPublisher {
    return LogPublisherImpl(
        directory = directory,
        filename = filename,
        formatter = formatter,
        storeFactory = storeFactory,
    )
}

private class LogPublisherImpl(
    override val directory: File,
    override val filename: LogFilename,
    private val formatter: FLogFormatter,
    private val storeFactory: FLogStore.Factory,
) : DirectoryLogPublisher {

    private data class DateInfo(
        val date: String,
        val file: File,
        val store: FLogStore,
    )

    @Volatile
    private var _limitPerDay: Long = 0
    private var _dateInfo: DateInfo? = null

    override fun setLimitPerDay(limit: Long) {
        _limitPerDay = limit
    }

    override fun publish(record: FLogRecord) {
        val dateInfo = getDateInfo(record)

        // 保存日志
        dateInfo.store.append(
            formatter.format(record)
        )

        // 检查日志大小
        dateInfo.checkLimit()
    }

    override fun close() {
        _dateInfo?.let { info ->
            _dateInfo = null
            info.closeStore()
        }
    }

    private fun getDateInfo(record: FLogRecord): DateInfo {
        val date = filename.filenameOf(record.millis)
        if (_dateInfo?.date != date) {
            close()
            val file = directory.resolve("${date}.log")
            _dateInfo = DateInfo(
                date = date,
                file = file,
                store = SafeLogStore(storeFactory.create(file)),
            )
        }
        return checkNotNull(_dateInfo)
    }

    override fun onIdle() {
        _dateInfo?.let { info ->
            if (info.file.isFile) {
                // 文件存在
            } else {
                // 文件不存在，关闭后会重新创建
                info.closeStore()
            }
        }
    }

    private fun DateInfo.checkLimit() {
        val limit = _limitPerDay
        if (limit <= 0) {
            // 不限制大小
            return
        }

        val partLimit = limit / 2
        if (store.size() < partLimit) {
            // 还未超过限制
            return
        }

        // 关闭并重命名
        closeStore()
        val partFile = file.resolveSibling("${file.name}.1")
        file.renameTo(partFile).also { rename ->
            fDebug {
                val res = if (rename) "success" else "failed"
                "lib publisher log file rename $res ${this@LogPublisherImpl}"
            }
        }
    }

    private fun DateInfo.closeStore() {
        store.close()
        if (formatter is AutoCloseable) {
            formatter.close()
        }
    }
}

private class SafeLogStore(
    private val instance: FLogStore,
) : FLogStore {
    override fun append(log: String) {
        runCatching { instance.append(log) }
            .onFailure {
                close()
                throw it
            }
    }

    override fun size(): Long {
        return runCatching { instance.size() }
            .getOrElse {
                close()
                throw it
            }
    }

    override fun close() {
        libRunCatching { instance.close() }
    }
}