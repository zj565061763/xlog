package com.sd.test.xlog

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
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
import org.junit.Assume.assumeTrue
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
  /** 用applicationContext在调度线程上获取目录，初始化清理排在日志写入和导出之前 */
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
    val appContext = object : ContextWrapper(testContext) {
      override fun getExternalFilesDir(type: String?): File {
        directoryCount.incrementAndGet()
        directoryThread.set(Thread.currentThread())
        return externalDir
      }
    }
    // 传入的Context可能是Activity，只能用来取applicationContext，不能在它上面获取目录
    val contextDirectoryCount = AtomicInteger()
    val context = object : ContextWrapper(testContext) {
      override fun getApplicationContext(): Context = appContext

      override fun getExternalFilesDir(type: String?): File {
        contextDirectoryCount.incrementAndGet()
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
        assertEquals(0, contextDirectoryCount.get())
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

  /** 目录为null时跳过写入、删除、回调和初始化清理，不输出库内部日志，恢复后可以继续使用 */
  @Test
  fun testNullDirectoryRecovery() {
    testUnavailableDirectory(throwOnGet = false)
  }

  /** 获取目录抛异常时不影响调用方，每次都输出库内部日志，恢复后可以继续使用 */
  @Test
  fun testDirectoryErrorRecovery() {
    testUnavailableDirectory(throwOnGet = true)
  }

  /** 系统接口出错时读取/proc/self/cmdline获取进程名，只有API 28以下的旧接口能通过Context让它出错 */
  @Test
  fun testProcessFromCmdline() {
    assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
    val dir = resetLogDir()
    val serviceCount = AtomicInteger()
    val context = object : ContextWrapper(testContext) {
      override fun getApplicationContext(): Context = this

      override fun getSystemService(name: String): Any? {
        if (name != Context.ACTIVITY_SERVICE) return super.getSystemService(name)
        serviceCount.incrementAndGet()
        error("service error")
      }
    }

    TestLogDispatcher().use { dispatcher ->
      assertTrue(FLog.init(context) {
        setLogDirectory { dir }
        setLogDispatcher(dispatcher)
      })
      flogI<InitLogger>(FLogMode.Store) { "msg" }
      dispatcher.awaitLogIdle()

      // 确认系统接口确实出错了，否则这个测试什么也没验证
      assertEquals(1, serviceCount.get())
      // 日志写在进程名对应的目录下
      val today = dateOfDaysAgo(0)
      assertEquals(listOf(testContext.packageName), dir.resolve(today).list()?.toList())
      assertTrue(dir.resolve("${today}/${testContext.packageName}/${today}.0.log").isFile)
    }
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
      val mark = logcatMark()
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

      // 目录为null是预期状态，不输出；获取目录出错才输出，每次获取输出一条
      val libLogs = libLogsSince(mark)
      if (throwOnGet) {
        assertEquals(libLogs.toString(), 4, libLogs.count { it == "lib java.io.IOException: directory error" })
      } else {
        assertEquals(emptyList<String>(), libLogs)
      }

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
