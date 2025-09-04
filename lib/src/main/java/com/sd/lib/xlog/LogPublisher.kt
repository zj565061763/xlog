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

  private data class DateInfo(
    val logFile: File,
    val logFilename: String,
    val logStore: FLogStore,
  )

  private var _dateInfo: DateInfo? = null
  private var _maxBytePerDay: Long = 0

  override fun setMaxBytePerDay(limit: Long) {
    _maxBytePerDay = limit
  }

  override fun publish(record: FLogRecord) {
    with(getDateInfo(record)) {
      logStore.append(formatter.format(record))
      checkLogSize()
    }
  }

  override fun close() {
    _dateInfo?.also { info ->
      _dateInfo = null
      info.closeStore()
    }
  }

  private fun getDateInfo(record: FLogRecord): DateInfo {
    val logFilename = filename.filenameOf(record.millis)
    if (_dateInfo?.logFilename != logFilename) {
      close()
      val logFile = directory.resolve(logFilename)
      _dateInfo = DateInfo(
        logFile = logFile,
        logFilename = logFilename,
        logStore = SafeLogStore(storeFactory.create(logFile)),
      )
    }
    return checkNotNull(_dateInfo)
  }

  override fun onIdle() {
    _dateInfo?.also { info ->
      if (info.logFile.isFile) {
        // 文件存在
      } else {
        // 文件不存在，关闭后会重新创建
        info.closeStore()
      }
    }
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

  private fun DateInfo.checkLogSize() {
    val maxBytePerDay = _maxBytePerDay
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
    closeStore()
    val partFile = logFile.resolveSibling("${logFilename}.1").also { it.deleteRecursively() }
    logFile.renameTo(partFile).also { rename ->
      libLog {
        val res = if (rename) "success" else "failed"
        "lib publisher part log file rename $res ${this@LogPublisherImpl}"
      }
    }
  }

  private fun DateInfo.closeStore() {
    logStore.close()
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