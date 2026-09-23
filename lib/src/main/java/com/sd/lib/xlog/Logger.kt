package com.sd.lib.xlog

import android.util.Log

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

/** 默认tag是短类名，匿名类没有短类名，改用去掉包名的类名 */
internal fun Class<out FLogger>.defaultLogTag(): String {
  return simpleName.ifEmpty { name.substringAfterLast('.') }
}

/** 配置信息是否为空 */
internal fun FLoggerConfig.isEmpty(): Boolean {
  return tag.isNullOrEmpty() && level == null && mode == null
}

/** 库内部日志，不受日志等级和模式影响，直接输出到Logcat，tag带上库名前缀便于识别 */
internal fun libLog(msg: String) {
  Log.e("XLogLibLogger", msg)
}