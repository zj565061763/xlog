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
  private var _level = FLogLevel.All

  /** 日志模式 */
  @Volatile
  private var _mode: FLogMode = FLogMode.Default

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
   * 修改[FLogger]配置信息
   */
  inline fun <reified T : FLogger> config(noinline block: (FLoggerConfig) -> FLoggerConfig) {
    config(T::class.java, block)
  }

  /**
   * 修改[FLogger]配置信息
   */
  @JvmStatic
  fun config(clazz: Class<out FLogger>, block: (FLoggerConfig) -> FLoggerConfig) {
    checkInit()
    synchronized(FLog) {
      val holder = _configHolder ?: mutableMapOf<Class<out FLogger>, FLoggerConfig>().also { _configHolder = it }
      val config = block(holder.getOrPut(clazz) { FLoggerConfig() })
      if (config.isEmpty()) {
        holder.remove(clazz)
        if (holder.isEmpty()) _configHolder = null
      } else {
        holder[clazz] = config
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
  internal fun log(
    clazz: Class<out FLogger>,
    level: FLogLevel,
    mode: FLogMode?,
    msg: String?,
  ) {
    synchronized(FLog) {
      if (msg.isNullOrEmpty()) return
      if (!isLoggable(clazz, level)) return
      newLogRecord(
        logger = clazz,
        tag = (getConfig(clazz)?.tag ?: "").ifEmpty { clazz.simpleName },
        msg = msg,
        level = level,
      )
    }.also { record ->
      when (mode ?: _mode) {
        FLogMode.Default -> {
          record.consoleLog()
          dispatch { _publisher.publish(record) }
        }
        FLogMode.Console -> {
          record.consoleLog()
        }
        FLogMode.Store -> {
          dispatch { _publisher.publish(record) }
        }
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

  /**
   * 打印[FLogLevel.Verbose]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logV(
    clazz: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(clazz, FLogLevel.Verbose, mode, msg)
  }

  /**
   * 打印[FLogLevel.Debug]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logD(
    clazz: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(clazz, FLogLevel.Debug, mode, msg)
  }

  /**
   * 打印[FLogLevel.Info]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logI(
    clazz: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(clazz, FLogLevel.Info, mode, msg)
  }

  /**
   * 打印[FLogLevel.Warning]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logW(
    clazz: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(clazz, FLogLevel.Warning, mode, msg)
  }

  /**
   * 打印[FLogLevel.Error]日志
   */
  @JvmStatic
  @JvmOverloads
  fun logE(
    clazz: Class<out FLogger>,
    mode: FLogMode? = null,
    msg: String?,
  ) {
    log(clazz, FLogLevel.Error, mode, msg)
  }
}

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