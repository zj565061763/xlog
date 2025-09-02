package com.sd.lib.xlog

import android.util.Log
import java.io.File

enum class FLogLevel {
  /** 开启所有日志 */
  All,

  Verbose, Debug, Info, Warning, Error,

  /** 关闭所有日志 */
  Off,
}

object FLog {
  /** 是否已经初始化 */
  private var _hasInit = false

  /** 日志等级 */
  @Volatile
  private var _level = FLogLevel.All

  /** 是否打印控制台日志 */
  @Volatile
  private var _consoleLogEnabled = true

  /** [FLogger]配置信息 */
  private var _configHolder: MutableMap<Class<out FLogger>, FLoggerConfig>? = null

  /** 文件日志发布 */
  private lateinit var _publisher: DirectoryLogPublisher

  /** 日志调度器 */
  private lateinit var _dispatcher: FLogDispatcher

  /**
   * 初始化
   * @return true-初始化成功；false-已经初始化过了
   */
  @JvmStatic
  @JvmOverloads
  fun init(
    /** 日志文件目录 */
    directory: File,
    /** 日志格式化 */
    formatter: FLogFormatter? = null,
    /** 日志仓库工厂 */
    storeFactory: FLogStore.Factory? = null,
    /** 日志调度器 */
    dispatcher: FLogDispatcher? = null,
  ): Boolean {
    synchronized(FLog) {
      if (_hasInit) return false
      _publisher = defaultLogPublisher(
        directory = directory,
        filename = defaultLogFilename(),
        formatter = formatter ?: defaultLogFormatter(),
        storeFactory = storeFactory ?: FLogStore.Factory { defaultLogStore(it) },
      ).safePublisher()
      _dispatcher = defaultLogDispatcher(
        dispatcher = dispatcher,
        onIdle = { handleDispatcherIdle() },
      )
      _hasInit = true
      return true
    }
  }

  /**
   * 是否打打印控制台日志，[FLog.logConsole]方法不受此开关的限制
   */
  @JvmStatic
  fun setConsoleLogEnabled(enabled: Boolean) {
    checkInit()
    _consoleLogEnabled = enabled
  }

  /**
   * 限制每天日志文件大小(单位MB)，小于等于0表示不限制，默认不限制
   */
  @JvmStatic
  fun setMaxMBPerDay(mb: Int) {
    checkInit()
    _publisher.setMaxBytePerDay(mb * 1024 * 1024L)
  }

  /**
   * 设置日志等级
   */
  @JvmStatic
  fun setLevel(level: FLogLevel) {
    checkInit()
    _level = level
    if (level == FLogLevel.Off) {
      dispatch {
        // 发送一个空消息，等待调度器空闲的时候处理空闲逻辑
      }
    }
  }

  /**
   * 修改[FLogger]配置信息
   */
  inline fun <reified T : FLogger> config(noinline block: FLoggerConfig.() -> Unit) {
    config(T::class.java, block)
  }

  /**
   * 修改[FLogger]配置信息
   */
  @JvmStatic
  fun config(clazz: Class<out FLogger>, block: FLoggerConfig.() -> Unit) {
    checkInit()
    synchronized(FLog) {
      val holder = _configHolder ?: mutableMapOf<Class<out FLogger>, FLoggerConfig>().also { _configHolder = it }
      val config = holder.getOrPut(clazz) { FLoggerConfig() }
      config.block()
      if (config.isEmpty()) {
        holder.remove(clazz)
        if (holder.isEmpty()) {
          _configHolder = null
        }
      }
    }
  }

  private fun getConfig(clazz: Class<out FLogger>): FLoggerConfig? {
    synchronized(FLog) {
      return _configHolder?.get(clazz)
    }
  }

  @PublishedApi
  internal fun isLoggable(clazz: Class<out FLogger>, level: FLogLevel): Boolean {
    checkInit()
    checkLoggable(level)

    if (_level == FLogLevel.Off) {
      /** 如果全局等级为[FLogLevel.Off]，不读取[FLoggerConfig]，不打印日志 */
      return false
    }

    val limitLevel = getConfig(clazz)?.level ?: _level
    return level >= limitLevel
  }

  @PublishedApi
  internal fun log(clazz: Class<out FLogger>, level: FLogLevel, msg: String?) {
    synchronized(FLog) {
      if (msg.isNullOrEmpty()) return
      if (!isLoggable(clazz, level)) return

      val config = getConfig(clazz)
      val tag = config?.tag?.takeIf { it.isNotEmpty() } ?: clazz.simpleName

      newLogRecord(
        logger = clazz,
        tag = tag,
        msg = msg,
        level = level,
      )
    }.also { record ->
      dispatch {
        if (_consoleLogEnabled) record.consoleLog()
        _publisher.publish(record)
      }
    }
  }

