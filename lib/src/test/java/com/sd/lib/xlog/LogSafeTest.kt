package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [safePublisher]捕获日志发布的异常，保证日志失败不影响业务 */
class LogSafeTest {
  @get:Rule
  val folder = TemporaryFolder()

  /** 创建日志仓库失败时，原始的发布会抛异常，包装之后不抛 */
  @Test
  fun testPublishError() {
    val publisher = newPublisher(folder.newFolder()) { error("create error") }
    assertThrows(IllegalStateException::class.java) { publisher.publish(testLogRecord()) }
    publisher.safePublisher().publish(testLogRecord())
  }

  /** 获取日志目录失败时，取目录、发布、空闲回调和访问目录都不抛异常，取目录按取不到处理 */
  @Test
  fun testDirectoryError() {
    val publisher = defaultLogPublisher(
      processProvider = { null },
      directoryProvider = { error("directory error") },
      filename = defaultLogFilename(),
      formatter = defaultLogFormatter(),
      storeFactory = { defaultLogStore(it) },
    ).safePublisher()

    assertNull(publisher.directory)
    publisher.publish(testLogRecord())
    publisher.onIdle()
    publisher.close()

    // 取不到目录，不执行block
    var called = false
    publisher.accessDirectory { called = true }
    assertFalse(called)
  }

  /** 获取目录暂时失败后可以恢复写入，取到目录之后不再获取 */
  @Test
  fun testDirectoryRecovery() {
    val dir = folder.newFolder()
    var directoryCount = 0
    val publisher = defaultLogPublisher(
      processProvider = { null },
      directoryProvider = { if (++directoryCount == 1) error("directory error") else dir },
      filename = defaultLogFilename(),
      formatter = defaultLogFormatter(),
      storeFactory = { defaultLogStore(it) },
    ).safePublisher()

    try {
      publisher.publish(testLogRecord(msg = "lost"))
      publisher.publish(testLogRecord(msg = "one"))
      publisher.publish(testLogRecord(msg = "two"))

      assertEquals(2, directoryCount)
      val lines = dir.walkTopDown().first { it.isFile }.readLines()
      assertEquals(listOf("one", "two"), lines.map { it.substringAfter("] ") })
    } finally {
      publisher.close()
    }
  }

  /** 关闭和空闲回调出错时，包装之后不抛异常 */
  @Test
  fun testCloseAndIdleError() {
    val publisher = object : DirectoryLogPublisher by newPublisher(folder.newFolder()) {
      override fun close() {
        error("close error")
      }

      override fun onIdle() {
        error("idle error")
      }
    }
    assertThrows(IllegalStateException::class.java) { publisher.close() }
    assertThrows(IllegalStateException::class.java) { publisher.onIdle() }

    val safePublisher = publisher.safePublisher()
    safePublisher.close()
    safePublisher.onIdle()
  }

  /** 包装多次只包一层 */
  @Test
  fun testWrapOnce() {
    val publisher = newPublisher(folder.newFolder()).safePublisher()
    assertSame(publisher, publisher.safePublisher())
  }
}
