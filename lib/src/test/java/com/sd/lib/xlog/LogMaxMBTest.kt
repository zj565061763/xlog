package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [FLog.setMaxMBPerDay]按1MB等于1048576字节换算，而且不能溢出：历史上用Int计算，数值过大时上限会变小或者失效。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogMaxMBTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val dispatcher = AwaitDispatcher()
    assertTrue(FLog.init(ContextWrapper(null)) {
      setLogDirectory { dir }
      setLogDispatcher(dispatcher)
    })

    fun logFileCount(): Int = dir.walkTopDown().count { it.isFile }

    // 4097MB用Int计算会溢出成1MB，写满512KB就切换；不溢出的话600KB远没到上限
    FLog.setMaxMBPerDay(4097)
    flogI<MaxMBLogger>(FLogMode.Store) { "1".repeat(600 * 1024) }
    flogI<MaxMBLogger>(FLogMode.Store) { "tail" }
    assertTrue(dispatcher.await())
    assertEquals(1, logFileCount())

    // 上限2MB时写满一半1024KB才切换，再写410KB共1010KB，还不切换。
    // 按1000换算的话一半是1000KB，这里已经切换了。
    FLog.setMaxMBPerDay(2)
    flogI<MaxMBLogger>(FLogMode.Store) { "1".repeat(410 * 1024) }
    flogI<MaxMBLogger>(FLogMode.Store) { "tail" }
    assertTrue(dispatcher.await())
    assertEquals(1, logFileCount())

    // 再写20KB共1030KB，超过1024KB后切换，下一条日志写进新文件
    flogI<MaxMBLogger>(FLogMode.Store) { "1".repeat(20 * 1024) }
    flogI<MaxMBLogger>(FLogMode.Store) { "next" }
    assertTrue(dispatcher.await())
    // 确认切换确实会发生，否则上面什么也没验证
    assertEquals(2, logFileCount())
  }
}

private interface MaxMBLogger : FLogger
