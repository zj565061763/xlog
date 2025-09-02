package com.sd.lib.xlog

/**
 * 日志文件名
 */
internal interface LogFilename {
  /**
   * 返回时间戳[millis]对应的日志文件名，不包含扩展名
   */
  fun filenameOf(millis: Long): String

  /**
   * 计算[filename1]和[filename2]之间的天数差距，例如：
   * 20231125 和 20231125 天数差距为0，
   * 20231130 和 20231125 天数差距为5，
   * 20231125 和 20231130 天数差距为-5
   * 如果返回null，表示文件名不合法
   */
  fun diffDays(filename1: String, filename2: String): Int?
}

internal fun defaultLogFilename(): LogFilename = LogFilenameImpl()

private class LogFilenameImpl : LogFilename {
  override fun filenameOf(millis: Long): String {
    return LogTime.create(millis).dateString
  }

  override fun diffDays(filename1: String, filename2: String): Int? {
    val f1 = filenameToInt(filename1) ?: return null
    val f2 = filenameToInt(filename2) ?: return null
    return f1 - f2
  }

  private fun filenameToInt(filename: String): Int? {
    return filename.substringBefore(".").toIntOrNull()?.takeIf { it > 0 }
  }
}
