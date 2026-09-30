package com.sd.test.xlog

import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLogDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.Closeable
import java.io.File
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal val testContext get() = InstrumentationRegistry.getInstrumentation().targetContext

/** 每个用例由Orchestrator启动新进程，初始化之前清理专用日志目录 */
internal fun resetLogDir(): File {
  return testContext.cacheDir.resolve("xlog-tests").also {
    assertTrue(it.deleteRecursively())
    assertFalse(it.exists())
  }
}

/** 距今[days]天的日期 */
internal fun dateOfDaysAgo(days: Int): String {
  return Calendar.getInstance().run {
    add(Calendar.DAY_OF_MONTH, -days)
    "%04d%02d%02d".format(java.util.Locale.US, get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))
  }
}

/** 串行执行任务，支持等待包含空闲回调在内的所有任务完成 */
internal class TestLogDispatcher : FLogDispatcher, Closeable {
  private val _executor = Executors.newSingleThreadExecutor()

  override fun dispatch(task: Runnable) {
    _executor.execute(task)
  }

  fun awaitLogIdle() {
    _executor.submit {}.get(10, TimeUnit.SECONDS)
  }

  fun hold(gate: CountDownLatch) {
    _executor.execute { check(gate.await(10, TimeUnit.SECONDS)) }
  }

  override fun close() {
    _executor.shutdown()
    assertTrue(_executor.awaitTermination(10, TimeUnit.SECONDS))
  }
}
