package com.sd.lib.xlog

import android.util.Log

/** 日志标识，一个日志标识代表一类相关的逻辑，默认的tag是子类去掉包名的类名，嵌套类带外部类名，例如`Outer$Inner` */
interface FLogger

/** [FLogger]配置信息 */
data class FLoggerConfig(
  /** 日志tag，为空时使用去掉包名的类名 */
  val tag: String? = null,
  /** 日志等级，覆盖全局等级，但全局等级为[FLogLevel.Off]时一律不打印 */
  val level: FLogLevel? = null,
  /** 日志模式，优先级：调用时传入的模式 > 配置 > 全局设置 */
  val mode: FLogMode? = null,
)

/**
 * 默认tag是去掉包名的类名，嵌套类、局部类和匿名类带外部类名。
 * 不用[Class.getSimpleName]：R8可能移除它依赖的内部类信息，混淆前后的结果不一致。
 */
internal fun Class<out FLogger>.defaultLogTag(): String = name.substringAfterLast('.')

/** 配置信息是否为空 */
internal fun FLoggerConfig.isEmpty(): Boolean {
  return tag.isNullOrEmpty() && level == null && mode == null
}

/** 库内部日志，直接输出到Logcat，只在全局等级为[FLogLevel.Off]时不输出，tag带上库名前缀便于识别 */
internal fun libLog(msg: String) {
  if (FLog.level == FLogLevel.Off) return
  Log.e("XLogLibLogger", msg)
}