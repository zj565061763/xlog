package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [FLog]不需要初始化就能测的行为，以及[processOf]、[processOfCmdline]。
 * JVM单元测试里没有Context，[FLog]始终是未初始化状态。
 */
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

  /** 系统接口取到时直接用，不读取cmdline */
  @Test
  fun testProcessOfSystem() {
    var cmdlineCount = 0
    assertEquals("com.sd.demo", processOf(system = { "com.sd.demo" }, cmdline = { cmdlineCount++; "" }))
    assertEquals(0, cmdlineCount)
  }

  /** 系统接口出错或为空时，从cmdline解析进程名 */
  @Test
  fun testProcessOfCmdlineFallback() {
    val cmdline = { "com.sd.demo:remote\u0000" }
    assertEquals("com.sd.demo:remote", processOf(system = { error("system error") }, cmdline = cmdline))
    assertEquals("com.sd.demo:remote", processOf(system = { "" }, cmdline = cmdline))
    assertEquals("com.sd.demo:remote", processOf(system = { null }, cmdline = cmdline))
  }

  /** 系统接口和cmdline都取不到时返回null，不抛异常 */
  @Test
  fun testProcessOfNull() {
    assertNull(processOf(system = { error("system error") }, cmdline = { error("cmdline error") }))
    assertNull(processOf(system = { null }, cmdline = { "" }))
  }

  /** 各参数以'\u0000'分隔，第一个是进程名 */
  @Test
  fun testProcessOfCmdline() {
    assertEquals("com.sd.demo.xlog", processOfCmdline("com.sd.demo.xlog\u0000"))
    assertEquals("com.sd.demo.xlog:remote", processOfCmdline("com.sd.demo.xlog:remote\u0000\u0000\u0000"))
    // 结尾没有'\u0000'
    assertEquals("com.sd.demo.xlog", processOfCmdline("com.sd.demo.xlog"))
    // 内容为空时取不到
    assertNull(processOfCmdline(""))
    assertNull(processOfCmdline("\u0000"))
  }
}

private interface LogTestLogger : FLogger
