package com.sd.test.xlog

import com.sd.lib.xlog.FLogMode
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.li

/** 混淆后的日志调用入口，测试APK不直接引用logger类型 */
object MinifiedLoggers {
  /** 输出顶层、嵌套、局部和匿名类型的日志 */
  @JvmStatic
  fun write() {
    class LocalLogger : FLogger
    flogI<TopLevelLogger>(FLogMode.Store) { "top" }
    flogI<LoggerOuter.NestedLogger>(FLogMode.Store) { "nested" }
    flogI<LocalLogger>(FLogMode.Store) { "local" }
    val anonymous = object : FLogger {}
    anonymous.li(FLogMode.Store) { "anonymous" }
  }
}

private interface TopLevelLogger : FLogger

private class LoggerOuter {
  interface NestedLogger : FLogger
}

private interface UnusedLogger : FLogger
