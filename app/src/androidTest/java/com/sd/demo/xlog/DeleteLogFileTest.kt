package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat

/**
 * 删除日志文件
 */
@RunWith(AndroidJUnit4::class)
class DeleteLogFileTest {

    @Test
    fun test() {
        val dir = testLogDir
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