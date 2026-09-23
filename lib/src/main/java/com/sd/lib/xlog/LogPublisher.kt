package com.sd.lib.xlog

import java.io.File

internal interface LogPublisher : AutoCloseable {
  /** 发布日志记录 */
  fun publish(record: FLogRecord)

  /** 关闭 */
  override fun close()

  /** 调度器空闲回调 */
  fun onIdle()
}

internal interface DirectoryLogPublisher : LogPublisher {
  /** 日志文件目录 */
  val directory: File

  /** 日志文件名 */
  val filename: LogFilename

  /**
   * 日志压缩包目录，以.开头，不参与日志保留策略，
   * 里面的内容只在本次进程运行期间有效，初始化的时候会清空。
   */
  val zipDirectory: File

  /** 限制每天日志文件大小(单位B)，小于等于0表示不限制大小 */
  fun setMaxBytePerDay(limit: Long)

  /** 指定日期(yyyyMMdd)的日志目录 */
  fun logDirOf(date: String): File

  /** 指定日期的日志压缩包文件 */
  fun zipFileOf(date: String): File
}

internal fun defaultLogPublisher(
  processProvider: () -> String?,
  directoryProvider: () -> File,
  filename: LogFilename,
  formatter: FLogFormatter,
  storeFactory: FLogStore.Factory,
): DirectoryLogPublisher {
  return LogPublisherImpl(
    processProvider = processProvider,
    directoryProvider = directoryProvider,
    filename = filename,
    formatter = formatter,
    storeFactory = storeFactory,
  )
}

private class LogPublisherImpl(
  processProvider: () -> String?,
  directoryProvider: () -> File,
  override val filename: LogFilename,
  private val formatter: FLogFormatter,
  private val storeFactory: FLogStore.Factory,
) : DirectoryLogPublisher {
  /** 获取进程名和目录可能有IPC或磁盘I/O，等到调度线程上第一次用到时再获取 */
  private val _process by lazy(processProvider)
  override val directory: File by lazy(directoryProvider)

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
      _handler = DateLogHandler(
        date = date,
        logDir = logDirOf(date).resolveProcess(),
        filename = filename,
        formatter = formatter,
        storeFactory = storeFactory,
      )
    }
    return checkNotNull(_handler)
  }

  /** 按进程名分子目录，取不到进程名时不分 */
  private fun File.resolveProcess(): File {
    val process = _process
    return if (process.isNullOrEmpty()) this else resolve(process.replace(":", "_"))
  }
}

/** 日志压缩包目录名，以.开头表示是库的内部目录，不参与日志保留策略 */
private const val ZIP_DIR_NAME = ".zip"
private const val ZIP_EXTENSION = "zip"

private class DateLogHandler(
  val date: String,
  private val logDir: File,
  private val filename: LogFilename,
  private val formatter: FLogFormatter,
  private val storeFactory: FLogStore.Factory,
) {
  /** 当前日志文件的序号，进程重启之后从已有的文件里恢复，接着往下写 */
  private var _seq: Int = logDir.listFiles()
    ?.mapNotNull { filename.seqOf(it.name) }
    ?.maxOrNull()
    ?: 0

  private var _logFile: File = logDir.resolve(filename.logNameOf(date, _seq))
  private var _logStore: FLogStore? = null

  fun publish(record: FLogRecord, maxBytePerDay: Long) {
    val logStore = getLogStore()
    val log = formatter.format(record)
    try {
      logStore.append(log)
    } catch (e: Throwable) {
      // 这条日志没写进去，要重置格式化器，否则下一条相同tag的日志会省略tag
      resetFormatter()
      throw e
    }
    checkLogSize(logStore, maxBytePerDay)
  }

  private fun getLogStore(): FLogStore {
    return _logStore ?: SafeLogStore(storeFactory.create(_logFile)).also { _logStore = it }
  }

  fun onIdle() {
    if (_logFile.isFile) {
      // 文件存在
    } else {
      // 文件不存在，关闭后会重新创建
      close()
    }
  }

  fun close() {
    _logStore?.close()
    resetFormatter()
  }

  private fun resetFormatter() {
    if (formatter is AutoCloseable) {
      formatter.close()
    }
  }

  private fun checkLogSize(logStore: FLogStore, maxBytePerDay: Long) {
    if (maxBytePerDay <= 0) {
      // 不限制大小
      return
    }

    val partSize = maxBytePerDay / 2
    if (logStore.size() < partSize) {
      // 还没写满当前文件
      return
    }

    // 当前文件写满，关掉之后切到下一个序号继续写
    close()
    _seq++
    _logFile = logDir.resolve(filename.logNameOf(date, _seq))

    /**
     * 新的日志仓库等下一条日志来的时候再创建。
     * 如果在这里创建，[FLogStore.Factory]抛异常的话，
     * [_logStore]会继续指向旧文件，下一条日志就写回旧文件里去了，
     * 于是每条日志都触发一次轮换、每次都失败，文件无限增长，
     * [FLog.setMaxMBPerDay]的限制形同虚设。
     */
    _logStore = null

    deleteOldLog()
  }

  /**
   * 只保留当前和上一个日志文件。
   * 删除失败只是多留一个文件，不重试，不影响写入。
   */
  private fun deleteOldLog() {
    logDir.listFiles()?.forEach { file ->
      val seq = filename.seqOf(file.name) ?: return@forEach
      if (seq <= _seq - KEEP_COUNT) {
        file.delete().also { deleted ->
          if (!deleted) libLog("delete old log file ${file.name} failed")
        }
      }
    }
  }
}

/** 保留的日志文件个数，当前文件加上一个写满的文件 */
private const val KEEP_COUNT = 2

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