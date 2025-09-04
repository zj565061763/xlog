package com.sd.lib.xlog

/**
 * 日志标识，一个日志标识代表一类相关的逻辑，默认的tag是子类的短类名
 */
interface FLogger

/**
 * [FLogger]配置信息
 */
data class FLoggerConfig(
  /** 日志标识 */
  val tag: String? = null,
  /** 日志等级 */
  val level: FLogLevel? = null,
  /** 日志模式 */
  val mode: FLogMode? = null,
)

/**
 * 配置信息是否为空
 */
internal fun FLoggerConfig.isEmpty(): Boolean {
  return tag.isNullOrEmpty() && level == null && mode == null
}

internal inline fun libLog(block: () -> String) {
  flogV<FLogLibLogger>(mode = FLogMode.Console, block = block)
}

internal class FLogLibLogger : FLogger