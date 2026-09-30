package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [processOf]、[processOfCmdline] */
class UtilsTest {
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
