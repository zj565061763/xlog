package com.sd.lib.xlog

import android.util.Log
import java.io.File
import java.util.Collections
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
    private val _hasInit = AtomicBoolean(false)

    /** 日志等级 */
    @Volatile
    private var _level: FLogLevel = FLogLevel.All

    /** 是否打印控制台日志 */
    @Volatile
    private var _consoleLogEnabled: Boolean = true

    /** 日志等级是否被[logDirectory]暂时锁定 */
    private var _isLevelLockedByLogDirectory = false
    /** 日志等级被库内部暂时锁定期间，外部暂存的等级 */
    private var _pendingLevel: FLogLevel? = null

    /** 是否子线程发布日志，true-子线程，false-主线程 */
    private var _async: Boolean? = null
        set(value) {
            check(field == null)
            field = value
        }

    /** [FLogger]配置信息 */
    private val _configHolder: MutableMap<Class<out FLogger>, FLoggerConfig> = Collections.synchronizedMap(hashMapOf())

    /** 文件日志 */
    private lateinit var _publisher: DirectoryLogPublisher

    /** 日志调度器 */
    private val _dispatcher: LogDispatcher by lazy {
        LogDispatcher.create(checkNotNull(_async)) {
            // TODO check level
        }
    }

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

        /** 是否子线程发布日志，true-子线程，false-主线程 */
        async: Boolean = false,
    ) {
        if (_hasInit.compareAndSet(false, true)) {
            _async = async
            _publisher = defaultPublisher(
                directory = directory,
                formatter = formatter ?: LogFormatterDefault(),
                storeFactory = storeFactory ?: FLogStore.Factory { defaultLogStore(it) },
                filename = LogFilenameDefault(),
            ).safePublisher()
        }
    }

    /**
     * 是否打打印控制台日志，[debug]方法不受此开关的限制
     */
    @JvmStatic
    fun setConsoleLogEnabled(enabled: Boolean) {
        _consoleLogEnabled = enabled
    }

    /**
     * 限制每天日志文件大小(单位MB)，小于等于0表示不限制，默认不限制
     */
    @JvmStatic
    fun setLimitMBPerDay(limitMBPerDay: Int) {
        checkInit()
        _dispatcher.dispatch {
            _publisher.setLimitPerDay(limitMBPerDay * 1024 * 1024L)
        }
    }

    /**
     * 设置日志等级
     */
    @JvmStatic
    fun setLevel(level: FLogLevel) {
        checkInit()
        _dispatcher.dispatch {
            if (isLevelLocked()) {
                _pendingLevel = level
                return@dispatch
            }
            _level = level
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
        _dispatcher.dispatch {
            val config = _configHolder.getOrPut(clazz) { FLoggerConfig() }
            config.block()
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
        checkInit()
        if (_level == FLogLevel.Off) {
            /**
             * 如果全局等级为[FLogLevel.Off]，则不读取[FLoggerConfig]，不打印日志
             */
            return false
        }

        if (level == FLogLevel.All) return false
        if (level == FLogLevel.Off) return false
        val limitLevel = getConfig(clazz)?.level ?: _level
        return level >= limitLevel
    }

    /**
     * 打印日志
     */
    @JvmStatic
    fun log(clazz: Class<out FLogger>, level: FLogLevel, msg: String?) {
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

        publishConsoleLog(record)

        _dispatcher.dispatch {
            // TODO sync and check level
            _publisher.publish(record)
        }
    }

    /**
     * 发布控制台日志
     */
    private fun publishConsoleLog(record: FLogRecord) {
        if (!_consoleLogEnabled) return
        record.run {
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
        checkInit()
        synchronized(FLog) {
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
        check(_hasInit.get()) { "You should init before this." }
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