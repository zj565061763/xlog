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

/**
 * 默认tag是短类名，匿名类没有短类名，改用去掉包名的类名。
 * 从类名推算，不用[Class.getSimpleName]：R8可能移除它依赖的内部类信息，嵌套类的tag会带上外部类名。
 */
internal fun Class<out FLogger>.defaultLogTag(): String {
  val className = name.substringAfterLast('.')
  // 嵌套类、局部类取最后一个$之后的部分，Java局部类带数字前缀，匿名类只有数字
  return className.substringAfterLast('$').trimStart { it in '0'..'9' }.ifEmpty { className }
}

/** 配置信息是否为空 */
internal fun FLoggerConfig.isEmpty(): Boolean {
  return tag.isNullOrEmpty() && level == null && mode == null
}

/** 库内部日志，直接输出到Logcat，只在全局等级为[FLogLevel.Off]时不输出，tag带上库名前缀便于识别 */
internal fun libLog(msg: String) {
  if (FLog.level == FLogLevel.Off) return
  Log.e("XLogLibLogger", msg)
}