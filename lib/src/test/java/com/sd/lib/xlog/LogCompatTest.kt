package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 旧版本编译的使用方代码会调用这些方法，删除或改签名会导致这些代码运行时崩溃 */
class LogCompatTest {
  /** 1.6.0到2.0.0版本的内联代码调用 */
  @Test
  fun testIsLoggable() {
    val method = flogMethod("isLoggable", Class::class.java, FLogLevel::class.java)
    assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
  }

  /** 1.6.0到2.0.0版本的内联代码调用 */
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

  /** 2.1.0及之后版本的内联代码调用 */
  @Test
  fun testConfigOf() {
    val method = flogMethod("configOf", Class::class.java)
    assertEquals(FLoggerConfig::class.java, method.returnType)
  }

  /** 2.1.0及之后版本的内联代码调用 */
  @Test
  fun testIsLoggableWithConfig() {
    val method = flogMethod("isLoggable", FLogLevel::class.java, FLoggerConfig::class.java)
    assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
  }

  /** 2.1.0及之后版本的内联代码调用 */
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

  /**
   * 2.2.0新增的[FLogFormatter.reset]是Java默认方法，没有实现它的旧实现类调用时不抛[AbstractMethodError]。
   * 同时保留DefaultImpls，2.2.0编译的Kotlin实现类会调用它。
   */
  @Test
  fun testFormatterReset() {
    assertTrue(FLogFormatter::class.java.getMethod("reset").isDefault)
    Class.forName("${FLogFormatter::class.java.name}\$DefaultImpls").getMethod("reset", FLogFormatter::class.java)
    JavaFormatter().reset()
  }
}

/** [FLog]上供内联代码调用的方法，内联代码通过`FLog.INSTANCE`调用，所以不能是静态方法 */
private fun flogMethod(name: String, vararg parameterTypes: Class<*>): Method {
  return FLog::class.java.getMethod(name, *parameterTypes).also {
    assertFalse(Modifier.isStatic(it.modifiers))
  }
}
