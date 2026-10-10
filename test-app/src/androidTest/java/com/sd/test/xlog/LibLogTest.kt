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

  /** 初始化时清空压缩包失败，输出库内部日志 */
  @Test
  fun testDeleteZipDirectoryFailed() {
    val dir = resetLogDir()
    val zipDir = dir.resolve(".zip/${testContext.packageName}")
    val staleZip = zipDir.resolve("stale.zip").also {
      assertTrue(zipDir.mkdirs())
      it.writeText("previous")
    }

    // 目录不可写时删不掉里面的压缩包
    assertTrue(zipDir.setWritable(false))
    try {
      TestLogDispatcher().use { dispatcher ->
        val mark = logcatMark()
        assertTrue(FLog.init(testContext) {
          setLogDirectory { dir }
          setLogDispatcher(dispatcher)
        })
        dispatcher.awaitLogIdle()

        // 确认压缩包确实没删掉，否则这个测试什么也没验证
        assertTrue(staleZip.isFile)
        assertEquals(listOf("delete zip directory failed"), libLogsSince(mark))
      }
    } finally {
      zipDir.setWritable(true)
    }
  }

  /** 删除日志时日志目录读取失败，输出库内部日志，不能当作空目录 */
  @Test
  fun testDeleteLogListFailed() {
    val dir = resetLogDir()
    val expired = dir.resolve(dateOfDaysAgo(2)).also {
      assertTrue(it.mkdirs())
      it.resolve("${it.name}.0.log").writeText("old")
    }

    TestLogDispatcher().use { dispatcher ->
      assertTrue(FLog.init(testContext) {
        setLogDirectory { dir }
        setLogDispatcher(dispatcher)
      })
      dispatcher.awaitLogIdle()

      // 目录不可读时列不出里面的内容
      assertTrue(dir.setReadable(false))
      try {
        assertEquals(null, dir.listFiles())
        val mark = logcatMark()
        FLog.deleteLog(1)
        dispatcher.awaitLogIdle()

        val libLogs = libLogsSince(mark)
        assertEquals(libLogs.toString(), 1, libLogs.count { it == "lib java.io.IOException: list ${dir.name} failed" })
      } finally {
        dir.setReadable(true)
      }
      assertTrue(expired.exists())
    }
  }
}

private interface LibLogger : FLogger
