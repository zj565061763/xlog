package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections

/**
 * Java API：[FLog.logV]等方法的两种重载都能从Java调用，等级、日志标识和模式正确。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogJavaApiTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val dispatcher = AwaitDispatcher()
    val formatter = RecordFormatter()
    assertTrue(FLog.init(ContextWrapper(null)) {
      setLogDirectory { dir }
      setLogDispatcher(dispatcher)
      setLogFormatter(formatter)
    })

    // 不带模式的重载用全局模式，Console模式的不写入仓库
    val logger = JavaApiLogger::class.java
    JavaApi.log(logger)
    JavaApi.log(logger, FLogMode.Store)
    JavaApi.log(logger, FLogMode.Console)
    assertTrue(dispatcher.await())

    val levels = listOf(FLogLevel.Verbose, FLogLevel.Debug, FLogLevel.Info, FLogLevel.Warning, FLogLevel.Error)
    val msgs = listOf("v", "d", "i", "w", "e")
    val records = formatter.records.toList()
    assertEquals(levels + levels, records.map { it.level })
    assertEquals(msgs + msgs, records.map { it.msg })
    assertEquals(listOf(logger), records.map { it.logger }.distinct())
    assertEquals(listOf("JavaApiLogger"), records.map { it.tag }.distinct())
    assertEquals(msgs + msgs, dir.walkTopDown().filter { it.isFile }.flatMap { it.readLines() }.toList())
  }
}

private interface JavaApiLogger : FLogger

/** 记录收到的日志记录，格式为 msg */
private class RecordFormatter : FLogFormatter {
  val records: MutableList<FLogRecord> = Collections.synchronizedList(mutableListOf())

  override fun format(record: FLogRecord): String {
    records.add(record)
    return "${record.msg}\n"
  }
}
