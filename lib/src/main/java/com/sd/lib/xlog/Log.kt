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

enum class FLogMode {
  /** 默认，发布到控制台和日志仓库 */
  Default,

  /** 仅发布到控制台 */
  Console,

  /** 仅发布到日志仓库 */
  Store,
}

object FLog {
  /** 是否已经初始化 */
  private var _hasInit = false

  /** 日志等级 */
  @Volatile
  private var _level: FLogLevel = FLogLevel.All
  /** 日志模式 */
  @Volatile
  private var _mode: FLogMode = FLogMode.Default

  /** 日志发布 */
  private lateinit var _publisher: DirectoryLogPublisher
  /** 日志调度器 */
  private lateinit var _dispatcher: FLogDispatcher
  /** [FLogger]配置信息 */
  private lateinit var _configHolder: Map<Class<out FLogger>, FLoggerConfig>

  /**
   * 初始化
   * @return true-初始化成功；false-已经初始化过了
   */
  @JvmStatic
  @JvmOverloads
  fun init(
    /** 日志文件目录 */
    directory: File,
    /** 初始化 */
    initBlock: FLogInitScope.() -> Unit = {},
  ): Boolean {
    synchronized(FLog) {
      if (_hasInit) return false
      val initScope = LogInitScopeImpl().apply(initBlock)

      _publisher = defaultLogPublisher(
        directory = directory,
        filename = defaultLogFilename(),
        formatter = initScope.formatter ?: defaultLogFormatter(),
        storeFactory = initScope.storeFactory ?: FLogStore.Factory { defaultLogStore(it) },
      ).safePublisher()

      _dispatcher = defaultLogDispatcher(
        dispatcher = initScope.dispatcher,
        onIdle = { handleDispatcherIdle() },
      )

      _configHolder = initScope.configHolder.toMap()
      _hasInit = true
      return true
    }
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
   * 设置日志模式
   */
  @JvmStatic
  fun setMode(mode: FLogMode) {
    checkInit()
    _mode = mode
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

      for (file in files) {
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
  fun logDirectory(block: FLogDirectoryScope.(File) -> Unit) {
    dispatch {
      _publisher.close()
      val scope = LogDirectoryScopeImpl(_publisher)
      scope.block(_publisher.directory)
      scope.destroy()
    }
  }

  @PublishedApi
  internal fun log(
    logger: Class<out FLogger>,
    level: FLogLevel,
    mode: FLogMode?,
    msg: String?,
  ) {
    if (msg.isNullOrEmpty()) return
    val config = getConfig(logger)
    if (!isLoggable(level, config)) return
    val tag = (config?.tag ?: "").ifEmpty { logger.simpleName }
    when (mode ?: config?.mode ?: _mode) {
      FLogMode.Default -> {
        val record = newLogRecord(logger = logger, level = level, tag = tag, msg = msg)
        publishConsoleLog(level = level, tag = tag, msg = msg)
        dispatch { _publisher.publish(record) }
      }
      FLogMode.Console -> {
        publishConsoleLog(level = level, tag = tag, msg = msg)
      }
      FLogMode.Store -> {
        val record = newLogRecord(logger = logger, level = level, tag = tag, msg = msg)
        dispatch { _publisher.publish(record) }
      }
    }
  }

  @PublishedApi
  internal fun isLoggable(logger: Class<out FLogger>, level: FLogLevel): Boolean {
    return isLoggable(level = level, config = getConfig(logger))
  }

  private fun isLoggable(level: FLogLevel, config: FLoggerConfig?): Boolean {
    checkInit()
    checkLoggable(level)

    if (_level == FLogLevel.Off) {
      /** 如果全局等级为[FLogLevel.Off]，不读取[FLoggerConfig]，不打印日志 */
      return false
    }

    val limitLevel = config?.level ?: _level
    return level >= limitLevel
  }

  private fun getConfig(logger: Class<out FLogger>): FLoggerConfig? {
    if (_configHolder.isEmpty()) return null
    return _configHolder[logger]
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

  private fun checkInit() {
    if (_hasInit) return
    synchronized(FLog) {
      check(_hasInit) { "You should init before this." }
    }
  }

  //---------- other ----------

  /**
   * 打印[FLogLevel.Verbose]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logV(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Verbose, mode, msg)
  }

  /**
   * 打印[FLogLevel.Debug]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logD(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Debug, mode, msg)
  }

  /**
   * 打印[FLogLevel.Info]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logI(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Info, mode, msg)
  }

  /**
   * 打印[FLogLevel.Warning]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logW(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Warning, mode, msg)
  }

  /**
   * 打印[FLogLevel.Error]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logE(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Error, mode, msg)
  }
}

private fun checkLoggable(level: FLogLevel) {
  require(level != FLogLevel.All)
  require(level != FLogLevel.Off)
}

private fun publishConsoleLog(level: FLogLevel, tag: String, msg: String) {
  when (level) {
    FLogLevel.Verbose -> Log.v(tag, msg)
    FLogLevel.Debug -> Log.d(tag, msg)
    FLogLevel.Info -> Log.i(tag, msg)
    FLogLevel.Warning -> Log.w(tag, msg)
    FLogLevel.Error -> Log.e(tag, msg)
    else -> {}
  }
}