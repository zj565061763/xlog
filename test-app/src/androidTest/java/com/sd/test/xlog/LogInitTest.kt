package com.sd.test.xlog

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogMode
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile

/** 在独立进程中验证首次初始化 */
@RunWith(AndroidJUnit4::class)
class LogInitTest {
  /** 获取目录在调度线程上执行，初始化清理排在日志写入和导出之前 */
  @Test
  fun testInitCleanupBeforeExport() {
    val externalDir = resetLogDir()
    val dir = externalDir.resolve("sd.lib.xlog")
    val today = dateOfDaysAgo(0)
    val staleZip = dir.resolve(".zip/${testContext.packageName}/stale.zip").also {
      assertTrue(it.parentFile!!.mkdirs())
      it.writeText("previous")
    }
    val gate = CountDownLatch(1)
    val directoryCount = AtomicInteger()
    val directoryThread = AtomicReference<Thread>()
    val context = object : ContextWrapper(testContext) {
      override fun getApplicationContext(): Context = this

      override fun getExternalFilesDir(type: String?): File {
        directoryCount.incrementAndGet()
        directoryThread.set(Thread.currentThread())
        return externalDir
      }
    }

    TestLogDispatcher().use { dispatcher ->
      dispatcher.hold(gate)
      try {
        assertTrue(FLog.init(context) {
          setLogDispatcher(dispatcher)
        })
        assertEquals(0, directoryCount.get())
        assertTrue(staleZip.exists())

        var zip: File? = null
        var logDirectory: File? = null
        flogI<InitLogger>(FLogMode.Store) { "after init" }
        FLog.logDirectory {
          logDirectory = it
          zip = logZipOf(today)
        }
        gate.countDown()
        dispatcher.awaitLogIdle()

        assertEquals(1, directoryCount.get())
        assertEquals(dir, logDirectory)
        assertNotNull(directoryThread.get())
        assertNotSame(Thread.currentThread(), directoryThread.get())
        assertFalse(staleZip.exists())
        val exported = checkNotNull(zip)
        assertTrue(exported.isFile)
        val text = ZipFile(exported).use { archive ->
          val entry = archive.entries().asSequence().single { !it.isDirectory }
          archive.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        assertTrue(text, text.endsWith("] after init\n"))
      } finally {
        gate.countDown()
      }
    }
  }

  /** 目录为null时跳过写入、删除、回调和初始化清理，恢复后可以继续使用 */
  @Test
  fun testNullDirectoryRecovery() {
    testUnavailableDirectory(throwOnGet = false)
  }

  /** 获取目录抛异常时不影响调用方，恢复后可以继续使用 */
  @Test
  fun testDirectoryErrorRecovery() {
    testUnavailableDirectory(throwOnGet = true)
  }

  private fun testUnavailableDirectory(throwOnGet: Boolean) {
    val dir = resetLogDir()
    val oldLog = dir.resolve("${dateOfDaysAgo(1)}/old.log").also {
      assertTrue(it.parentFile!!.mkdirs())
      it.writeText("old")
    }
    val staleZip = dir.resolve(".zip/${testContext.packageName}/stale.zip").also {
      assertTrue(it.parentFile!!.mkdirs())
      it.writeText("previous")
    }
    val available = AtomicBoolean()
    val directoryCount = AtomicInteger()

    TestLogDispatcher().use { dispatcher ->
      assertTrue(FLog.init(testContext) {
        setLogDispatcher(dispatcher)
        setLogDirectory {
          directoryCount.incrementAndGet()
          when {
            available.get() -> dir
            throwOnGet -> throw IOException("directory error")
            else -> null
          }
        }
      })

      var callbacks = 0
      flogI<InitLogger>(FLogMode.Store) { "lost" }
      FLog.deleteLog(0)
      FLog.logDirectory { callbacks++ }
      dispatcher.awaitLogIdle()
      assertEquals(4, directoryCount.get())
      assertEquals(0, callbacks)
      assertTrue(oldLog.isFile)
      assertTrue(staleZip.isFile)
      assertFalse(dir.resolve(dateOfDaysAgo(0)).exists())

      available.set(true)
      flogI<InitLogger>(FLogMode.Store) { "recovered" }
      FLog.logDirectory { callbacks++ }
      dispatcher.awaitLogIdle()
      assertEquals(5, directoryCount.get())
      assertEquals(1, callbacks)

      available.set(false)
      flogI<InitLogger>(FLogMode.Store) { "cached" }
      FLog.logDirectory { callbacks++ }
      dispatcher.awaitLogIdle()
      assertEquals(5, directoryCount.get())
      assertEquals(2, callbacks)
      val lines = dir.resolve(dateOfDaysAgo(0)).walkTopDown().filter { it.isFile }.flatMap { it.readLines() }.toList()
      assertEquals(listOf("recovered", "cached"), lines.map { it.substringAfter("] ") })
      assertTrue(lines.all { it.contains("[InitLogger|") })
      assertEquals("old", oldLog.readText())
      assertEquals("previous", staleZip.readText())
    }
  }
}

private interface InitLogger : FLogger
