package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LogPublisherTest {
  @get:Rule
  val folder = TemporaryFolder()

  /** 正常轮换：写满就切下一个序号，始终只保留当前和上一个 */
  @Test
  fun testRotate() {
    val dir = folder.newFolder()
    val publisher = newPublisher(dir) { defaultLogStore(it) }

    // 上限400字节，写满200字节就轮换，每条日志约61字节，所以4条填满一个分片
    val maxByte = 400L
    publisher.setMaxBytePerDay(maxByte)

    // 4条填满序号0，再4条填满序号1(序号0被删)，剩2条写进序号2
    repeat(10) { publisher.publish(testLogRecord()) }

    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    assertEquals(
      listOf(filename.logNameOf(date, 1), filename.logNameOf(date, 2)),
      dir.logNames(),
    )

    val totalSize = dir.totalSize()
    assertTrue("日志总大小${totalSize}字节，超过了上限${maxByte}字节", totalSize <= maxByte)
  }

  /**
   * 轮换的时候创建日志仓库失败，不能退回去继续写旧文件。
   * 否则每条日志都会触发一次轮换、每次都失败，
   * 旧文件无限增长，[FLog.setMaxMBPerDay]的限制形同虚设
   */
  @Test
  fun testStoreCreateErrorOnRotate() {
    val dir = folder.newFolder()
    var createCount = 0

    val publisher = newPublisher(dir) { file ->
      createCount++
      // 第一个文件创建成功，之后轮换时全部失败
      if (createCount >= 2) error("create error")
      defaultLogStore(file)
    }

    val maxByte = 100L
    publisher.setMaxBytePerDay(maxByte)

    var failures = 0
    repeat(20) {
      runCatching { publisher.publish(testLogRecord()) }.onFailure { failures++ }
    }

    // 创建失败的时候日志写不进去，但是已有文件不能因此无限增长
    val totalSize = dir.totalSize()
    assertTrue("日志总大小${totalSize}字节，超过了上限${maxByte}字节", totalSize <= maxByte)
    assertTrue("应该有写入失败", failures > 0)
  }
}

private fun newPublisher(dir: File, storeFactory: FLogStore.Factory): DirectoryLogPublisher {
  return defaultLogPublisher(
    process = null,
    directory = dir,
    filename = defaultLogFilename(),
    formatter = defaultLogFormatter(),
    storeFactory = storeFactory,
  )
}

private const val RECORD_MILLIS = 1_700_000_000_000L

/** 每条日志格式化之后约61字节 */
private fun testLogRecord(): FLogRecord = object : FLogRecord {
  override val logger: Class<out FLogger> = FLogLibLogger::class.java
  override val level: FLogLevel = FLogLevel.Info
  override val tag: String = "T"
  override val msg: String = "0123456789012345678901234567890123456789"
  override val millis: Long = RECORD_MILLIS
  override val isMainThread: Boolean = false
  override val threadID: String = "1"
}

private fun File.logNames(): List<String> {
  return walkTopDown().filter { it.isFile }.map { it.name }.sorted().toList()
}

private fun File.totalSize(): Long {
  return walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
