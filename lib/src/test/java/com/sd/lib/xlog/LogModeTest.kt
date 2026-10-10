package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 日志模式的优先级：调用时传入的模式 > logger配置的模式 > 全局设置，Console模式的日志不写入仓库。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogModeTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val dispatcher = AwaitDispatcher()
    assertTrue(FLog.init(ContextWrapper(null)) {
      setLogDirectory { dir }
      setLogDispatcher(dispatcher)
      configLogger(ConsoleModeLogger::class.java) { it.copy(mode = FLogMode.Console) }
    })

    // 全局设置默认是Default，写入仓库
    flogI<ModeLogger> { "global default" }
    // 配置的模式优先于全局设置
    flogI<ConsoleModeLogger> { "config console" }
    // 调用时传入的模式优先于配置
    flogI<ConsoleModeLogger>(FLogMode.Store) { "call store over config" }

    FLog.setMode(FLogMode.Console)
    flogI<ModeLogger> { "global console" }
    flogI<ModeLogger>(FLogMode.Default) { "call default over global" }

    FLog.setMode(FLogMode.Store)
    flogI<ModeLogger> { "global store" }
    flogI<ModeLogger>(FLogMode.Console) { "call console over global" }
    flogI<ConsoleModeLogger> { "config console over global" }

    assertTrue(dispatcher.await())
    assertEquals(
      listOf("global default", "call store over config", "call default over global", "global store"),
      dir.walkTopDown().filter { it.isFile }.flatMap { it.readLines() }.map { it.substringAfter("] ") }.toList(),
    )
  }
}

private interface ModeLogger : FLogger

/** 配置为只输出到控制台的日志标识 */
private interface ConsoleModeLogger : FLogger
