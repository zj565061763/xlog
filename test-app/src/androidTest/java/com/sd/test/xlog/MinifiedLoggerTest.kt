package com.sd.test.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 验证经过R8的日志调用和consumer rules */
@RunWith(AndroidJUnit4::class)
class MinifiedLoggerTest {
  /** 混淆后保留logger名称，嵌套和局部类型的tag带外部类名，未使用类型仍被移除 */
  @Test
  fun testDefaultTagsAndUnusedLogger() {
    val dir = resetLogDir()
    TestLogDispatcher().use { dispatcher ->
      assertTrue(FLog.init(testContext) {
        setLogDirectory { dir }
        setLogDispatcher(dispatcher)
      })
      MinifiedLoggers.write()
      FLog.logDirectory { }
      dispatcher.awaitLogIdle()

      val tags = dir.resolve(dateOfDaysAgo(0)).walkTopDown().filter { it.isFile }.flatMap { it.readLines() }
        .associate { it.substringAfter("] ") to it.substringAfter('[').substringBefore('|') }
      assertEquals("TopLevelLogger", tags["top"])
      assertEquals("LoggerOuter\$NestedLogger", tags["nested"])
      assertEquals("MinifiedLoggers\$write\$LocalLogger", tags["local"])
      assertEquals("MinifiedLoggers\$write\$anonymous\$1", tags["anonymous"])

      val loader = testContext.classLoader
      assertThrows(ClassNotFoundException::class.java) { loader.loadClass("com.sd.test.xlog.UnusedLogger") }
      assertThrows(ClassNotFoundException::class.java) { loader.loadClass("com.sd.test.xlog.LoggerOuter") }
    }
  }
}
