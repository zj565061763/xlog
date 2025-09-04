package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.testLogDir
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogDirectoryScope
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat

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

    var scope: FLogDirectoryScope? = null
    FLog.logDirectory {
      scope = this
      val date = SimpleDateFormat("yyyyMMdd").format(System.currentTimeMillis())
      logZipOf(date)!!.also { file ->
        assertEquals(true, file.exists())
        assertEquals(true, file.length() > 0)
      }
    }
    val date = SimpleDateFormat("yyyyMMdd").format(System.currentTimeMillis())
    assertEquals(null, scope!!.logZipOf(date))
  }
}