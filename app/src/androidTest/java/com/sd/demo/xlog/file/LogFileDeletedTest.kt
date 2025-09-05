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

/**
 * 日志文件被删除，自动重建
 */
@RunWith(AndroidJUnit4::class)
class LogFileDeletedTest {

  @Test
  fun test() {
    val dir = testLogDir
    FLog.setLevel(FLogLevel.All)

    dir.deleteRecursively()
    assertEquals(false, dir.exists())
    flogI<TestLogger> { "info" }
    flogI<TestLogger> { "info" }
    assertEquals(true, dir.exists())

    // 模拟删除文件
    dir.deleteRecursively()
    assertEquals(false, dir.exists())

    // 触发close()
    flogI<TestLogger> { "info" }
    // 触发创建新文件
    flogI<TestLogger> { "info" }

    assertEquals(true, dir.exists())
    assertEquals(false, dir.listFiles()?.isEmpty())
  }
}