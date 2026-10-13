package com.sd.test.xlog

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** 单个日志文件达到4GiB时也能打包，这种文件只有在设备上才能验证 */
@RunWith(AndroidJUnit4::class)
class LogZipLargeFileTest {
  /** API 23要预先声明条目的大小才能写入4GiB以上的文件；API 21、22写不了这么大的条目，跳过 */
  @Test
  fun test() {
    assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
    val dir = resetLogDir()
    val today = dateOfDaysAgo(0)
    val size = 4L * 1024 * 1024 * 1024
    // 稀疏文件，不占用实际空间
    val logFile = dir.resolve("${today}/${testContext.packageName}/${today}.0.log").also {
      assertTrue(it.parentFile!!.mkdirs())
      RandomAccessFile(it, "rw").use { file -> file.setLength(size) }
    }

    TestLogDispatcher().use { dispatcher ->
      val mark = logcatMark()
      assertTrue(FLog.init(testContext) {
        setLogDirectory { dir }
        setLogDispatcher(dispatcher)
      })

      // 打包要几十秒，超过awaitLogIdle的等待时间，先单独等打包结束
      val done = CountDownLatch(1)
      var zip: File? = null
      FLog.logDirectory {
        try {
          zip = logZipOf(today)
        } finally {
          done.countDown()
        }
      }
      assertTrue(done.await(5, TimeUnit.MINUTES))
      dispatcher.awaitLogIdle()

      // 打包失败的原因在库内部日志里，先断言它
      assertEquals(emptyList<String>(), libLogsSince(mark))
      val entries = ZipFile(checkNotNull(zip)).use { archive ->
        archive.entries().asSequence().filter { !it.isDirectory }.map { it.name to it.size }.toList()
      }
      assertEquals(listOf("${today}/${testContext.packageName}/${logFile.name}" to size), entries)
    }
  }
}
