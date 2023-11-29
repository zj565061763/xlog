package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.fDebug
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
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

    @Test
    fun testOpenClose() {
        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.All)
            assertEquals(true, FLog.isOpened())
            var count = 0
            flogV<TestLogger> { count++ }
            flogD<TestLogger> { count++ }
            flogI<TestLogger> { count++ }
            flogW<TestLogger> { count++ }
            flogE<TestLogger> { count++ }
            assertEquals(5, count)
        }

        kotlin.run {
            FLog.close()
            assertEquals(false, FLog.isOpened())
            var count = 0
            flogV<TestLogger> { count++ }
            flogD<TestLogger> { count++ }
            flogI<TestLogger> { count++ }
            flogW<TestLogger> { count++ }
            flogE<TestLogger> { count++ }
            assertEquals(0, count)
        }
    }

    @Test
    fun testLogLevel() {
        // All
        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.All)
            var result = ""
            flogV<TestLogger> {
                result += "v"
                Unit
            }
            flogD<TestLogger> {
                result += "d"
                Unit
            }
            flogI<TestLogger> {
                result += "i"
                Unit
            }
            flogW<TestLogger> {
                result += "w"
                Unit
            }
            flogE<TestLogger> {
                result += "e"
                Unit
            }
            assertEquals("vdiwe", result)
        }

        // Verbose
        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.Verbose)
            var result = ""
            flogV<TestLogger> {
                result += "v"
                Unit
            }
            flogD<TestLogger> {
                result += "d"
                Unit
            }
            flogI<TestLogger> {
                result += "i"
                Unit
            }
            flogW<TestLogger> {
                result += "w"
                Unit
            }
            flogE<TestLogger> {
                result += "e"
                Unit
            }
            assertEquals("vdiwe", result)
        }

        // Debug
        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.Debug)
            var result = ""
            flogV<TestLogger> {
                result += "v"
                Unit
            }
            flogD<TestLogger> {
                result += "d"
                Unit
            }
            flogI<TestLogger> {
                result += "i"
                Unit
            }
            flogW<TestLogger> {
                result += "w"
                Unit
            }
            flogE<TestLogger> {
                result += "e"
                Unit
            }
            assertEquals("diwe", result)
        }

        // Info
        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.Info)
            var result = ""
            flogV<TestLogger> {
                result += "v"
                Unit
            }
            flogD<TestLogger> {
                result += "d"
                Unit
            }
            flogI<TestLogger> {
                result += "i"
                Unit
            }
            flogW<TestLogger> {
                result += "w"
                Unit
            }
            flogE<TestLogger> {
                result += "e"
                Unit
            }
            assertEquals("iwe", result)
        }

        // Warning
        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.Warning)
            var result = ""
            flogV<TestLogger> {
                result += "v"
                Unit
            }
            flogD<TestLogger> {
                result += "d"
                Unit
            }
            flogI<TestLogger> {
                result += "i"
                Unit
            }
            flogW<TestLogger> {
                result += "w"
                Unit
            }
            flogE<TestLogger> {
                result += "e"
                Unit
            }
            assertEquals("we", result)
        }

        // Error
        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.Error)
            var result = ""
            flogV<TestLogger> {
                result += "v"
                Unit
            }
            flogD<TestLogger> {
                result += "d"
                Unit
            }
            flogI<TestLogger> {
                result += "i"
                Unit
            }
            flogW<TestLogger> {
                result += "w"
                Unit
            }
            flogE<TestLogger> {
                result += "e"
                Unit
            }
            assertEquals("e", result)
        }
    }

    @Test
    fun testConsoleDebug() {
        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.All)
            var count = 0
            fDebug { count++ }
            assertEquals(1, count)
        }

        kotlin.run {
            FLog.close()
            FLog.open(_context, FLogLevel.Warning)
            var count = 0
            fDebug { count++ }
            assertEquals(0, count)
        }
    }

    @Test
    fun testConfig() {
        FLog.close()
        FLog.open(_context, FLogLevel.Info)

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
        FLog.close()
        FLog.open(_context, FLogLevel.All)

        val dir = FLog.logDirectory { it }!!

        dir.deleteRecursively()
        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        assertEquals(true, dir.exists())
        assertEquals(false, dir.listFiles()?.isEmpty())

        val today = SimpleDateFormat("yyyyMMdd").format(System.currentTimeMillis()).toInt()

        val file1 = dir.resolve("${today - 1}.xlog").apply { fCreateFile() }
        val file2 = dir.resolve("${today - 2}.xlog").apply { fCreateFile() }
        val file3 = dir.resolve("${today - 3}.xlog").apply { fCreateFile() }
        val file4 = dir.resolve("${today - 4}.xlog").apply { fCreateFile() }
        val file5 = dir.resolve("${today - 5}.xlog").apply { fCreateFile() }

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
        FLog.close()
        FLog.open(_context, FLogLevel.All)

        val dir = FLog.logDirectory { it }!!

        dir.deleteRecursively()
        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        assertEquals(true, dir.exists())
        assertEquals(false, dir.listFiles()?.isEmpty())

        dir.deleteRecursively()
        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        assertEquals(false, dir.exists())

        flogI<TestLogger> { "info" }
        assertEquals(true, dir.exists())
        assertEquals(false, dir.listFiles()?.isEmpty())
    }

    @Test
    fun testLogFileLimit() {
        FLog.close()
        FLog.open(_context, FLogLevel.All, limitMBPerDay = 1)

        val dir = FLog.logDirectory { it }!!

        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        assertEquals(true, dir.exists())
        assertEquals(false, dir.listFiles()?.isEmpty())

        dir.listFiles { _, name -> name.endsWith(".old") }.let { files ->
            assertEquals(0, files?.size)
        }

        val log = "1".repeat(800 * 1024)
        flogI<TestLogger> { log }

        dir.listFiles { _, name -> name.endsWith(".old") }.let { files ->
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