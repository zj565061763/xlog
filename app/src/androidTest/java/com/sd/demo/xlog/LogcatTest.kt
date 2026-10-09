package com.sd.demo.xlog

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.FLogMode
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * 日志输出到Logcat的等级、tag和模式，以及库内部日志。
 *
 * Logcat是异步写入的，每个用例最后打印一条结束标记，等它出现后再断言。
 */
@RunWith(AndroidJUnit4::class)
class LogcatTest {
  /** 每次运行都不同，用来区分Logcat里以前留下的日志 */
  private val _id = UUID.randomUUID().toString()

  /** 五个等级分别对应Logcat的V/D/I/W/E，tag是默认tag */
  @Test
  fun testLevel() {
    resetLogDir()
    flogV<LogcatLogger> { "$_id v" }
    flogD<LogcatLogger> { "$_id d" }
    flogI<LogcatLogger> { "$_id i" }
    flogW<LogcatLogger> { "$_id w" }
    flogE<LogcatLogger> { "$_id e" }
    logEnd()

    assertEquals(
      listOf("V/$TAG v", "D/$TAG d", "I/$TAG i", "W/$TAG w", "E/$TAG e"),
      awaitLogcat(),
    )
  }

  /** Default和Console模式输出到Logcat，Store模式不输出，调用时传入的模式优先于全局模式 */
  @Test
  fun testMode() {
    resetLogDir()
    flogI<LogcatLogger> { "$_id global default" }

    FLog.setMode(FLogMode.Store)
    flogI<LogcatLogger> { "$_id global store" }
    flogI<LogcatLogger>(mode = FLogMode.Console) { "$_id call console" }

    FLog.setMode(FLogMode.Console)
    flogI<LogcatLogger> { "$_id global console" }
    flogI<LogcatLogger>(mode = FLogMode.Store) { "$_id call store" }
    logEnd()

    assertEquals(
      listOf("I/$TAG global default", "I/$TAG call console", "I/$TAG global console"),
      awaitLogcat(),
    )
  }

  /** 库内部错误以Error等级输出到Logcat，tag是XLogLibLogger；全局等级为Off时不输出 */
  @Test
  fun testLibLog() {
    resetLogDir()
    FLog.logDirectory { error("$_id on") }
    awaitLogIdle()

    FLog.setLevel(FLogLevel.Off)
    FLog.logDirectory { error("$_id off") }
    awaitLogIdle()

    // 等级为Off时结束标记也打印不出来，先恢复
    FLog.setLevel(FLogLevel.All)
    logEnd()

    assertEquals(
      listOf("E/$LIB_TAG lib java.lang.IllegalStateException: on"),
      awaitLogcat(),
    )
  }

  /** 打印结束标记，它出现在Logcat里说明之前的日志都已经写进去 */
  private fun logEnd() {
    flogI<LogcatLogger>(mode = FLogMode.Console) { "$_id $END" }
  }

  /** 等结束标记出现，返回它之前本次运行打印的日志，格式为 `优先级/tag 内容`，内容不含运行标识 */
  private fun awaitLogcat(): List<String> {
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (true) {
      val logs = readLogcat()
      val end = logs.indexOf("I/$TAG $END")
      if (end >= 0) return logs.take(end)
      if (SystemClock.uptimeMillis() >= deadline) throw AssertionError("wait logcat timeout: $logs")
      Thread.sleep(100)
    }
  }

  /** Logcat里本次运行打印的日志，多行的内容只有带运行标识的那一行 */
  private fun readLogcat(): List<String> {
    return shell("logcat -d -v tag -s $TAG:V $LIB_TAG:V").lineSequence()
      .mapNotNull { LogcatLine.matchEntire(it) }
      .map { it.destructured }
      .filter { (_, _, msg) -> msg.contains(_id) }
      .map { (priority, tag, msg) -> "${priority}/${tag} ${msg.replace("$_id ", "")}" }
      .toList()
  }
}

private interface LogcatLogger : FLogger

private const val TAG = "LogcatLogger"
private const val LIB_TAG = "XLogLibLogger"
private const val END = "end"

/** `logcat -v tag`的一行：`优先级/tag: 内容`，tag不足8位时后面补空格 */
private val LogcatLine = Regex("""([VDIWEF])/(\S+)\s*: (.*)""")

/** 以shell身份执行命令，返回输出 */
private fun shell(command: String): String {
  val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
  return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
}
