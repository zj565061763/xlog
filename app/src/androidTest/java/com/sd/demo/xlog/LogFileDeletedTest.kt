package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 日志文件被删除，重建测试
 */
@RunWith(AndroidJUnit4::class)
class LogFileDeletedTest {
    @Test
    fun test() {
        val dir = testLogDir
        FLog.setLevel(FLogLevel.All)

        dir.deleteRecursively()
        Assert.assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        Assert.assertEquals(true, dir.exists())
        Assert.assertEquals(false, dir.listFiles()?.isEmpty())

        dir.deleteRecursively()
        Assert.assertEquals(false, dir.exists())
        flogI<TestLogger> { "info" }
        Assert.assertEquals(true, dir.exists())
        Assert.assertEquals(false, dir.listFiles()?.isEmpty())
    }
}