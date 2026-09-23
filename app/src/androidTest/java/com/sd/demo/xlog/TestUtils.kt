package com.sd.demo.xlog

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.sd.demo.xlog.log.TestLogDispatcher
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.FLogMode
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.fLogDir
import org.junit.Assert.assertEquals
import java.io.File
import java.util.Calendar
import java.util.zip.ZipFile

interface TestLogger : FLogger

val testContext: Context
  get() = InstrumentationRegistry.getInstrumentation().targetContext

fun File.fCreateFile(): Boolean {
  if (isFile) return true
  if (isDirectory) deleteRecursively()
  parentFile?.mkdirs()
  return createNewFile()
}

/**
 * 等待调度器上已经提交的任务执行完成，包括任务结束后的空闲回调。
 * 断言日志文件的状态之前都要先调用它，否则任务可能还没执行
 */
fun awaitLogIdle() {
  assertEquals(true, TestLogDispatcher.await())
}

/**
 * 恢复默认的日志等级、模式和单日上限，关闭已经打开的日志文件，删除日志目录，返回日志目录。
 *
 * 全局设置是单例状态，上一个测试改过的话会影响后面的测试，需要其他值的测试在这之后设置。
 *
 * 日志文件的句柄还开着的时候，即使目录被删除，写日志也只是写到这个句柄上，
 * 文件不会被重建（要等空闲回调发现文件不存在才会close）。
 * 所以测试开始前必须先关闭，否则结果会受上一个测试残留状态的影响。
 */
fun resetLogDir(): File {
  FLog.setLevel(FLogLevel.All)
  FLog.setMode(FLogMode.Default)
  FLog.setMaxMBPerDay(0)
  FLog.logDirectory { }
  awaitLogIdle()
  return testContext.fLogDir().apply {
    deleteRecursively()
    assertEquals(false, exists())
  }
}

/** 当前时间往前推[days]天对应的日志目录名，[days]为负数时表示以后的日期，格式和[com.sd.lib.xlog.FLog]内部保持一致 */
fun dateOfDaysAgo(days: Int): String {
  val calendar = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_MONTH, -days)
  }
  val year = calendar.get(Calendar.YEAR)
  val month = calendar.get(Calendar.MONTH) + 1
  val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
  return "${year}${month.pad()}${dayOfMonth.pad()}"
}

private fun Int.pad(): String = toString().padStart(length = 2, padChar = '0')

/** 日志目录下今天所有日志文件的内容 */
fun File.todayLogText(): String {
  return resolve(dateOfDaysAgo(0)).walkTopDown().filter { it.isFile }.joinToString("") { it.readText() }
}

/** 压缩包里的文件条目，不包括目录，按名称排序 */
fun File.zipFileNames(): List<String> {
  return ZipFile(this).use { zip -> zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.sorted().toList() }
}
