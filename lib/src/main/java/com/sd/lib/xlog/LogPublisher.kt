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
   * 日志压缩包目录，以.开头，不参与日志保留策略，
   * 里面的内容只在本次进程运行期间有效，初始化的时候会清空
   */
  val zipDirectory: File

  /**
   * 限制每天日志文件大小(单位B)，小于等于0表示不限制大小
   */
  fun setMaxBytePerDay(limit: Long)

  /**
   * 指定日期(yyyyMMdd)的日志目录
   */
  fun logDirOf(date: String): File

  /**
   * 指定日期的日志压缩包文件
   */
  fun zipFileOf(date: String): File
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

  @Volatile
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

  override val zipDirectory: File
    get() = directory.resolve(ZIP_DIR_NAME).resolveProcess()

  override fun logDirOf(date: String): File {
    require(date.isNotEmpty())
    return directory.resolve(date)
  }

  override fun zipFileOf(date: String): File {
    require(date.isNotEmpty())
    return zipDirectory.resolve("${date}.${ZIP_EXTENSION}")
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

  private fun getDateLogFile(date: String): File {
    require(date.isNotEmpty())
    return directory.resolve(date)
      .resolveProcess()
      .resolve("${date}.${filename.extension}")
  }

  /** 多进程的时候按进程名分子目录 */
  private fun File.resolveProcess(): File {
    return if (process.isNullOrEmpty()) this else resolve(process.replace(":", "_"))
  }
}

/** 日志压缩包目录名，以.开头表示是库的内部目录，不参与日志保留策略 */
private const val ZIP_DIR_NAME = ".zip"
private const val ZIP_EXTENSION = "zip"

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