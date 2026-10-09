package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [defaultLogStore] */
class LogStoreTest {
  @get:Rule
  val folder = TemporaryFolder()

  /** 写入之前查询大小不会创建文件 */
  @Test
  fun testSizeBeforeAppend() {
    val file = folder.root.resolve("dir").resolve("test.log")
    val store = defaultLogStore(file)
    assertEquals(0L, store.size())
    assertEquals(false, file.exists())
    assertEquals(false, file.parentFile?.exists())
  }

  /** 大小包括打开前已有的内容，关闭之后可以继续追加 */
  @Test
  fun testAppendAfterClose() {
    val file = folder.newFile().apply { writeText("old\n") }
    val store = defaultLogStore(file)
    assertEquals(4L, store.size())

    store.append("log\n")
    assertEquals(8L, store.size())

    store.close()
    store.append("log\n")
    assertEquals(12L, store.size())
    store.close()

    assertEquals("old\nlog\nlog\n", file.readText())
  }

  /** 关闭之后查询大小读取文件长度，包括关闭期间外部追加的内容 */
  @Test
  fun testSizeAfterClose() {
    val file = folder.newFile()
    val store = defaultLogStore(file)
    store.append("log\n")
    store.close()

    file.appendText("ext\n")
    assertEquals(8L, store.size())
  }

  /** 路径上的目录被同名文件占用时，删掉文件再创建目录，日志照常写入 */
  @Test
  fun testReplaceParentFile() {
    val occupied = folder.newFile("date")
    assertEquals(true, occupied.isFile)
    val file = occupied.resolve("process").resolve("test.log")

    val store = defaultLogStore(file)
    store.append("log\n")
    store.close()

    assertEquals(true, occupied.isDirectory)
    assertEquals("log\n", file.readText())
  }

  /** 日志路径被同名目录占用时，替换成文件再写入 */
  @Test
  fun testReplaceDirectory() {
    val file = folder.newFolder().apply { resolve("child").writeText("child") }
    assertEquals(true, file.isDirectory)

    val store = defaultLogStore(file)
    store.append("log\n")
    store.close()

    assertEquals(true, file.isFile)
    assertEquals("log\n", file.readText())
  }
}
