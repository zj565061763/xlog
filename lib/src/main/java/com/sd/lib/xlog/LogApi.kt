package com.sd.lib.xlog

/**
 * 打印[FLogLevel.Verbose]日志
 */
inline fun <reified T : FLogger> flogV(
  mode: FLogMode? = null,
  block: () -> String,
) {
  flog<T>(FLogLevel.Verbose, mode, block)
}

/**
 * 打印[FLogLevel.Debug]日志
 */
inline fun <reified T : FLogger> flogD(
  mode: FLogMode? = null,
  block: () -> String,
) {
  flog<T>(FLogLevel.Debug, mode, block)
}

/**
 * 打印[FLogLevel.Info]日志
 */
inline fun <reified T : FLogger> flogI(
  mode: FLogMode? = null,
  block: () -> String,
) {
  flog<T>(FLogLevel.Info, mode, block)
}

/**
 * 打印[FLogLevel.Warning]日志
 */
inline fun <reified T : FLogger> flogW(
  mode: FLogMode? = null,
  block: () -> String,
) {
  flog<T>(FLogLevel.Warning, mode, block)
}

/**
 * 打印[FLogLevel.Error]日志
 */
inline fun <reified T : FLogger> flogE(
  mode: FLogMode? = null,
  block: () -> String,
) {
  flog<T>(FLogLevel.Error, mode, block)
}

/**
 * 打印日志
 */
inline fun <reified T : FLogger> flog(
  level: FLogLevel,
  mode: FLogMode? = null,
  block: () -> String,
) {
  logInternal(T::class.java, level, mode, block)
}

@PublishedApi
internal inline fun <T : FLogger> logInternal(
  clazz: Class<T>,
  level: FLogLevel,
  mode: FLogMode?,
  block: () -> String,
) {
  with(FLog) {
    if (isLoggable(clazz, level)) {
      log(clazz = clazz, level = level, mode = mode, msg = block())
    }
  }
}