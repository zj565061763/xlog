package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.dateOfDaysAgo
import com.sd.demo.xlog.resetLogDir
import com.sd.demo.xlog.testContext
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogDirectoryScope
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 限制日志文件大小
 */
@RunWith(AndroidJUnit4::class)
class LogFileLimitTest {

  @Test
  fun test() {
    FLog.setLevel(FLogLevel.All)
    FLog.setMaxMBPerDay(1)

    val dir = resetLogDir()
    flogI<TestLogger> { "info" }
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(true, dir.exists())
    assertEquals(false, dir.listFiles()?.isEmpty())

    val today = dateOfDaysAgo(0)
    val logDir = dir.resolve(today).resolve(testContext.packageName)

    logDir.listFiles { _, name -> name.endsWith(".1") }.also { files ->
      assertEquals(0, files?.size)
    }

    val log = "1".repeat(800 * 1024)
    flogI<TestLogger> { log }
    awaitLogIdle()

    logDir.listFiles { _, name -> name.endsWith(".1") }.also { files ->
      assertEquals(1, files?.size)
    }

    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(2, logDir.listFiles()!!.size)

    var scope: FLogDirectoryScope? = null
    FLog.logDirectory {
      scope = this
      logZipOf(today)!!.also { file ->
        assertEquals(true, file.exists())
        assertEquals(true, file.length() > 0)
      }
    }
    awaitLogIdle()
    // 离开logDirectory之后scope已经销毁
    assertEquals(null, scope!!.logZipOf(today))
  }
}