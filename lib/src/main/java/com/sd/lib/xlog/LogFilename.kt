package com.sd.lib.xlog

/**
 * 日志文件名
 */
internal interface LogFilename {
  /** 文件扩展名，不包含. */
  val extension: String

  /**
   * 返回时间戳[millis]对应的文件日期，不包含扩展名
   */
  fun dateOf(millis: Long): String

  /**
   * 返回指定年月日对应的文件日期，不包含扩展名
   */
  fun dateOf(year: Int, month: Int, dayOfMonth: Int): String

  /**
   * 计算[date1]和[date2]之间的天数差距，例如：
   * 20231125 和 20231125 天数差距为0，
   * 20231130 和 20231125 天数差距为5，
   * 20231125 和 20231130 天数差距为-5
   * 如果返回null，表示文件日期格式不合法
   */
  fun diffDays(date1: String, date2: String): Int?
}

internal fun defaultLogFilename(): LogFilename = LogFilenameImpl()

private class LogFilenameImpl(
  override val extension: String = "log",
) : LogFilename {
  override fun dateOf(millis: Long): String {
    return LogTime.dateOf(millis)
  }

  override fun dateOf(year: Int, month: Int, dayOfMonth: Int): String {
    return LogTime.dateOf(year = year, month = month, dayOfMonth = dayOfMonth)
  }

  override fun diffDays(date1: String, date2: String): Int? {
    val f1 = dateToInt(date1) ?: return null
    val f2 = dateToInt(date2) ?: return null
    return f1 - f2
  }

  private fun dateToInt(date: String): Int? {
    return date.toIntOrNull()?.takeIf { it > 0 }
  }
}