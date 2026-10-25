package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 队列里还有日志时把等级设为Off：排队的日志照常写入，日志文件等空闲时在调度线程上关闭，不在调用线程上提前关闭。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogLevelOffTest {
  @get:Rule
  val folder = TemporaryFolder()

  @get:Rule
  val sameDay = SameDayRule()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val dispatcher = AwaitDispatcher()
    val closeThreads = Collections.synchronizedList(mutableListOf<Thread>())
    assertTrue(FLog.init(ContextWrapper(null)) {
      setLogDirectory { dir }
      setLogDispatcher(dispatcher)
      setLogStoreFactory { file ->
        val store = defaultLogStore(file)
        object : FLogStore by store {
          override fun close() {
            closeThreads.add(Thread.currentThread())
            store.close()
          }
        }
      }
    })

    // 先写一条打开日志文件，否则提前关闭时没有文件可关，这个测试什么也没验证
    flogI<LevelOffLogger>(FLogMode.Store) { "opened" }
    assertTrue(dispatcher.await())
    val logFile = dir.walkTopDown().single { it.isFile }

    // 停住调度线程，日志排队等待写入
    val gate = CountDownLatch(1)
    dispatcher.dispatch { check(gate.await(10, TimeUnit.SECONDS)) }
    val count = 10
    repeat(count) { index -> flogI<LevelOffLogger>(FLogMode.Store) { "msg${index}" } }

    // 排队的日志还没写入，这时不能关闭
    FLog.setLevel(FLogLevel.Off)
    assertEquals(emptyList<Thread>(), closeThreads)
    assertEquals(listOf("opened"), logFile.msgs())

    gate.countDown()
    assertTrue(dispatcher.await())

    // 排队的日志全部写入，之后在调度线程上关闭一次
    assertEquals(listOf("opened") + (0 until count).map { "msg${it}" }, logFile.msgs())
    assertEquals(listOf(checkNotNull(dispatcher.thread)), closeThreads)
  }
}

private interface LevelOffLogger : FLogger

/** 日志文件里每条日志的消息 */
private fun File.msgs(): List<String> = readLines().map { it.substringAfter("] ") }
