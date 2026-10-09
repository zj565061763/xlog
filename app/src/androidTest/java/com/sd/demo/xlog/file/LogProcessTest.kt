package com.sd.demo.xlog.file

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.SampleLog
import com.sd.demo.xlog.SampleLogProcess
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.dateOfDaysAgo
import com.sd.demo.xlog.fCreateFile
import com.sd.demo.xlog.resetLogDir
import com.sd.demo.xlog.testContext
import com.sd.demo.xlog.zipFileNames
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.flogI
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 多进程：各进程写到自己的子目录，init只清空本进程的压缩包目录，打包包含所有进程的日志。
 *
 * [SampleLogProcess]运行在:custom进程里，不在测试进程里，
 * 所以不能用[awaitLogIdle]等待，只能轮询文件系统。
 */
@RunWith(AndroidJUnit4::class)
class LogProcessTest {
  private val _mainProcess = testContext.packageName
  private val _customProcess = "${_mainProcess}:custom"

  /** 进程名里的:在目录名里替换为- */
  private val _customDirName = "${_mainProcess}-custom"

  @Before
  fun setUp() {
    killCustomProcess()
  }

  @After
  fun tearDown() {
    killCustomProcess()
  }

  @Test
  fun test() {
    val dir = resetLogDir()
    val today = dateOfDaysAgo(0)
    flogI<TestLogger> { "main" }
    awaitLogIdle()

    // 两个进程的压缩包目录各放一个标记文件，custom进程init时只能清空自己的
    val mainMarker = dir.resolve(".zip").resolve(_mainProcess).resolve("marker.zip").apply { fCreateFile() }
    val customMarker = dir.resolve(".zip").resolve(_customDirName).resolve("marker.zip").apply { fCreateFile() }

    // custom进程打印5条日志，最后在子线程打印一条，等到它说明前面的都写完了
    startCustomProcessActivity()
    val customLog = dir.resolve(today).resolve(_customDirName).resolve("${today}.0.log")
    awaitUntil { customLog.isFile && customLog.readText().contains("in thread") }

    val lines = customLog.readLines()
    assertEquals(6, lines.size)
    assertTrue(lines[0], lines[0].contains("[AppLoggerAppLogger|V"))
    assertEquals(setOf(_mainProcess, _customDirName), dir.resolve(today).list()?.toSet())

    // init的清空任务排在日志前面，日志写出来时标记文件已经删掉
    assertEquals(false, customMarker.exists())
    assertEquals(true, mainMarker.exists())

    var zip: File? = null
    FLog.logDirectory { zip = logZipOf(today) }
    awaitLogIdle()
    assertEquals(
      setOf("${today}/${_mainProcess}/${today}.0.log", "${today}/${_customDirName}/${today}.0.log"),
      zip!!.zipFileNames().toSet(),
    )
  }

  /** 从测试进程启动：页面没有exported，只有同一个uid能启动；adb启动的测试进程有后台启动权限 */
  private fun startCustomProcessActivity() {
    val intent = Intent(testContext, SampleLogProcess::class.java)
      .putExtra(SampleLog.EXTRA_LOG, true)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    testContext.startActivity(intent)
  }

  /** 杀掉custom进程，保证它下次启动时重新init；同一个uid的进程可以直接kill */
  private fun killCustomProcess() {
    val manager = testContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    fun pid(): Int? = manager.runningAppProcesses?.firstOrNull { it.processName == _customProcess }?.pid
    pid()?.also { Process.killProcess(it) }
    awaitUntil { pid() == null }
  }
}

/** 轮询等待[condition]成立，超时则断言失败 */
private fun awaitUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
  val deadline = SystemClock.uptimeMillis() + timeoutMillis
  while (!condition()) {
    assertTrue("wait timeout", SystemClock.uptimeMillis() < deadline)
    Thread.sleep(50)
  }
}
