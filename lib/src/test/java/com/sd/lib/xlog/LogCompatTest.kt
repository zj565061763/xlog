package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 使用方编译时内联进去的代码会调用这些方法，删除或改签名会导致这些代码运行时崩溃 */
class LogCompatTest {
  /** 2.0.0及之前版本的内联代码调用 */
  @Test
  fun testIsLoggable() {
    val method = flogMethod("isLoggable", Class::class.java, FLogLevel::class.java)
    assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
  }

  /** 2.0.0及之前版本的内联代码调用 */
  @Test
  fun testLog() {
    val method = flogMethod(
      "log",
      Class::class.java,
      FLogLevel::class.java,
      FLogMode::class.java,
      String::class.java,
    )
    assertEquals(Void.TYPE, method.returnType)
  }

  /** 2.1.0的内联代码调用 */
  @Test
  fun testConfigOf() {
    val method = flogMethod("configOf", Class::class.java)
    assertEquals(FLoggerConfig::class.java, method.returnType)
  }

  /** 2.1.0的内联代码调用 */
  @Test
  fun testIsLoggableWithConfig() {
    val method = flogMethod("isLoggable", FLogLevel::class.java, FLoggerConfig::class.java)
    assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
  }

  /** 2.1.0的内联代码调用 */
  @Test
  fun testPublishLog() {
    val method = flogMethod(
      "publishLog",
      Class::class.java,
      FLogLevel::class.java,
      FLogMode::class.java,
      String::class.java,
      FLoggerConfig::class.java,
    )
    assertEquals(Void.TYPE, method.returnType)
  }
}

/** [FLog]上供内联代码调用的方法，内联代码通过`FLog.INSTANCE`调用，所以不能是静态方法 */
private fun flogMethod(name: String, vararg parameterTypes: Class<*>): Method {
  return FLog::class.java.getMethod(name, *parameterTypes).also {
    assertFalse(Modifier.isStatic(it.modifiers))
  }
}
