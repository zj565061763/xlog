package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonTest {
    @Test
    fun test() {
        kotlin.run {
            FLog.setLevel(FLogLevel.All)
            var count = 0
            flogV<TestLogger> { count++ }
            flogD<TestLogger> { count++ }
            flogI<TestLogger> { count++ }
            flogW<TestLogger> { count++ }
            flogE<TestLogger> { count++ }

            FLog.dispatch {
                Assert.assertEquals(5, count)
            }
        }
    }
}