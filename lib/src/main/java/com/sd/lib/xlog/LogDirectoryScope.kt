package com.sd.lib.xlog

import java.io.File

interface FLogDirectoryScope {
  /**
   * 获取指定年月日的日志文件
   */
  fun logOf(year: Int, month: Int, dayOfMonth: Int): List<File>
}

internal class LogDirectoryScopeImpl(
  private val publisher: DirectoryLogPublisher,
) : FLogDirectoryScope {
  override fun logOf(year: Int, month: Int, dayOfMonth: Int): List<File> {
    return publisher.logOf(year = year, month = month, dayOfMonth = dayOfMonth)
  }
}