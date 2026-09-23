package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [processOfCmdline] */
class LogDirTest {
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
