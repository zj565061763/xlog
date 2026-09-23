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
}

private interface TestLogger : FLogger
