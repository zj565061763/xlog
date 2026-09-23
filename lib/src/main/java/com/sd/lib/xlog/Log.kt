package com.sd.lib.xlog

import android.content.Context
import android.util.Log
import java.io.File

/** 日志等级，按声明顺序从低到高 */
enum class FLogLevel {
  /** 开启所有日志 */
  All,

  Verbose, Debug, Info, Warning, Error,

  /** 关闭所有日志 */
  Off,
}

/** 日志模式 */
enum class FLogMode {
  /** 默认，发布到控制台和日志仓库 */
  Default,

  /** 仅发布到控制台 */
  Console,

  /** 仅发布到日志仓库 */
  Store,
}

/** 日志库入口，使用前需要先调用[init] */
object FLog {
  /** 是否已经初始化 */
  @Volatile
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
  fun init(context: Context, initBlock: FLogInitScope.() -> Unit = {}): Boolean {
    synchronized(FLog) {
      if (_hasInit) return false
      val initScope = LogInitScopeImpl().apply(initBlock)

      _publisher = defaultLogPublisher(
        process = context.currentProcess(),
        directory = initScope.directory ?: context.fLogDir(),
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

      /**
       * 清空上次运行遗留的压缩包。
       * 压缩包只是导出用的临时产物，使用方需要长期保存的话应该自己移走。
       */
      dispatch { _publisher.zipDirectory.deleteRecursively() }
      return true
    }
  }

  /** 设置日志等级，默认[FLogLevel.All] */
  @JvmStatic
  fun setLevel(level: FLogLevel) {
    checkInit()
    _level = level
    if (level == FLogLevel.Off) {
      dispatch {
        // 提交一个空任务，调度器空闲时关闭日志文件
      }
    }
  }

  /** 设置日志模式，默认[FLogMode.Default] */
  @JvmStatic
  fun setMode(mode: FLogMode) {
    checkInit()
    _mode = mode
  }

  /** 限制每天日志文件大小(单位MB)，多进程时每个进程单独计算，小于等于0表示不限制，默认不限制 */
  @JvmStatic
  fun setMaxMBPerDay(mb: Int) {
    checkInit()
    _publisher.setMaxBytePerDay(mb * 1024L * 1024L)
  }

  /**
   * 删除日志，在调度器上执行。
   * 不会删除[FLogDirectoryScope.logZipOf]导出的压缩包。
   * @param saveDays 要保留的日志天数，1表示只保留当天，小于等于0表示删除全部日志
   */
  @JvmStatic
  fun deleteLog(saveDays: Int) {
    logDirectory { dir ->
      val files = dir.listFiles()
      if (!files.isNullOrEmpty()) {
        val filename = _publisher.filename
        val today = filename.dateOf(System.currentTimeMillis())

        for (file in files) {
          /**
           * 以.开头的是库的内部目录（比如导出的日志压缩包），不受日志保留策略管辖，
           * 所以即使是删除全部日志也不动它。
           */
          if (file.name.startsWith(".")) continue

          if (filename.shouldDeleteLog(today = today, date = file.name, saveDays = saveDays)) {
            file.deleteRecursively()
          }
        }
      }
    }
  }

  /**
   * 访问日志目录，[block]在调度器上执行，执行前会先关闭当前的日志文件。
   * 目录只能存放日志，[deleteLog]会删除其中不是日志的文件。
   */
  @JvmStatic
  fun logDirectory(block: FLogDirectoryScope.(File) -> Unit) {
    dispatch {
      _publisher.close()
      val scope = LogDirectoryScopeImpl(_publisher)
      try {
        // 避免外部传入的[block]抛异常导致App崩溃
        libRunCatching { scope.block(_publisher.directory) }
      } finally {
        scope.destroy()
      }
    }
  }

  /** 打印已经通过等级检查的日志，[config]是[configOf]返回的配置 */
  @PublishedApi
  internal fun publishLog(
    logger: Class<out FLogger>,
    level: FLogLevel,
    mode: FLogMode?,
    msg: String?,
    config: FLoggerConfig?,
  ) {
    if (msg.isNullOrEmpty()) return
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

  /** [level]是否可以打印，[config]是[configOf]返回的配置 */
  @PublishedApi
  internal fun isLoggable(level: FLogLevel, config: FLoggerConfig?): Boolean {
    checkLoggable(level)

    if (_level == FLogLevel.Off) {
      /** 如果全局等级为[FLogLevel.Off]，忽略[FLoggerConfig]，不打印日志 */
      return false
    }

    val limitLevel = config?.level ?: _level
    return level >= limitLevel
  }

  /** [logger]的配置，没有配置返回null */
  @PublishedApi
  internal fun configOf(logger: Class<out FLogger>): FLoggerConfig? {
    checkInit()
    if (_configHolder.isEmpty()) return null
    return _configHolder[logger]
  }

  /** 在调度器上面执行 */
  private fun dispatch(task: Runnable) {
    checkInit()
    _dispatcher.dispatch(task)
  }

  /** 调度器空闲逻辑 */
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

  /** 打印[FLogLevel.Verbose]日志 */
  @JvmStatic
  @JvmOverloads
  fun logV(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Verbose, mode, msg)
  }

  /** 打印[FLogLevel.Debug]日志 */
  @JvmStatic
  @JvmOverloads
  fun logD(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Debug, mode, msg)
  }

  /** 打印[FLogLevel.Info]日志 */
  @JvmStatic
  @JvmOverloads
  fun logI(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Info, mode, msg)
  }

  /** 打印[FLogLevel.Warning]日志 */
  @JvmStatic
  @JvmOverloads
  fun logW(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Warning, mode, msg)
  }

  /** 打印[FLogLevel.Error]日志 */
  @JvmStatic
  @JvmOverloads
  fun logE(
    logger: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(logger, FLogLevel.Error, mode, msg)
  }

  /** 检查等级后打印日志，给Java API使用 */
  private fun log(
    logger: Class<out FLogger>,
    level: FLogLevel,
    mode: FLogMode?,
    msg: String?,
  ) {
    if (msg.isNullOrEmpty()) return
    val config = configOf(logger)
    if (!isLoggable(level, config)) return
    publishLog(logger = logger, level = level, mode = mode, msg = msg, config = config)
  }
}

private fun checkLoggable(level: FLogLevel) {
  require(level != FLogLevel.All && level != FLogLevel.Off) { "Cannot log with level ${level}." }
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