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
  fun setMaxBytePerDay(limit: Long)

  /**
   * 调度器空闲回调
   */
  fun onIdle()

  /**
   * 获取指定年月日的日志文件
   */
  fun logOf(year: Int, month: Int, dayOfMonth: Int): List<File>
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
  private var _handler: LogDateHandler? = null
  private var _maxBytePerDay: Long = 0

  override fun setMaxBytePerDay(limit: Long) {
    _maxBytePerDay = limit
  }

  override fun publish(record: FLogRecord) {
    getHandler(record).publish(record, _maxBytePerDay)
  }

  override fun close() {
    _handler?.also {
      _handler = null
      it.close()
    }
  }

  override fun onIdle() {
    _handler?.onIdle()
  }

  override fun logOf(year: Int, month: Int, dayOfMonth: Int): List<File> {
    val logFilename = filename.filenameOf(year = year, month = month, dayOfMonth = dayOfMonth)
    val file = directory.resolve(logFilename)
    val file1 = directory.resolve("${logFilename}.1")
    return buildList {
      if (file.exists()) add(file)
      if (file1.exists()) add(file1)
    }
  }

  private fun getHandler(record: FLogRecord): LogDateHandler {
    val logFilename = filename.filenameOf(record.millis)
    if (_handler?.logFilename != logFilename) {
      close()
      val logFile = directory.resolve(logFilename)
      _handler = LogDateHandler(
        logFilename = logFilename,
        logFile = logFile,
        logStore = SafeLogStore(storeFactory.create(logFile)),
        formatter = formatter,
      )
    }
    return checkNotNull(_handler)
  }
}

private class LogDateHandler(
  val logFilename: String,
  private val logFile: File,
  private val logStore: FLogStore,
  private val formatter: FLogFormatter,
) {
  fun publish(record: FLogRecord, maxBytePerDay: Long) {
    logStore.append(formatter.format(record))
    checkLogSize(maxBytePerDay)
  }

  fun onIdle() {
    if (logFile.isFile) {
      // 文件存在
    } else {
      // 文件不存在，关闭后会重新创建
      close()
    }
  }

  fun close() {
    logStore.close()
    if (formatter is AutoCloseable) {
      formatter.close()
    }
  }

  private fun checkLogSize(maxBytePerDay: Long) {
    if (maxBytePerDay <= 0) {
      // 不限制大小
      return
    }

    val partSize = maxBytePerDay / 2
    if (logStore.size() < partSize) {
      // 还未超过限制
      return
    }

    // 关闭并重命名
    close()
    val partFile = logFile.resolveSibling("${logFilename}.1").also { it.deleteRecursively() }
    logFile.renameTo(partFile).also { rename ->
      libLog { "lib part log file rename $rename" }
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