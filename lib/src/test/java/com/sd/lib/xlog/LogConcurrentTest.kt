package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [FLog]的并发：多线程同时初始化只有一个成功，多线程打印的日志不丢失、不串行，每个线程保持自己的顺序。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogConcurrentTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val dispatcher = AwaitDispatcher()
    val threadCount = 8
    val logsPerThread = 500
    val pool = Executors.newFixedThreadPool(threadCount)

    try {
      // 所有线程就绪后同时初始化，只有一个成功，initBlock也只执行一次
      val start = CountDownLatch(1)
      val initBlockCount = AtomicInteger()
      val inits = (0 until threadCount).map {
        pool.submit(Callable {
          check(start.await(10, TimeUnit.SECONDS))
          FLog.init(ContextWrapper(null)) {
            initBlockCount.incrementAndGet()
            setLogDirectory { dir }
            setLogDispatcher(dispatcher)
          }
        })
      }
      start.countDown()
      assertEquals(1, inits.count { it.get(10, TimeUnit.SECONDS) })
      assertEquals(1, initBlockCount.get())

      val logs = (0 until threadCount).map { thread ->
        pool.submit {
          repeat(logsPerThread) { index -> flogI<ConcurrentLogger> { "${thread}-${index}" } }
        }
      }
      logs.forEach { it.get(10, TimeUnit.SECONDS) }
      assertTrue(dispatcher.await())

      val msgs = dir.walkTopDown().filter { it.isFile }.flatMap { it.readLines() }.map { it.substringAfter("] ") }.toList()
      assertEquals(threadCount * logsPerThread, msgs.size)
      repeat(threadCount) { thread ->
        assertEquals((0 until logsPerThread).map { "${thread}-${it}" }, msgs.filter { it.startsWith("${thread}-") })
      }
    } finally {
      pool.shutdownNow()
    }
  }
}

private interface ConcurrentLogger : FLogger
