package com.sd.lib.xlog

/** 日志标识，一个日志标识代表一类相关的逻辑，默认的tag是子类的短类名 */
interface FLogger

/** [FLogger]配置信息 */
data class FLoggerConfig(
  /** 日志tag，为空时使用短类名 */
  val tag: String? = null,
  /** 日志等级，覆盖全局等级，但全局等级为[FLogLevel.Off]时一律不打印 */
  val level: FLogLevel? = null,
  /** 日志模式，优先级：调用时传入的模式 > 配置 > 全局设置 */
  val mode: FLogMode? = null,
)

/** 配置信息是否为空 */
internal fun FLoggerConfig.isEmpty(): Boolean {
  return tag.isNullOrEmpty() && level == null && mode == null
}

internal inline fun libLog(block: () -> String) {
  flogV<XLogLibLogger>(mode = FLogMode.Console, block = block)
}

/** 库内部日志的标识，类名就是Logcat里的tag，所以带上库名前缀 */
internal class XLogLibLogger : FLogger