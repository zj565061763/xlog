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

/**
 * 日志文件被删除，自动重建
 */
@RunWith(AndroidJUnit4::class)
class LogFileDeletedTest {

  @Test
  fun test() {
    FLog.setLevel(FLogLevel.All)
    val dir = resetLogDir()

    // 没有文件句柄了，这条日志会创建新文件
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(true, dir.exists())

    // 模拟文件被外部删除
    dir.deleteRecursively()
    assertEquals(false, dir.exists())

    /**
     * 这条日志写到已经被删除的文件句柄上，不会重建文件，
     * 要等到空闲回调发现文件不存在才会close()，
     * 所以这里必须单独等待，不能和下面那条日志一起提交
     */
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(false, dir.exists())

    // 上一步空闲回调已经close()，这条日志会重新创建文件
    flogI<TestLogger> { "info" }
    awaitLogIdle()

    assertEquals(true, dir.exists())
    assertEquals(false, dir.listFiles()?.isEmpty())
  }
}