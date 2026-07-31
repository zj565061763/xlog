package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.dateOfDaysAgo
import com.sd.demo.xlog.resetLogDir
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 日志压缩包不参与日志保留策略
 */
@RunWith(AndroidJUnit4::class)
class LogZipTest {

  @Test
  fun test() {
    FLog.setLevel(FLogLevel.All)

    val dir = resetLogDir()
    flogI<TestLogger> { "info" }
    awaitLogIdle()

    val today = dateOfDaysAgo(0)

    var zip: File? = null
    FLog.logDirectory { zip = logZipOf(today) }
    awaitLogIdle()
    assertEquals(true, zip?.exists())
    assertEquals(true, (zip?.length() ?: 0) > 0)

    // 压缩包在.开头的内部目录里，不在日志根目录下
    assertEquals(true, zip!!.startsWith(dir))
    assertEquals(
      true,
      generateSequence(zip!!.parentFile) { it.parentFile }
        .any { it.name.startsWith(".") },
    )

    // 只保留当天的日志，压缩包不受影响
    kotlin.run {
      FLog.deleteLog(1)
      awaitLogIdle()
      assertEquals(true, dir.resolve(today).exists())
      assertEquals(true, zip!!.exists())
    }

    /**
     * 删除全部日志，压缩包依然保留。
     * 使用方常见的用法就是导出压缩包之后立即清空日志，再去上传压缩包
     */
    kotlin.run {
      FLog.deleteLog(0)
      awaitLogIdle()
      assertEquals(false, dir.resolve(today).exists())
      assertEquals(true, zip!!.exists())
      assertEquals(true, dir.exists())
    }
  }
}
