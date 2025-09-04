package com.sd.lib.xlog

/**
 * 打印[FLogLevel.Verbose]日志
 */
inline fun FLogger.lv(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Verbose, mode, block)
}

/**
 * 打印[FLogLevel.Debug]日志
 */
inline fun FLogger.ld(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Debug, mode, block)
}

/**
 * 打印[FLogLevel.Info]日志
 */
inline fun FLogger.li(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Info, mode, block)
}

/**
 * 打印[FLogLevel.Warning]日志
 */
inline fun FLogger.lw(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Warning, mode, block)
}

/**
 * 打印[FLogLevel.Error]日志
 */
inline fun FLogger.le(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Error, mode, block)
}

/**
 * 打印日志
 */
inline fun FLogger.l(
  level: FLogLevel,
  mode: FLogMode? = null,
  block: () -> String,
) {
  logInternal(javaClass, level, mode, block)
}