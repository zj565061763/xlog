package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Test

/** [FLogger]的默认tag */
class LoggerTest {
  /** 默认tag是短类名 */
  @Test
  fun testDefaultLogTag() {
    assertEquals("TestLogger", TestLogger::class.java.defaultLogTag())
  }

  /** 匿名类没有短类名，默认tag是去掉包名的类名 */
  @Test
  fun testAnonymousDefaultLogTag() {
    val logger = object : FLogger {}
    assertEquals("", logger.javaClass.simpleName)
    assertEquals("LoggerTest\$testAnonymousDefaultLogTag\$logger\$1", logger.javaClass.defaultLogTag())
  }

  /** 嵌套类的默认tag不带外部类名 */
  @Test
  fun testNestedDefaultLogTag() {
    assertEquals("NestedLogger", TestOuter.NestedLogger::class.java.defaultLogTag())
    assertEquals("DeepLogger", TestOuter.Inner.DeepLogger::class.java.defaultLogTag())
  }

  /** 局部类的默认tag不带外部类名和函数名 */
  @Test
  fun testLocalDefaultLogTag() {
    class LocalLogger : FLogger
    assertEquals("LocalLogger", LocalLogger::class.java.defaultLogTag())
  }

  /** Java编译器生成的局部类名带数字前缀，匿名类名只有数字 */
  @Test
  fun testJavaDefaultLogTag() {
    assertEquals("LocalLogger", JavaLoggers.localLogger().defaultLogTag())
    assertEquals("JavaLoggers\$1", JavaLoggers.anonymousLogger().defaultLogTag())
  }
}

private interface TestLogger : FLogger

private class TestOuter {
  interface NestedLogger : FLogger

  class Inner {
    interface DeepLogger : FLogger
  }
}
