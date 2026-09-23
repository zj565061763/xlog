package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Test

/** [defaultLogFormatter] */
class LogFormatterTest {
  private val _time = LogTime.timeOf(RECORD_MILLIS)

  /** 格式为 HH:mm:ss.SSS[tag|L|threadID] msg */
  @Test
  fun testFormat() {
    val formatter = defaultLogFormatter()
    assertEquals("${_time}[T|I|1] msg\n", formatter.format(testLogRecord(msg = "msg")))
  }

  /** 等级显示为单个字母 */
  @Test
  fun testLevel() {
    val levels = listOf(
      FLogLevel.Verbose to "V",
      FLogLevel.Debug to "D",
      FLogLevel.Info to "I",
      FLogLevel.Warning to "W",
      FLogLevel.Error to "E",
    )
    for ((level, name) in levels) {
      val log = defaultLogFormatter().format(testLogRecord(level = level, msg = "msg"))
      assertEquals("${_time}[T|${name}|1] msg\n", log)
    }
  }

  /** 连续相同的tag省略，tag变化时重新输出 */
  @Test
  fun testOmitTag() {
    val formatter = defaultLogFormatter()
    val logs = listOf("A", "A", "B", "A").map { formatter.format(testLogRecord(tag = it, msg = "msg")) }
    assertEquals(
      listOf(
        "${_time}[A|I|1] msg\n",
        "${_time}[I|1] msg\n",
        "${_time}[B|I|1] msg\n",
        "${_time}[A|I|1] msg\n",
      ),
      logs,
    )
  }

  /** 关闭时重置状态，下一条日志不省略tag */
  @Test
  fun testClose() {
    val formatter = defaultLogFormatter()
    formatter.format(testLogRecord(tag = "A", msg = "msg"))
    (formatter as AutoCloseable).close()
    assertEquals("${_time}[A|I|1] msg\n", formatter.format(testLogRecord(tag = "A", msg = "msg")))
  }

  /** 主线程省略线程ID */
  @Test
  fun testMainThread() {
    val formatter = defaultLogFormatter()
    assertEquals("${_time}[T|I] msg\n", formatter.format(testLogRecord(msg = "msg", isMainThread = true)))
  }
}
