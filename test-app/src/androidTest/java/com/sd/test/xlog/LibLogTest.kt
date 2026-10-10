package com.sd.test.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogMode
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 库内部日志只在出错时输出，正常结果输出的话会被当成出错 */
@RunWith(AndroidJUnit4::class)
class LibLogTest {
  /** 正常的写入、切换日志文件、删除过期日志和打包都不输出库内部日志 */
  @Test
  fun testSilentOnSuccess() {
    val dir = resetLogDir()
    val today = dateOfDaysAgo(0)
    // 过期的日志目录，deleteLog会删掉它
    val expired = dir.resolve(dateOfDaysAgo(2)).also {
      assertTrue(it.mkdirs())
      it.resolve("${it.name}.0.log").writeText("old")
    }

    TestLogDispatcher().use { dispatcher ->
      val mark = logcatMark()
      assertTrue(FLog.init(testContext) {
        setLogDirectory { dir }
        setLogDispatcher(dispatcher)
      })

      // 上限1MB，每条600KB的日志写满一个文件；第2、3次切换时各删除一个旧文件
      FLog.setMaxMBPerDay(1)
      val log = "1".repeat(600 * 1024)
      repeat(3) { flogI<LibLogger>(FLogMode.Store) { log } }
      flogI<LibLogger>(FLogMode.Store) { "tail" }
      FLog.deleteLog(1)
      var zip: File? = null
      FLog.logDirectory { zip = logZipOf(today) }
      dispatcher.awaitLogIdle()

      // 确认切换时删除了旧文件、过期目录已经删除、打包成功，否则这个测试什么也没验证
      val logDir = dir.resolve("${today}/${testContext.packageName}")
      assertEquals(listOf("${today}.2.log", "${today}.3.log"), logDir.list()?.sorted())
      assertFalse(expired.exists())
      assertEquals(true, zip?.isFile)

      assertEquals(emptyList<String>(), libLogsSince(mark))
    }
  }
}

private interface LibLogger : FLogger
