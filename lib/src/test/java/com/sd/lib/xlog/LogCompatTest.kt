package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.0.0及之前版本编译的内联代码会调用这些方法，删除或改签名会导致旧代码运行时崩溃 */
class LogCompatTest {
  @Test
  fun testIsLoggable() {
    val method = FLog::class.java.getMethod("isLoggable", Class::class.java, FLogLevel::class.java)
    assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
  }

  @Test
  fun testLog() {
    val method = FLog::class.java.getMethod(
      "log",
      Class::class.java,
      FLogLevel::class.java,
      FLogMode::class.java,
      String::class.java,
    )
    assertEquals(Void.TYPE, method.returnType)
  }
}
