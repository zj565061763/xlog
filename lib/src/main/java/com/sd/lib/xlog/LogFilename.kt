package com.sd.lib.xlog

import java.util.Calendar

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
  private val _calendar = Calendar.getInstance()

  override fun dateOf(millis: Long): String {
    return LogTime.dateOf(millis)
  }

  override fun dateOf(year: Int, month: Int, dayOfMonth: Int): String {
    return LogTime.dateOf(year = year, month = month, dayOfMonth = dayOfMonth)
  }

  override fun diffDays(date1: String, date2: String): Int? {
    val time1 = parseDate(date1) ?: return null
    val time2 = parseDate(date2) ?: return null
    val diff = time1 - time2
    return (diff / (24 * 3600 * 1000L)).toInt()
  }

  private fun parseDate(date: String): Long? {
    if (date.length != 8) return null
    val year = date.substring(0, 4).toIntOrNull() ?: return null
    val month = date.substring(4, 6).toIntOrNull() ?: return null
    val day = date.substring(6, 8).toIntOrNull() ?: return null
    return with(_calendar) {
      set(year, month - 1, day, 0, 0, 0)
      set(Calendar.MILLISECOND, 0)
      timeInMillis
    }
  }
}