  /**
   * 删除日志
   * @param saveDays 要保留的日志天数，小于等于0表示删除全部日志
   */
  @JvmStatic
  fun deleteLog(saveDays: Int) {
    logDirectory { dir ->
      if (saveDays <= 0) {
        dir.deleteRecursively()
        return@logDirectory
      }

      val files = dir.listFiles()
      if (files.isNullOrEmpty()) {
        return@logDirectory
      }

      val filename = _publisher.filename
      val today = filename.filenameOf(System.currentTimeMillis())

      files.forEach { file ->
        val diffDays = filename.diffDays(today, file.name)
        if (diffDays == null || diffDays > (saveDays - 1)) {
          file.deleteRecursively()
        }
      }
    }
  }

  /**
   * 访问日志文件目录，[block]在[FLogDispatcher]调度器上面执行
   */
  @JvmStatic
  fun logDirectory(block: (File) -> Unit) {
    dispatch {
      _publisher.close()
      block(_publisher.directory)
    }
  }

  /**
   * 在调度器上面执行
   */
  private fun dispatch(task: Runnable) {
    checkInit()
    _dispatcher.dispatch(task)
  }

  /**
   * 调度器空闲逻辑
   */
  private fun handleDispatcherIdle() {
    if (_level == FLogLevel.Off) {
      _publisher.close()
    } else {
      _publisher.onIdle()
    }
  }

  //---------- other ----------

  private fun checkInit() {
    if (_hasInit) return
    synchronized(FLog) {
      check(_hasInit) { "You should init before this." }
    }
  }

  @PublishedApi
  internal fun isLoggableConsole(level: FLogLevel): Boolean {
    checkInit()
    checkLoggable(level)
    return level >= _level
  }

  /**
   * 打印控制台日志，不会写入到文件中，默认tag为[DefaultDebugTag]，
   * 如果[level]等于[FLogLevel.All]或者[FLogLevel.Off]则抛异常[IllegalArgumentException]，
   * 注意：此方法不受[setConsoleLogEnabled]开关限制，只受日志等级限制
   */
  @JvmStatic
  @JvmOverloads
  fun logConsole(
    tag: String = DefaultDebugTag,
    level: FLogLevel = FLogLevel.Debug,
    msg: String?,
  ) {
    if (msg.isNullOrEmpty()) return
    if (isLoggableConsole(level)) {
      when (level) {
        FLogLevel.Verbose -> Log.v(tag, msg)
        FLogLevel.Debug -> Log.d(tag, msg)
        FLogLevel.Info -> Log.i(tag, msg)
        FLogLevel.Warning -> Log.w(tag, msg)
        FLogLevel.Error -> Log.e(tag, msg)
        else -> {}
      }
    }
  }

  /**
   * 打印[FLogLevel.Verbose]日志
   */
  @JvmStatic
  fun logV(clazz: Class<out FLogger>, msg: String?) {
    log(clazz, FLogLevel.Verbose, msg)
  }

  /**
   * 打印[FLogLevel.Debug]日志
   */
  @JvmStatic
  fun logD(clazz: Class<out FLogger>, msg: String?) {
    log(clazz, FLogLevel.Debug, msg)
  }

  /**
   * 打印[FLogLevel.Info]日志
   */
  @JvmStatic
  fun logI(clazz: Class<out FLogger>, msg: String?) {
    log(clazz, FLogLevel.Info, msg)
  }

  /**
   * 打印[FLogLevel.Warning]日志
   */
  @JvmStatic
  fun logW(clazz: Class<out FLogger>, msg: String?) {
    log(clazz, FLogLevel.Warning, msg)
  }

  /**
   * 打印[FLogLevel.Error]日志
   */
  @JvmStatic
  fun logE(clazz: Class<out FLogger>, msg: String?) {
    log(clazz, FLogLevel.Error, msg)
  }
}

@PublishedApi
internal const val DefaultDebugTag = "DebugLogger"

private fun checkLoggable(level: FLogLevel) {
  require(level != FLogLevel.All)
  require(level != FLogLevel.Off)
}

private fun FLogRecord.consoleLog() {
  when (level) {
    FLogLevel.Verbose -> Log.v(tag, msg)
    FLogLevel.Debug -> Log.d(tag, msg)
    FLogLevel.Info -> Log.i(tag, msg)
    FLogLevel.Warning -> Log.w(tag, msg)
    FLogLevel.Error -> Log.e(tag, msg)
    else -> {}
  }
}