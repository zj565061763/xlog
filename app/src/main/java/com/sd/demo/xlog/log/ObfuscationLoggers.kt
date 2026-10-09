package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.li

/**
 * 混淆测试用：这个类没有被keep（见proguard-minified-rules.pro），R8会重命名、内联或移除它。
 * 嵌套类、局部类和匿名类的默认tag要和未混淆时一致，由LogObfuscationTest验证。
 */
class ObfuscationLoggers {
  interface NestedLogger : FLogger

  /** 依次用嵌套类、局部类和匿名类打印日志，消息分别是nested、local和anonymous */
  fun log() {
    class LocalLogger : FLogger

    flogI<NestedLogger> { "nested" }
    LocalLogger().li { "local" }
    object : FLogger {}.li { "anonymous" }
  }
}

/** 测试只能通过这个被keep的入口调用[ObfuscationLoggers]，直接引用的话它的名字会被保留 */
fun logObfuscation() {
  ObfuscationLoggers().log()
}
