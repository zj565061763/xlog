package com.sd.lib.xlog

/**
 * 打印[FLogLevel.Verbose]日志
 */
inline fun <reified T : FLogger> flogV(block: () -> Any) {
    logInternal(T::class.java, FLogLevel.Verbose, block)
}

/**
 * 打印[FLogLevel.Debug]日志
 */
inline fun <reified T : FLogger> flogD(block: () -> Any) {
    logInternal(T::class.java, FLogLevel.Debug, block)
}

/**
 * 打印[FLogLevel.Info]日志
 */
inline fun <reified T : FLogger> flogI(block: () -> Any) {
    logInternal(T::class.java, FLogLevel.Info, block)
}

/**
 * 打印[FLogLevel.Warning]日志
 */
inline fun <reified T : FLogger> flogW(block: () -> Any) {
    logInternal(T::class.java, FLogLevel.Warning, block)
}

/**
 * 打印[FLogLevel.Error]日志
 */
inline fun <reified T : FLogger> flogE(block: () -> Any) {
    logInternal(T::class.java, FLogLevel.Error, block)
}

@PublishedApi
internal inline fun <T : FLogger> logInternal(
    clazz: Class<T>,
    level: FLogLevel,
    block: () -> Any,
) {
    with(FLog) {
        if (isLoggable(clazz, level)) {
            log(clazz, level, msg = block().toString())
        }
    }
}

/**
 * [FLog.debug]
 */
inline fun fDebug(
    level: FLogLevel = FLogLevel.Debug,
    block: () -> Any,
) {
    with(FLog) {
        if (isLoggableConsoleDebug(level)) {
            debug(level, msg = block().toString())
        }
    }
}