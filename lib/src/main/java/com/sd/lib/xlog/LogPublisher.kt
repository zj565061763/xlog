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

  /**
   * 调度器空闲回调
   */
  fun onIdle()
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
   * 获取指定年月日的日志文件
   */
  fun logOf(year: Int, month: Int, dayOfMonth: Int): List<File>
}

internal fun defaultLogPublisher(
  process: String?,
  directory: File,
  filename: LogFilename,
  formatter: FLogFormatter,
  storeFactory: FLogStore.Factory,
): DirectoryLogPublisher {
  return LogPublisherImpl(
    process = process,
    directory = directory,
    filename = filename,
    formatter = formatter,
    storeFactory = storeFactory,
  )
}

private class LogPublisherImpl(
  private val process: String?,
  override val directory: File,
  override val filename: LogFilename,
  private val formatter: FLogFormatter,
  private val storeFactory: FLogStore.Factory,
) : DirectoryLogPublisher {
  private var _handler: DateLogHandler? = null
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
    val date = filename.dateOf(year = year, month = month, dayOfMonth = dayOfMonth)
    val file = getDateLogFile(date)
    val file1 = getDateLogFile(date, suffix = ".1")
    return buildList {
      if (file.exists()) add(file)
      if (file1.exists()) add(file1)
    }
  }

  private fun getHandler(record: FLogRecord): DateLogHandler {
    val date = filename.dateOf(record.millis)
    if (_handler?.date != date) {
      close()
      val logFile = getDateLogFile(date)
      _handler = DateLogHandler(
        date = date,
        logFile = logFile,
        logStore = SafeLogStore(storeFactory.create(logFile)),
        formatter = formatter,
      )
    }
    return checkNotNull(_handler)
  }

  private fun getDateLogFile(date: String, suffix: String = ""): File {
    require(date.isNotEmpty())
    val logDir = directory.resolve(date).let { if (process.isNullOrEmpty()) it else it.resolve(process) }
    return logDir.resolve("${date}.${filename.extension}${suffix}")
  }
}

private class DateLogHandler(
  val date: String,
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
    val partFile = logFile.resolveSibling("${logFile.name}.1").also { it.deleteRecursively() }
    logFile.renameTo(partFile).also { rename ->
      libLog { "part log file rename $rename" }
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