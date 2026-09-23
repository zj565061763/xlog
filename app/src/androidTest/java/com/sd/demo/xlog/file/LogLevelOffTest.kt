package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.resetLogDir
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 日志等级设为Off后，调度器空闲时关闭日志文件 */
@RunWith(AndroidJUnit4::class)
class LogLevelOffTest {

  @Test
  fun test() {
    val dir = resetLogDir()
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(true, dir.exists())

    FLog.setLevel(FLogLevel.Off)
    awaitLogIdle()

    /**
     * 文件已经关闭的话，删除之后下一条日志会立即重建文件。
     * 如果文件没有关闭，这条日志会写到已被删除的句柄上，目录不会出现。
     */
    dir.deleteRecursively()
    FLog.setLevel(FLogLevel.All)
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(true, dir.exists())
  }
}
