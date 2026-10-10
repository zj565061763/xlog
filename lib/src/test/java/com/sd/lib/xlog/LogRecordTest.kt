package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 日志记录在打印日志的线程上生成：时间戳是调用时间，线程ID是调用线程的，和什么时候写入无关。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogRecordTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val dispatcher = AwaitDispatcher()
    val records = Collections.synchronizedList(mutableListOf<FLogRecord>())
    assertTrue(FLog.init(ContextWrapper(null)) {
      setLogDirectory { dir }
      setLogDispatcher(dispatcher)
      setLogFormatter(object : FLogFormatter {
        override fun format(record: FLogRecord): String {
          records.add(record)
          return "${record.msg}\n"
        }
      })
    })
    assertTrue(dispatcher.await())

    // 停住调度线程，日志排队等待写入
    val gate = CountDownLatch(1)
    dispatcher.dispatch { check(gate.await(10, TimeUnit.SECONDS)) }

    val before = System.currentTimeMillis()
    flogI<RecordTimeLogger>(FLogMode.Store) { "msg" }
    val after = System.currentTimeMillis()

    // 等时钟走过调用时间再放行，写入时间一定比调用时间晚
    while (System.currentTimeMillis() <= after) Thread.sleep(1)
    // 确认这时还没有写入，否则这个测试什么也没验证
    assertEquals(0, records.size)
    gate.countDown()
    assertTrue(dispatcher.await())

    val record = records.single()
    assertTrue("${before} ${record.millis} ${after}", record.millis in before..after)
    assertEquals(Thread.currentThread().id.toString(), record.threadID)
    assertNotEquals(Thread.currentThread(), dispatcher.thread)
  }
}

private interface RecordTimeLogger : FLogger
