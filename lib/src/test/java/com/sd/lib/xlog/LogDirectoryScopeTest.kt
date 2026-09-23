package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileNotFoundException

/** [inputStreamOrNull] */
class LogDirectoryScopeTest {
  @get:Rule
  val folder = TemporaryFolder()

  /** 文件存在时正常打开 */
  @Test
  fun testExists() {
    val file = folder.newFile().apply { writeText("log") }
    assertEquals("log", file.inputStreamOrNull()?.use { it.readBytes().decodeToString() })
  }

  /** 文件在打开前被删除时返回null，打包时跳过 */
  @Test
  fun testDeleted() {
    val file = folder.newFile().apply { delete() }
    assertNull(file.inputStreamOrNull())
  }

  /** 文件存在但打不开时照常抛出，不能当作被删除跳过 */
  @Test(expected = FileNotFoundException::class)
  fun testOpenFailed() {
    folder.newFolder().inputStreamOrNull()
  }
}
