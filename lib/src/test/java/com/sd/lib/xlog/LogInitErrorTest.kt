package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * [FLog.init]的initBlock抛异常：异常抛给调用方，仍是未初始化状态；再次初始化成功，失败那次的设置不残留。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogInitErrorTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val failedDir = folder.newFolder()
    val dir = folder.newFolder()
    val dispatcher = AwaitDispatcher()
    // 失败那次设置的目录、格式化器、仓库工厂和调度器被用到的次数
    val failedUsed = AtomicInteger()

    val thrown = assertThrows(IOException::class.java) {
      FLog.init(ContextWrapper(null)) {
        setLogDirectory {
          failedUsed.incrementAndGet()
          failedDir
        }
        setLogFormatter(object : FLogFormatter {
          override fun format(record: FLogRecord): String {
            failedUsed.incrementAndGet()
            return "failed\n"
          }
        })
        setLogStoreFactory { file ->
          failedUsed.incrementAndGet()
          defaultLogStore(file)
        }
        setLogDispatcher { task ->
          failedUsed.incrementAndGet()
          task.run()
        }
        // 残留的话，下面的日志会因为等级不满足被丢弃，或者带上这个tag
        configLogger(InitErrorLogger::class.java) { it.copy(tag = "failed", level = FLogLevel.Off) }
        throw IOException("init error")
      }
    }
    assertEquals("init error", thrown.message)

    // 仍是未初始化状态
    assertThrows(IllegalStateException::class.java) { flogI<InitErrorLogger> { "lost" } }

    assertTrue(FLog.init(ContextWrapper(null)) {
      setLogDirectory { dir }
      setLogDispatcher(dispatcher)
    })
    flogI<InitErrorLogger>(FLogMode.Store) { "msg" }
    assertTrue(dispatcher.await())

    assertEquals(0, failedUsed.get())
    assertEquals(emptyList<String>(), failedDir.list()?.toList())
    val lines = dir.walkTopDown().filter { it.isFile }.flatMap { it.readLines() }.toList()
    assertEquals(1, lines.size)
    assertTrue(lines[0], lines[0].contains("[InitErrorLogger|I"))
    assertTrue(lines[0], lines[0].endsWith("] msg"))
  }
}

private interface InitErrorLogger : FLogger
