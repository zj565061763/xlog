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
  /** 日志文件目录，取不到时为null，取到之后不再变化 */
  val directory: File?

  /** 日志文件名 */
  val filename: LogFilename

  /** 限制每天日志文件大小(单位B)，小于等于0表示不限制大小 */
  fun setMaxBytePerDay(limit: Long)

  /** 指定日期(yyyyMMdd)的日志目录，取不到日志目录时返回null */
  fun logDirOf(date: String): File?

  /** 指定日期的日志压缩包文件，取不到日志目录时返回null */
  fun zipFileOf(date: String): File?

  /**
   * 删除本进程的压缩包目录，初始化的时候调用。
   * 取不到进程名时压缩包目录是所有进程共用的，不删除，避免误删其他进程的压缩包。
   */
  fun deleteZipDirectory()
}

internal fun defaultLogPublisher(
  processProvider: () -> String?,
  directoryProvider: () -> File?,
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
  private val directoryProvider: () -> File?,
  override val filename: LogFilename,
  private val formatter: FLogFormatter,
  private val storeFactory: FLogStore.Factory,
) : DirectoryLogPublisher {
  /** 获取进程名和目录可能有IPC或磁盘I/O，等到调度线程上第一次用到时再获取 */
  private val _process by lazy { processProvider()?.takeIf { it.isValidDirName() } }
  private var _directory: File? = null

  /** 取不到时下次用到再获取，取到之后固定不变 */
  override val directory: File?
    get() = _directory ?: directoryProvider()?.also { _directory = it }

  private var _handler: DateLogHandler? = null

  @Volatile
  private var _maxBytePerDay: Long = 0

  override fun setMaxBytePerDay(limit: Long) {
    _maxBytePerDay = limit
  }

  override fun publish(record: FLogRecord) {
    // 取不到日志目录时丢弃
    getHandler(record)?.publish(record, _maxBytePerDay)
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

  override fun logDirOf(date: String): File? {
    require(date.isNotEmpty())
    return directory?.resolve(date)
  }

  override fun zipFileOf(date: String): File? {
    require(date.isNotEmpty())
    val dir = directory ?: return null
    return zipDirectoryOf(dir).resolve("${date}.${ZIP_EXTENSION}")
  }

  override fun deleteZipDirectory() {
    if (_process.isNullOrEmpty()) return
    val dir = directory ?: return
    if (!zipDirectoryOf(dir).deleteRecursively()) libLog("delete zip directory failed")
  }

  /** 日志目录[dir]下的压缩包目录，以.开头，不参与日志保留策略，里面的内容只在本次进程运行期间有效 */
  private fun zipDirectoryOf(dir: File): File = dir.resolve(ZIP_DIR_NAME).resolveProcess()

  private fun getHandler(record: FLogRecord): DateLogHandler? {
    val date = filename.dateOf(record.millis)
    if (_handler?.date != date) {
      val dateDir = logDirOf(date) ?: return null
      close()
      _handler = DateLogHandler(
        date = date,
        dateDir = dateDir,
        logDir = dateDir.resolveProcess(),
        filename = filename,
        formatter = formatter,
        storeFactory = storeFactory,
      )
    }
    return _handler
  }

  /**
   * 按进程名分子目录，取不到进程名时不分。
   * :在FAT文件名里不合法，替换为进程名里不会出现的-；
   * 不能替换为_，全局进程名可以含_，com.example:worker会和com.example_worker共用目录。
   */
  private fun File.resolveProcess(): File {
    val process = _process
    return if (process.isNullOrEmpty()) this else resolve(process.replace(":", "-"))
  }
}

/** 能否用作一层目录名，含路径分隔符或者是.和..时会跳出所在目录 */
private fun String.isValidDirName(): Boolean = '/' !in this && this != "." && this != ".."

/** 日志压缩包目录名，以.开头表示是库的内部目录，不参与日志保留策略 */
private const val ZIP_DIR_NAME = ".zip"
private const val ZIP_EXTENSION = "zip"

private class DateLogHandler(
  val date: String,
  /** 日期目录 */
  private val dateDir: File,
  /** 存放日志文件的目录，有进程名时是[dateDir]下的进程目录，否则就是[dateDir] */
  private val logDir: File,
  private val filename: LogFilename,
  private val formatter: FLogFormatter,
  private val storeFactory: FLogStore.Factory,
) {
  init {
    deleteOccupiedDirs()
  }

  /**
   * 当前日志文件的序号，进程重启之后从已有的文件里恢复，接着往下写。
   * 目录存在但读取失败时抛异常，这条日志不写入，下一条日志重新读取；
   * 不能当作空目录，否则会从序号0开始写，和已有的文件接不上。
   */
  private var _seq: Int = logDir.listFilesOrNull()
    ?.mapNotNull { filename.seqOf(it.name) }
    ?.maxOrNull()
    ?: 0

  private var _logFile: File = logDir.resolve(filename.logNameOf(date, _seq))
  private var _logStore: FLogStore? = null

  fun publish(record: FLogRecord, maxBytePerDay: Long) {
    val logStore = getLogStore()
    try {
      logStore.append(formatter.format(record))
      checkLogSize(logStore, maxBytePerDay)
    } catch (e: Throwable) {
      /**
       * 仓库出错时已经关闭，和关闭日志文件一样要重置格式化器。
       * 写入失败时这条日志没写进去，不重置的话下一条相同tag的日志会省略tag。
       */
      resetFormatter()
      throw e
    }
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
      deleteOccupiedDirs()
    }
  }

  fun close() {
    _logStore?.close()
    resetFormatter()
  }

  /**
   * 日期目录、进程目录被同名文件占用时删掉，否则当天的日志一直写不进文件。
   * 只处理日志目录里面的这两层：日志目录只能存放日志，不用担心误删；
   * 日志目录本身和它上层的路径不属于日志库，被文件占用时不能删。
   */
  private fun deleteOccupiedDirs() {
    if (dateDir.isFile) dateDir.delete()
    if (logDir.isFile) logDir.delete()
  }

  private fun resetFormatter() {
    // 出错不往外抛，否则会中断日志轮换，一直写回旧文件
    libRunCatching { formatter.reset() }
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
   * 目录读取失败、删除失败都只是多留文件，不重试，不影响写入。
   */
  private fun deleteOldLog() {
    // 序号已经切换，这条日志也已经写入，读取失败只输出不往外抛
    val files = libRunCatching { logDir.listFilesOrNull() }.getOrNull() ?: return
    files.forEach { file ->
      val seq = filename.seqOf(file.name) ?: return@forEach
      if (seq <= _seq - KEEP_COUNT) {
        if (!file.deleteOrAbsent()) libLog("delete old log file ${file.name} failed")
      }
    }
  }
}

/** 保留的日志文件个数，当前文件加上一个写满的文件 */
private const val KEEP_COUNT = 2

/** 删除文件，已经不存在也算成功，比如列出之后被其他进程删除 */
internal fun File.deleteOrAbsent(): Boolean = delete() || !exists()

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