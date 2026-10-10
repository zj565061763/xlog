package com.sd.test.xlog

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLogDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.Closeable
import java.io.File
import java.util.Calendar
import java.util.UUID
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

/** 往Logcat打印一条内容每次都不同的标记并返回它，之后用[libLogsSince]读取这之后的库内部日志 */
internal fun logcatMark(): String {
  return UUID.randomUUID().toString().also { Log.i(MARK_TAG, it) }
}

/**
 * [mark]之后输出到Logcat的库内部日志，一行一条，不含优先级和tag。
 * Logcat是异步写入的，这里再打印一条结束标记，等它出现才返回。
 */
internal fun libLogsSince(mark: String): List<String> {
  val end = logcatMark()
  val deadline = SystemClock.uptimeMillis() + 10_000
  while (true) {
    val logs = shell("logcat -d -v tag -s $MARK_TAG:V $LIB_TAG:V").lineSequence()
      .mapNotNull { LogcatLine.matchEntire(it) }
      .map { it.destructured }
      .map { (tag, msg) -> tag to msg }
      .toList()
    val startIndex = logs.indexOf(MARK_TAG to mark)
    val endIndex = logs.indexOf(MARK_TAG to end)
    if (startIndex >= 0 && endIndex > startIndex) {
      return logs.subList(startIndex + 1, endIndex).filter { it.first == LIB_TAG }.map { it.second }
    }
    if (SystemClock.uptimeMillis() >= deadline) throw AssertionError("wait logcat timeout: $logs")
    Thread.sleep(100)
  }
}

private const val MARK_TAG = "XLogTestMark"
private const val LIB_TAG = "XLogLibLogger"

/** `logcat -v tag`的一行：`优先级/tag: 内容`，tag不足8位时后面补空格 */
private val LogcatLine = Regex("""[VDIWEF]/(\S+)\s*: (.*)""")

/** 以shell身份执行命令，返回输出 */
private fun shell(command: String): String {
  val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
  return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
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
