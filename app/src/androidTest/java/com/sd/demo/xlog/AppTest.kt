package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.fDebug
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogI
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AppTest {
    private val _context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val _logDir get() = _context.filesDir.resolve("app_log")

    @Test
    fun testConsoleDebug() {
        kotlin.run {
            FLog.setLevel(FLogLevel.All)
            var count = 0
            fDebug { count++ }
            assertEquals(1, count)
        }

        kotlin.run {
            FLog.setLevel(FLogLevel.Warning)
            var count = 0
            fDebug { count++ }
            assertEquals(0, count)
        }
    }

    @Test
    fun testConfig() {
        FLog.setLevel(FLogLevel.Info)

        kotlin.run {
            var count = 0
            flogD<TestLogger> { count++ }
            assertEquals(0, count)
        }

        kotlin.run {
            FLog.config<TestLogger> {
                this.level = FLogLevel.Debug
            }
            var count = 0
            flogD<TestLogger> { count++ }
            assertEquals(1, count)
        }

        kotlin.run {
            FLog.config<TestLogger> {
                this.level = null
            }
            var count = 0
            flogD<TestLogger> { count++ }
            assertEquals(0, count)
        }
    }

    @Test
    fun testDeleteLogFile() {
        val dir = _logDir
        FLog.setLevel(FLogLevel.All)

        dir.deleteRecursively()
        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        assertEquals(true, dir.exists())
        assertEquals(false, dir.listFiles()?.isEmpty())

        val today = SimpleDateFormat("yyyyMMdd").format(System.currentTimeMillis()).toInt()

        val file1 = dir.resolve("${today - 1}.log").apply { fCreateFile() }
        val file2 = dir.resolve("${today - 2}.log").apply { fCreateFile() }
        val file3 = dir.resolve("${today - 3}.log").apply { fCreateFile() }
        val file4 = dir.resolve("${today - 4}.log").apply { fCreateFile() }
        val file5 = dir.resolve("${today - 5}.log").apply { fCreateFile() }

        assertEquals(true, file1.exists())
        assertEquals(true, file2.exists())
        assertEquals(true, file3.exists())
        assertEquals(true, file4.exists())
        assertEquals(true, file5.exists())

        kotlin.run {
            FLog.deleteLog(5)
            assertEquals(true, file1.exists())
            assertEquals(true, file2.exists())
            assertEquals(true, file3.exists())
            assertEquals(true, file4.exists())
            assertEquals(false, file5.exists())
        }

        kotlin.run {
            FLog.deleteLog(3)
            assertEquals(true, file1.exists())
            assertEquals(true, file2.exists())
            assertEquals(false, file3.exists())
            assertEquals(false, file4.exists())
            assertEquals(false, file5.exists())
        }

        kotlin.run {
            FLog.deleteLog(1)
            assertEquals(false, file1.exists())
            assertEquals(false, file2.exists())
            assertEquals(false, file3.exists())
            assertEquals(false, file4.exists())
            assertEquals(false, file5.exists())
        }

        kotlin.run {
            FLog.deleteLog(0)
            assertEquals(false, dir.exists())
        }
    }

    @Test
    fun testLogFileDeleted() {
        val dir = _logDir
        FLog.setLevel(FLogLevel.All)

        dir.deleteRecursively()
        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        assertEquals(true, dir.exists())
        assertEquals(false, dir.listFiles()?.isEmpty())

        dir.deleteRecursively()
        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        assertEquals(true, dir.exists())
        assertEquals(false, dir.listFiles()?.isEmpty())
    }

    @Test
    fun testLogFileLimit() {
        val dir = _logDir
        FLog.setLevel(FLogLevel.All)
        FLog.setLimitMBPerDay(1)

        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        assertEquals(true, dir.exists())
        assertEquals(false, dir.listFiles()?.isEmpty())

        dir.listFiles { _, name -> name.endsWith(".1") }.let { files ->
            assertEquals(0, files?.size)
        }

        val log = "1".repeat(800 * 1024)
        flogI<TestLogger> { log }

        dir.listFiles { _, name -> name.endsWith(".1") }.let { files ->
            assertEquals(1, files?.size)
        }
    }
}

private fun File?.fCreateFile(): Boolean {
    if (this == null) return false
    if (this.isFile) return true
    if (this.isDirectory) this.deleteRecursively()
    return this.parentFile.fMakeDirs() && this.createNewFile()
}

private fun File?.fMakeDirs(): Boolean {
    if (this == null) return false
    if (this.isDirectory) return true
    if (this.isFile) this.delete()
    return this.mkdirs()
}