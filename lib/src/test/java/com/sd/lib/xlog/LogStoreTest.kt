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
}
