package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogLevelTest {

    @Test
    fun testAll() {
        FLog.setLevel(FLogLevel.All)
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

    @Test
    fun testVerbose() {
        FLog.setLevel(FLogLevel.Verbose)
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

    @Test
    fun testDebug() {
        FLog.setLevel(FLogLevel.Debug)
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

    @Test
    fun testInfo() {
        FLog.setLevel(FLogLevel.Info)
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

    @Test
    fun testWarning() {
        FLog.setLevel(FLogLevel.Warning)
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

    @Test
    fun testError() {
        FLog.setLevel(FLogLevel.Error)
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