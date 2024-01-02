package com.sd.lib.xlog

import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class FLogLevel {
    /** 开启所有日志 */
    All,

    Verbose, Debug, Info, Warning, Error,

    /** 关闭所有日志 */
    Off,
}

object FLog {
    /** 是否已经初始化 */
    @Volatile
    private var _hasInit: Boolean = false
        set(value) {
            require(value)
            field = value
        }

    /** 日志等级 */
    @Volatile
    private var _level: FLogLevel = FLogLevel.All

    /** 是否打印控制台日志 */
    private var _consoleLogEnabled: Boolean = true

    /** 日志等级是否被[logDirectory]暂时锁定 */
    private var _isLevelLockedByLogDirectory = false
    /** 日志等级被库内部暂时锁定期间，外部暂存的等级 */
    private var _pendingLevel: FLogLevel? = null

    /** 是否异步发布日志 */
    private var _async: Boolean = false
    /** 异步发布日志任务 */
    private val _taskHolder: MutableSet<AsyncLogTask> = hashSetOf()

    /** [FLogger]配置信息 */
    private val _configHolder: MutableMap<Class<out FLogger>, FLoggerConfig> = hashMapOf()

    /** 文件日志 */
    private lateinit var _publisher: DirectoryLogPublisher

    /**
     * 初始化
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

        /** 是否异步发布日志 */
        async: Boolean = false,
    ) {
        synchronized(FLog) {
            if (_hasInit) return
            _async = async
            _publisher = defaultPublisher(
                directory = directory,
                formatter = formatter ?: LogFormatterDefault(),
                storeFactory = storeFactory ?: FLogStore.Factory { defaultLogStore(it) },
                filename = LogFilenameDefault(),
            ).safePublisher()
            _hasInit = true

            // 设置默认日志大小
            setLimitMBPerDay(100)
        }
    }

    /**
     * 是否打打印控制台日志，[debug]方法不受此开关的限制
     */
    @JvmStatic
    fun setConsoleLogEnabled(enabled: Boolean) {
        synchronized(FLog) {
            checkInit()
            _consoleLogEnabled = enabled
        }
    }

    /**
     * 限制每天日志文件大小(单位MB)，小于等于0表示不限制大小
     */
    fun setLimitMBPerDay(limitMBPerDay: Int) {
        synchronized(FLog) {
            checkInit()
            _publisher.setLimitPerDay(limitMBPerDay * 1024 * 1024L)
        }
    }

    /**
     * 设置日志等级
     */
    @JvmStatic
    fun setLevel(level: FLogLevel) {
        synchronized(FLog) {
            checkInit()
            if (isLevelLocked()) {
                _pendingLevel = level
                return
            }
            if (_level != level) {
                _level = level
                if (level == FLogLevel.Off) {
                    _publisher.close()
                }
            }
        }
    }

    private fun isLevelLocked(): Boolean {
        return _isLevelLockedByLogDirectory
    }

    private fun restoreLevel(oldLevel: FLogLevel) {
        if (isLevelLocked()) return
        val level = _pendingLevel ?: oldLevel
        setLevel(level)
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
            checkInit()
            val config = _configHolder[clazz] ?: FLoggerConfig().also {
                _configHolder[clazz] = it
            }
            block(config)
            if (config.isEmpty()) {
                _configHolder.remove(clazz)
            }
        }
    }

    private fun getConfig(clazz: Class<out FLogger>): FLoggerConfig? {
        return _configHolder[clazz]
    }

    @PublishedApi
    internal fun isLoggable(clazz: Class<out FLogger>, level: FLogLevel): Boolean {
        synchronized(FLog) {
            checkInit()
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

            if (_consoleLogEnabled) {
                when (record.level) {
                    FLogLevel.Verbose -> Log.v(tag, msg)
                    FLogLevel.Debug -> Log.d(tag, msg)
                    FLogLevel.Info -> Log.i(tag, msg)
                    FLogLevel.Warning -> Log.w(tag, msg)
                    FLogLevel.Error -> Log.e(tag, msg)
                    else -> {}
                }
            }

            if (_async || _taskHolder.isNotEmpty()) {
                logAsync(record)
            } else {
                _publisher.publish(record)
            }
        }
    }

    private fun logAsync(record: FLogRecord) {
        object : AsyncLogTask(_publisher, record) {
            override fun onFinish(publisher: LogPublisher) {
                val task = this
                _taskHolder.remove(task)
                if (_taskHolder.isEmpty() && _level == FLogLevel.Off) {
                    check(publisher === _publisher)
                    publisher.close()
                }
            }
        }.let { task ->
            // submit
            _taskHolder.add(task)
            task.submit()
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

            val filename = _publisher.filename
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
    fun <T> logDirectory(block: (File) -> T): T {
        synchronized(FLog) {
            checkInit()
            val oldLevel = _level
            setLevel(FLogLevel.Off)
            _isLevelLockedByLogDirectory = true
            return try {
                block(_publisher.directory)
            } finally {
                _isLevelLockedByLogDirectory = false
                restoreLevel(oldLevel)
            }
        }
    }

    //---------- other ----------

    @PublishedApi
    internal fun isLoggableConsoleDebug(): Boolean {
        checkInit()
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

    private fun checkInit() {
        check(_hasInit) { "You should init before this." }
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

private val LogExecutor by lazy { Executors.newSingleThreadExecutor() }

private abstract class AsyncLogTask(
    private val publisher: LogPublisher,
    private val record: FLogRecord,
) : Runnable {
    private val _hasRun = AtomicBoolean(false)

    fun submit() {
        LogExecutor.submit(this)
    }

    override fun run() {
        if (_hasRun.compareAndSet(false, true)) {
            synchronized(FLog) {
                publisher.publish(record)
                onFinish(publisher)
            }
        }
    }

    abstract fun onFinish(publisher: LogPublisher)
}