package com.sd.lib.xlog

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

  /** 获取日志目录失败时，发布和空闲回调都不抛异常 */
  @Test
  fun testDirectoryError() {
    val publisher = defaultLogPublisher(
      processProvider = { null },
      directoryProvider = { error("directory error") },
      filename = defaultLogFilename(),
      formatter = defaultLogFormatter(),
      storeFactory = { defaultLogStore(it) },
    ).safePublisher()

    publisher.publish(testLogRecord())
    publisher.onIdle()
    publisher.close()
  }

  /** 包装多次只包一层 */
  @Test
  fun testWrapOnce() {
    val publisher = newPublisher(folder.newFolder()).safePublisher()
    assertSame(publisher, publisher.safePublisher())
  }
}
