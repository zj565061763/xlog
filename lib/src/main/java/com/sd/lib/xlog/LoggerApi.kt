package com.sd.lib.xlog

/**
 * 打印[FLogLevel.Verbose]日志
 */
inline fun FLogger.lv(block: () -> String) {
  logInternal(javaClass, FLogLevel.Verbose, block)
}

/**
 * 打印[FLogLevel.Debug]日志
 */
inline fun FLogger.ld(block: () -> String) {
  logInternal(javaClass, FLogLevel.Debug, block)
}

/**
 * 打印[FLogLevel.Info]日志
 */
inline fun FLogger.li(block: () -> String) {
  logInternal(javaClass, FLogLevel.Info, block)
}

/**
 * 打印[FLogLevel.Warning]日志
 */
inline fun FLogger.lw(block: () -> String) {
  logInternal(javaClass, FLogLevel.Warning, block)
}

/**
 * 打印[FLogLevel.Error]日志
 */
inline fun FLogger.le(block: () -> String) {
  logInternal(javaClass, FLogLevel.Error, block)
}

/**
 * 打印控制台日志，不会写入到文件中
 */
inline fun FLogger.logConsole(
  level: FLogLevel = FLogLevel.Debug,
  block: () -> String,
) {
  flogConsole(
    tag = javaClass.simpleName,
    level = level,
    block = block,
  )
}