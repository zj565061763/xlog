package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.log.logObfuscation
import com.sd.demo.xlog.resetLogDir
import com.sd.demo.xlog.todayLogText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R8混淆后嵌套类、局部类和匿名类的默认tag和未混淆时一致。
 * 测试跑在minified构建上（app的testBuildType），外部类ObfuscationLoggers没有被keep。
 */
@RunWith(AndroidJUnit4::class)
class LogObfuscationTest {

  @Test
  fun test() {
    // 外部类已经被重命名或移除，确认确实混淆了，否则这个测试什么也没验证
    assertThrows(ClassNotFoundException::class.java) { Class.forName("com.sd.demo.xlog.log.ObfuscationLoggers") }

    val dir = resetLogDir()
    logObfuscation()
    awaitLogIdle()

    val lines = dir.todayLogText().lines().filter { it.isNotEmpty() }
    val tags = lines.map { it.substringAfter("[").substringBefore("|") }
    assertEquals(listOf("nested", "local", "anonymous"), lines.map { it.substringAfter("] ") })
    assertEquals(listOf("ObfuscationLoggers\$NestedLogger", "ObfuscationLoggers\$log\$LocalLogger"), tags.take(2))
    // 匿名类的编号由编译器决定，只断言编号前面的部分
    assertTrue(tags[2], Regex("ObfuscationLoggers\\\$log\\\$\\d+").matches(tags[2]))
  }
}
