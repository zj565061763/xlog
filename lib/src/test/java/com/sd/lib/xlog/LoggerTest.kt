package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Test

/** [FLogger]的默认tag */
class LoggerTest {
  /** 顶层类的默认tag是类名 */
  @Test
  fun testDefaultLogTag() {
    assertEquals("TestLogger", TestLogger::class.java.defaultLogTag())
  }

  /** 匿名类的默认tag带外部类名、函数名和编译器生成的编号 */
  @Test
  fun testAnonymousDefaultLogTag() {
    val logger = object : FLogger {}
    assertEquals("LoggerTest\$testAnonymousDefaultLogTag\$logger\$1", logger.javaClass.defaultLogTag())
  }

  /** 嵌套类的默认tag带外部类名 */
  @Test
  fun testNestedDefaultLogTag() {
    assertEquals("TestOuter\$NestedLogger", TestOuter.NestedLogger::class.java.defaultLogTag())
    assertEquals("TestOuter\$Inner\$DeepLogger", TestOuter.Inner.DeepLogger::class.java.defaultLogTag())
  }

  /** 局部类的默认tag带外部类名和函数名 */
  @Test
  fun testLocalDefaultLogTag() {
    class LocalLogger : FLogger
    assertEquals("LoggerTest\$testLocalDefaultLogTag\$LocalLogger", LocalLogger::class.java.defaultLogTag())
  }

  /** Java编译器生成的局部类名带数字编号，匿名类名只有编号，都原样保留 */
  @Test
  fun testJavaDefaultLogTag() {
    assertEquals("JavaLoggers\$1LocalLogger", JavaLoggers.localLogger().defaultLogTag())
    assertEquals("JavaLoggers\$1", JavaLoggers.anonymousLogger().defaultLogTag())
  }

  /** 类名开头的数字是名字的一部分，不能去掉，否则和去掉数字后同名的类型分不开 */
  @Test
  fun testDigitDefaultLogTag() {
    assertEquals("123Logger", `123Logger`::class.java.defaultLogTag())
    assertEquals("TestOuter\$456Logger", TestOuter.`456Logger`::class.java.defaultLogTag())
  }
}

private interface TestLogger : FLogger

private interface `123Logger` : FLogger

private class TestOuter {
  interface NestedLogger : FLogger

  interface `456Logger` : FLogger

  class Inner {
    interface DeepLogger : FLogger
  }
}
