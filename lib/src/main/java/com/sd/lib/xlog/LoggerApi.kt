package com.sd.lib.xlog

/**
 * 打印[FLogLevel.Verbose]日志
 */
inline fun FLogger.lv(block: () -> Any) {
    logInternal(javaClass, FLogLevel.Verbose, block)
}

/**
 * 打印[FLogLevel.Debug]日志
 */
inline fun FLogger.ld(block: () -> Any) {
    logInternal(javaClass, FLogLevel.Debug, block)
}

/**
 * 打印[FLogLevel.Info]日志
 */
inline fun FLogger.li(block: () -> Any) {
    logInternal(javaClass, FLogLevel.Info, block)
}

/**
 * 打印[FLogLevel.Warning]日志
 */
inline fun FLogger.lw(block: () -> Any) {
    logInternal(javaClass, FLogLevel.Warning, block)
}

/**
 * 打印[FLogLevel.Error]日志
 */
inline fun FLogger.le(block: () -> Any) {
    logInternal(javaClass, FLogLevel.Error, block)
}