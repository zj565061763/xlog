package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 日志模式的优先级：调用时传入的模式 > logger配置的模式 > 全局设置，Console模式的日志不写入仓库。
 * 每个等级的API都把调用时传入的模式和自己的等级传下去。
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
      listOf("I global default", "I call store over config", "I call default over global", "I global store"),
      dir.levelMsgs(),
    )

    // 全局设置是Console，每个等级的API传入Store后都写入仓库，等级和API对应
    FLog.setMode(FLogMode.Console)
    val logger = object : ModeLogger {}
    flogV<ModeLogger>(FLogMode.Store) { "flogV" }
    flogD<ModeLogger>(FLogMode.Store) { "flogD" }
    flogI<ModeLogger>(FLogMode.Store) { "flogI" }
    flogW<ModeLogger>(FLogMode.Store) { "flogW" }
    flogE<ModeLogger>(FLogMode.Store) { "flogE" }
    logger.lv(FLogMode.Store) { "lv" }
    logger.ld(FLogMode.Store) { "ld" }
    logger.li(FLogMode.Store) { "li" }
    logger.lw(FLogMode.Store) { "lw" }
    logger.le(FLogMode.Store) { "le" }

    assertTrue(dispatcher.await())
    assertEquals(
      listOf("V flogV", "D flogD", "I flogI", "W flogW", "E flogE", "V lv", "D ld", "I li", "W lw", "E le"),
      dir.levelMsgs().drop(4),
    )
  }
}

/** 目录下日志文件里每一行的等级和消息，格式为 `L msg` */
private fun File.levelMsgs(): List<String> {
  val levels = setOf("V", "D", "I", "W", "E")
  return walkTopDown().filter { it.isFile }.flatMap { it.readLines() }
    .map { line ->
      // 方括号里是 tag|L|threadID，tag和threadID可能省略
      val level = line.substringAfter("[").substringBefore("]").split("|").first { it in levels }
      "$level ${line.substringAfter("] ")}"
    }
    .toList()
}

private interface ModeLogger : FLogger

/** 配置为只输出到控制台的日志标识 */
private interface ConsoleModeLogger : FLogger
