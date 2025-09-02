package com.sd.lib.xlog

/**
 * 打印[FLogLevel.Verbose]日志
 */
inline fun <reified T : FLogger> flogV(block: () -> String) {
  logInternal(T::class.java, FLogLevel.Verbose, block)
}

/**
 * 打印[FLogLevel.Debug]日志
 */
inline fun <reified T : FLogger> flogD(block: () -> String) {
  logInternal(T::class.java, FLogLevel.Debug, block)
}

/**
 * 打印[FLogLevel.Info]日志
 */
inline fun <reified T : FLogger> flogI(block: () -> String) {
  logInternal(T::class.java, FLogLevel.Info, block)
}

/**
 * 打印[FLogLevel.Warning]日志
 */
inline fun <reified T : FLogger> flogW(block: () -> String) {
  logInternal(T::class.java, FLogLevel.Warning, block)
}

/**
 * 打印[FLogLevel.Error]日志
 */
inline fun <reified T : FLogger> flogE(block: () -> String) {
  logInternal(T::class.java, FLogLevel.Error, block)
}

/**
 * 打印控制台日志，不会写入到文件中
 */
inline fun flogConsole(
  tag: String = DefaultDebugTag,
  level: FLogLevel = FLogLevel.Debug,
  block: () -> String,
) {
  with(FLog) {
    if (isLoggableConsole(level)) {
      logConsole(tag = tag, level = level, msg = block())
    }
  }
}

@PublishedApi
internal inline fun <T : FLogger> logInternal(
  clazz: Class<T>,
  level: FLogLevel,
  block: () -> String,
) {
  with(FLog) {
    if (isLoggable(clazz, level)) {
      log(clazz = clazz, level = level, msg = block())
    }
  }
}