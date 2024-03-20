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
 * 限制日志文件大小
 */
@RunWith(AndroidJUnit4::class)
class LogFileLimitTest {

    @Test
    fun test() {
        val dir = testLogDir
        FLog.setLevel(FLogLevel.All)
        FLog.setLimitMBPerDay(1)

        dir.deleteRecursively()
        assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
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