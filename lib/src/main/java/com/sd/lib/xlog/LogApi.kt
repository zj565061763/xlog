package com.sd.lib.xlog

/**
 * 打印[FLogLevel.Verbose]日志
 */
inline fun <reified T : FLogger> flogV(block: () -> Any) {
    logInternal<T>(FLogLevel.Verbose, block)
}

/**
 * 打印[FLogLevel.Debug]日志
 */
inline fun <reified T : FLogger> flogD(block: () -> Any) {
    logInternal<T>(FLogLevel.Debug, block)
}

/**
 * 打印[FLogLevel.Info]日志
 */
inline fun <reified T : FLogger> flogI(block: () -> Any) {
    logInternal<T>(FLogLevel.Info, block)
}

/**
 * 打印[FLogLevel.Warning]日志
 */
inline fun <reified T : FLogger> flogW(block: () -> Any) {
    logInternal<T>(FLogLevel.Warning, block)
}

/**
 * 打印[FLogLevel.Error]日志
 */
inline fun <reified T : FLogger> flogE(block: () -> Any) {
    logInternal<T>(FLogLevel.Error, block)
}

@PublishedApi
internal inline fun <reified T : FLogger> logInternal(level: FLogLevel, block: () -> Any) {
    val clazz = T::class.java
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