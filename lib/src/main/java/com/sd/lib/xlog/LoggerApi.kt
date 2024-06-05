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

/**
 * 打印控制台日志，不会写入到文件中
 */
inline fun FLogger.debug(
    level: FLogLevel = FLogLevel.Debug,
    block: () -> Any,
) {
    fDebug(
        tag = javaClass.simpleName,
        level = level,
        block = block,
    )
}