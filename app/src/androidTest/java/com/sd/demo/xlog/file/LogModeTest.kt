package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.log.AppLogger
import com.sd.demo.xlog.log.ConsoleLogger
import com.sd.demo.xlog.resetLogDir
import com.sd.demo.xlog.todayLogText
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogMode
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.li
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 日志模式、tag、线程ID和各种API写入日志文件的内容 */
@RunWith(AndroidJUnit4::class)
class LogModeTest {

  /** Console模式不写文件，调用时传入的模式优先于全局模式 */
  @Test
  fun testMode() {
    val dir = resetLogDir()

    FLog.setMode(FLogMode.Console)
    flogI<TestLogger> { "global console" }
    awaitLogIdle()
    assertEquals(false, dir.exists())

    flogI<TestLogger>(mode = FLogMode.Store) { "call store" }
    FLog.setMode(FLogMode.Store)
    flogI<TestLogger> { "global store" }
    flogI<TestLogger>(mode = FLogMode.Console) { "call console" }
    awaitLogIdle()

    val text = dir.todayLogText()
    assertEquals(listOf("call store", "global store"), text.lines().filter { it.isNotEmpty() }.map { it.substringAfter("] ") })
  }

  /** 配置的模式优先于全局模式，调用时传入的模式优先于配置 */
  @Test
  fun testConfigMode() {
    val dir = resetLogDir()
    FLog.setMode(FLogMode.Store)

    // App里给ConsoleLogger配置了Console模式
    flogI<ConsoleLogger> { "config console" }
    awaitLogIdle()
    assertEquals(false, dir.exists())

    flogI<ConsoleLogger>(mode = FLogMode.Store) { "call store" }
    awaitLogIdle()

    val text = dir.todayLogText()
    assertEquals(listOf("call store"), text.lines().filter { it.isNotEmpty() }.map { it.substringAfter("] ") })
  }

  /** 配置的tag优先于默认tag，默认tag是短类名 */
  @Test
  fun testTag() {
    val dir = resetLogDir()
    // App里给AppLogger配置了tag
    flogI<AppLogger> { "app" }
    flogI<TestLogger> { "test" }
    awaitLogIdle()

    val lines = dir.todayLogText().lines()
    assertEquals(true, lines[0].contains("[AppLoggerAppLogger|I|"))
    assertEquals(true, lines[1].contains("[TestLogger|I|"))
  }

  /** [FLogger]扩展API的日志标识是接收者的实际类型，匿名对象的tag是去掉包名的类名 */
  @Test
  fun testLoggerApi() {
    val dir = resetLogDir()
    val anonymous = object : FLogger {}
    ConcreteLogger().li { "concrete" }
    anonymous.li { "anonymous" }
    awaitLogIdle()

    val lines = dir.todayLogText().lines()
    assertTrue(lines[0], lines[0].contains("[ConcreteLogger|I|"))
    assertTrue(lines[1], lines[1].contains("[LogModeTest\$testLoggerApi\$anonymous\$1|I|"))
  }

  /** 主线程的日志省略线程ID，其他线程带上线程ID */
  @Test
  fun testThreadID() {
    val dir = resetLogDir()
    InstrumentationRegistry.getInstrumentation().runOnMainSync { flogI<TestLogger> { "main" } }
    flogI<TestLogger> { "other" }
    awaitLogIdle()

    val lines = dir.todayLogText().lines()
    assertTrue(lines[0], lines[0].endsWith("I] main"))
    assertTrue(lines[1], lines[1].endsWith("I|${Thread.currentThread().id}] other"))
  }

  /** Java API和Kotlin API写入的内容一致，消息为null或空串时忽略 */
  @Test
  fun testJavaApi() {
    val dir = resetLogDir()
    FLog.logI(TestLogger::class.java, msg = "java")
    FLog.logI(TestLogger::class.java, msg = null)
    FLog.logI(TestLogger::class.java, msg = "")
    FLog.logE(TestLogger::class.java, FLogMode.Console, "console")
    awaitLogIdle()

    val lines = dir.todayLogText().lines().filter { it.isNotEmpty() }
    assertEquals(1, lines.size)
    assertEquals(true, lines[0].contains("[TestLogger|I|"))
    assertEquals(true, lines[0].endsWith("] java"))
  }
}

/** 实现了[TestLogger]的类，用扩展API打印日志时，tag是它自己的短类名 */
private class ConcreteLogger : TestLogger
