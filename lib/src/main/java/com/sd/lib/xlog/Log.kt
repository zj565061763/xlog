package com.sd.lib.xlog

import android.util.Log
import java.io.File

enum class FLogLevel {
    All, Verbose, Debug, Info, Warning, Error, Off,
}

object FLog {
    /** 日志等级 */
    @Volatile
    private var _level: FLogLevel = FLogLevel.Off

    /** 日志文件目录 */
    private var _logDirectory: File? = null

    /** 日志写入执行器 */
    private var _executor: FLogExecutor? = null

    /** 文件日志 */
    @Volatile
    private var _publisher: DirectoryLogPublisher? = null

    /** 是否打印控制台日志 */
    private var _enableConsoleLog: Boolean = false

    /** [FLogger]配置信息 */
    private val _configHolder: MutableMap<Class<out FLogger>, FLoggerConfig> = hashMapOf()

    /**
     * 日志是否已经打开
     */
    @JvmStatic
    fun isOpened(): Boolean {
        return _publisher != null
    }

    /**
     * 打开日志，默认只打开文件日志，可以调用[enableConsoleLog]方法开关控制台日志
     */
    @JvmStatic
    @JvmOverloads
    fun open(
        /** 日志等级 */
        level: FLogLevel,

        /** 日志文件目录 */
        directory: File,

        /** 限制每天日志文件大小(单位MB)，小于等于0表示不限制大小 */
        limitMBPerDay: Long,

        /** 日志格式化 */
        formatter: FLogFormatter? = null,

        /** 日志仓库工厂 */
        storeFactory: FLogStore.Factory? = null,

        /** 日志执行器，可以定义执行线程，包括日志的格式化和写入，默认在调用线程执行 */
        executor: FLogExecutor? = null,
    ) {
        synchronized(FLog) {
            if (isOpened()) return
            if (level == FLogLevel.Off) error("level off")
            _level = level
            _logDirectory = directory
            _executor = executor
            _publisher = LogPublisherImpl(
                directory = directory,
                limitMBPerDay = limitMBPerDay,
                formatter = formatter ?: LogFormatterDefault(),
                filename = LogFilenameDefault(),
                storeFactory = storeFactory ?: FLogStore.Factory { defaultLogStore(it) },
            ).safePublisher()
        }
    }

    /**
     * 是否打打印控制台日志，[debug]方法不受此开关的限制
     */
    @JvmStatic
    fun enableConsoleLog(enable: Boolean) {
        synchronized(FLog) {
            if (isOpened()) {
                _enableConsoleLog = enable
            }
        }
    }

    /**
     * 设置日志等级
     */
    @JvmStatic
    fun setLevel(level: FLogLevel) {
        synchronized(FLog) {
            if (isOpened()) {
                _level = level
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
        synchronized(FLog) {
            if (isOpened()) {
                val config = _configHolder[clazz] ?: FLoggerConfig().also {
                    _configHolder[clazz] = it
                }
                block(config)
                if (config.isEmpty()) {
                    _configHolder.remove(clazz)
                }
            }
        }
    }

    private fun getConfig(clazz: Class<out FLogger>): FLoggerConfig? {
        return _configHolder[clazz]
    }

    @PublishedApi
    internal fun isLoggable(clazz: Class<out FLogger>, level: FLogLevel): Boolean {
        synchronized(FLog) {
            if (!isOpened()) return false
            if (level == FLogLevel.All) return false
            if (level == FLogLevel.Off) return false
            val limitLevel = getConfig(clazz)?.level ?: _level
            return level >= limitLevel
        }
    }

    /**
     * 打印日志
     */
    @JvmStatic
    fun log(clazz: Class<out FLogger>, level: FLogLevel, msg: String?) {
        synchronized(FLog) {
            if (msg.isNullOrEmpty()) return
            if (!isLoggable(clazz, level)) return

            val config = getConfig(clazz)
            val tag = config?.tag?.takeIf { it.isNotEmpty() } ?: clazz.simpleName

            val record = newLogRecord(
                logger = clazz,
                tag = tag,
                msg = msg,
                level = level,
            )

            val publisher = checkNotNull(_publisher)

            val executor = _executor
            if (executor == null) {
                publisher.publish(record)
            } else {
                executor.submit { publisher.publish(record) }
            }

            if (_enableConsoleLog) {
                when (record.level) {
                    FLogLevel.Verbose -> Log.v(tag, msg)
                    FLogLevel.Debug -> Log.d(tag, msg)
                    FLogLevel.Info -> Log.i(tag, msg)
                    FLogLevel.Warning -> Log.w(tag, msg)
                    FLogLevel.Error -> Log.e(tag, msg)
                    else -> {}
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
            if (saveDays <= 0) dir.deleteRecursively()
            val files = dir.listFiles()
            if (files.isNullOrEmpty()) return@logDirectory

            val filename = checkNotNull(_publisher).filename
            val today = filename.filenameOf(System.currentTimeMillis()).also { check(it.isNotEmpty()) }
            files.forEach { file ->
                val diffDays = filename.diffDays(today, file.name)
                if (diffDays == null || diffDays > (saveDays - 1)) {
                    file.deleteRecursively()
                }
            }
        }
    }

    /**
     * 访问日志文件目录，由于在[block]中可能操作文件，所以在[block]执行期间，无法写入日志
     */
    @JvmStatic
    fun <T> logDirectory(block: (File) -> T): T? {
        synchronized(FLog) {
            return if (isOpened()) {
                val oldLevel = _level
                _level = FLogLevel.Off
                _publisher?.close()
                val result = libTryRun { block(checkNotNull(_logDirectory)) }
                _level = oldLevel
                result.getOrElse { null }
            } else {
                null
            }
        }
    }

    /**
     * 关闭日志
     */
    @JvmStatic
    fun close() {
        synchronized(FLog) {
            _level = FLogLevel.Off
            _logDirectory = null
            _enableConsoleLog = false
            _configHolder.clear()
            _publisher?.close()
            _publisher = null
            _executor?.close()
            _executor = null
        }
    }

    //---------- other ----------

    @PublishedApi
    internal fun isLoggableConsoleDebug(): Boolean {
        if (!isOpened()) return false
        return FLogLevel.Debug >= _level
    }

    /**
     * 打印控制台日志，不会写入到文件中，tag：DebugLogger
     */
    @JvmStatic
    fun debug(msg: String?) {
        if (msg.isNullOrEmpty()) return
        if (isLoggableConsoleDebug()) {
            Log.d("DebugLogger", msg)
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