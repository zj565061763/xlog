package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.testLogDir
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * 限制日志文件大小
 */
@RunWith(AndroidJUnit4::class)
class LogFileLimitTest {

  @Test
  fun test() {
    val dir = testLogDir
    FLog.setLevel(FLogLevel.All)
    FLog.setMaxMBPerDay(1)

    dir.deleteRecursively()
    assertEquals(false, dir.exists())
    flogI<TestLogger> { "info" }
    flogI<TestLogger> { "info" }
    assertEquals(true, dir.exists())
    assertEquals(false, dir.listFiles()?.isEmpty())

    dir.listFiles { _, name -> name.endsWith(".1") }.also { files ->
      assertEquals(0, files?.size)
    }

    val log = "1".repeat(800 * 1024)
    flogI<TestLogger> { log }

    dir.listFiles { _, name -> name.endsWith(".1") }.also { files ->
      assertEquals(1, files?.size)
    }

    flogI<TestLogger> { "info" }
    assertEquals(2, dir.listFiles()!!.size)

    FLog.logDirectory {
      val calendar = Calendar.getInstance()
      val logs = logOf(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1,
        dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
      )
      assertEquals(2, logs.size)
    }
  }
}