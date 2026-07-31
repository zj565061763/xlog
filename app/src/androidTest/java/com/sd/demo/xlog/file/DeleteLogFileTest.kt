package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.dateOfDaysAgo
import com.sd.demo.xlog.fCreateFile
import com.sd.demo.xlog.resetLogDir
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 删除日志文件。
 *
 * [FLog.deleteLog]是和当前时间比较的，所以这里的日期只能相对当前时间往前推，
 * 跨月/跨年/闰年/夏令时这些日期计算的场景在lib模块的LogFilenameTest里覆盖
 */
@RunWith(AndroidJUnit4::class)
class DeleteLogFileTest {

  @Test
  fun test() {
    FLog.setLevel(FLogLevel.All)

    val dir = resetLogDir()
    flogI<TestLogger> { "info" }
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(true, dir.exists())

    val todayFile = dir.resolve(dateOfDaysAgo(0))
    val file1 = dir.resolve(dateOfDaysAgo(1)).apply { fCreateFile() }
    val file2 = dir.resolve(dateOfDaysAgo(2)).apply { fCreateFile() }
    val file3 = dir.resolve(dateOfDaysAgo(3)).apply { fCreateFile() }
    val file4 = dir.resolve(dateOfDaysAgo(4)).apply { fCreateFile() }
    val file5 = dir.resolve(dateOfDaysAgo(5)).apply { fCreateFile() }

    assertEquals(true, todayFile.exists())
    assertEquals(true, file1.exists())
    assertEquals(true, file2.exists())
    assertEquals(true, file3.exists())
    assertEquals(true, file4.exists())
    assertEquals(true, file5.exists())

    kotlin.run {
      FLog.deleteLog(5)
      awaitLogIdle()
      assertEquals(true, todayFile.exists())
      assertEquals(true, file1.exists())
      assertEquals(true, file2.exists())
      assertEquals(true, file3.exists())
      assertEquals(true, file4.exists())
      assertEquals(false, file5.exists())
    }

    kotlin.run {
      FLog.deleteLog(3)
      awaitLogIdle()
      assertEquals(true, todayFile.exists())
      assertEquals(true, file1.exists())
      assertEquals(true, file2.exists())
      assertEquals(false, file3.exists())
      assertEquals(false, file4.exists())
      assertEquals(false, file5.exists())
    }

    kotlin.run {
      FLog.deleteLog(1)
      awaitLogIdle()
      assertEquals(true, todayFile.exists())
      assertEquals(false, file1.exists())
      assertEquals(false, file2.exists())
      assertEquals(false, file3.exists())
      assertEquals(false, file4.exists())
      assertEquals(false, file5.exists())
    }

    kotlin.run {
      FLog.deleteLog(0)
      awaitLogIdle()
      assertEquals(false, dir.exists())
    }
  }
}
