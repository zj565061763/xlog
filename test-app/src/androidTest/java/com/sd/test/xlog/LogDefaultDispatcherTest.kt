package com.sd.test.xlog

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogMode
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 不设置调度器时使用默认调度器，它的线程优先级只有在设备上才能验证 */
@RunWith(AndroidJUnit4::class)
class LogDefaultDispatcherTest {
  /** 日志在后台线程上按顺序写入；线程的nice值达到10的话，Android 12及以下会把它移到后台调度组，队列积压 */
  @Test
  fun test() {
    val dir = resetLogDir()
    assertTrue(FLog.init(testContext) {
      setLogDirectory { dir }
    })

    val count = 200
    repeat(count) { index -> flogI<DefaultDispatcherLogger>(FLogMode.Store) { "msg${index}" } }

    // block在调度线程上执行，排在前面的日志这时都已经写入
    val done = CountDownLatch(1)
    val thread = AtomicReference<Thread>()
    val nice = AtomicInteger()
    FLog.logDirectory {
      thread.set(Thread.currentThread())
      nice.set(Process.getThreadPriority(Process.myTid()))
      done.countDown()
    }
    assertTrue(done.await(10, TimeUnit.SECONDS))

    val lines = dir.resolve(dateOfDaysAgo(0)).walkTopDown().filter { it.isFile }.flatMap { it.readLines() }.toList()
    assertEquals((0 until count).map { "msg${it}" }, lines.map { it.substringAfter("] ") })
    assertNotSame(Thread.currentThread(), thread.get())
    assertTrue("nice=${nice.get()}", nice.get() < 10)
  }
}

private interface DefaultDispatcherLogger : FLogger
