package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [FLog.setMaxMBPerDay]换算成字节不能溢出，历史上用Int计算，数值过大时上限会变小或者失效。
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
    val log = "1".repeat(600 * 1024)
    flogI<MaxMBLogger>(FLogMode.Store) { log }
    flogI<MaxMBLogger>(FLogMode.Store) { "tail" }
    assertTrue(dispatcher.await())
    assertEquals(1, logFileCount())

    // 确认上限是1MB时确实会切换，否则上面什么也没验证
    FLog.setMaxMBPerDay(1)
    flogI<MaxMBLogger>(FLogMode.Store) { "rotate" }
    flogI<MaxMBLogger>(FLogMode.Store) { "next" }
    assertTrue(dispatcher.await())
    assertEquals(2, logFileCount())
  }
}

private interface MaxMBLogger : FLogger
