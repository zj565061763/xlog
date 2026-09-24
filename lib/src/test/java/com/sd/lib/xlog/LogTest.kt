package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** [FLog]不需要初始化就能测的行为，JVM单元测试里没有Context，[FLog]始终是未初始化状态 */
class LogTest {
  /** 未初始化时调用抛[IllegalStateException]，提示先初始化 */
  @Test
  fun testNotInit() {
    val thrown = assertThrows(IllegalStateException::class.java) { flogI<LogTestLogger> { "msg" } }
    assertEquals("You should init before this.", thrown.message)

    assertThrows(IllegalStateException::class.java) { FLog.logI(LogTestLogger::class.java, msg = "msg") }
    assertThrows(IllegalStateException::class.java) { FLog.setLevel(FLogLevel.Info) }
    assertThrows(IllegalStateException::class.java) { FLog.setMode(FLogMode.Console) }
    assertThrows(IllegalStateException::class.java) { FLog.setMaxMBPerDay(1) }
    assertThrows(IllegalStateException::class.java) { FLog.deleteLog(1) }
    assertThrows(IllegalStateException::class.java) { FLog.logDirectory { } }
  }

  /** 用[FLogLevel.All]或[FLogLevel.Off]打印日志时抛[IllegalArgumentException] */
  @Test
  fun testIllegalLevel() {
    assertThrows(IllegalArgumentException::class.java) { FLog.isLoggable(FLogLevel.All, null) }
    assertThrows(IllegalArgumentException::class.java) { FLog.isLoggable(FLogLevel.Off, null) }
  }
}

private interface LogTestLogger : FLogger
