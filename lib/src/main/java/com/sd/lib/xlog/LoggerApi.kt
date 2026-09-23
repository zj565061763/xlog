package com.sd.lib.xlog

/** 打印[FLogLevel.Verbose]日志，日志标识的规则见[l] */
inline fun FLogger.lv(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Verbose, mode, block)
}

/** 打印[FLogLevel.Debug]日志，日志标识的规则见[l] */
inline fun FLogger.ld(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Debug, mode, block)
}

/** 打印[FLogLevel.Info]日志，日志标识的规则见[l] */
inline fun FLogger.li(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Info, mode, block)
}

/** 打印[FLogLevel.Warning]日志，日志标识的规则见[l] */
inline fun FLogger.lw(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Warning, mode, block)
}

/** 打印[FLogLevel.Error]日志，日志标识的规则见[l] */
inline fun FLogger.le(
  mode: FLogMode? = null,
  block: () -> String,
) {
  l(FLogLevel.Error, mode, block)
}

/**
 * 打印日志，[level]为[FLogLevel.All]或[FLogLevel.Off]时抛出[IllegalArgumentException]。
 *
 * 日志标识是接收者的实际类型，而不是它实现的[FLogger]子接口，
 * 所以默认tag是实际类型的短类名，[FLogInitScope.configLogger]也要按实际类型配置。
 */
inline fun FLogger.l(
  level: FLogLevel,
  mode: FLogMode? = null,
  block: () -> String,
) {
  logInternal(javaClass, level, mode, block)
}