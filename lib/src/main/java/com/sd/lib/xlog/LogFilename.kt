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

  override fun diffDays(date1: String, date2: String): Int? {
    val day1 = epochDayOf(date1) ?: return null
    val day2 = epochDayOf(date2) ?: return null
    return (day1 - day2).toInt()
  }
}

/**
 * 把yyyyMMdd转为距离1970-01-01的天数，日期不合法返回null。
 * 这里用纯数值计算，不能用[java.util.Calendar]转毫秒再相减，
 * 因为夏令时切换的那天不是24小时，相减除以86400000会少算一天。
 */
private fun epochDayOf(date: String): Long? {
  if (date.length != 8) return null
  val year = date.substring(0, 4).toIntOrNull() ?: return null
  val month = date.substring(4, 6).toIntOrNull() ?: return null
  val dayOfMonth = date.substring(6, 8).toIntOrNull() ?: return null
  if (month !in 1..12) return null
  if (dayOfMonth !in 1..daysOfMonth(year = year, month = month)) return null
  return epochDay(year = year, month = month, dayOfMonth = dayOfMonth)
}

/** 公历年月日转距离1970-01-01的天数 */
private fun epochDay(year: Int, month: Int, dayOfMonth: Int): Long {
  // 把3月当作一年的开始，这样闰日落在年末，不需要判断当年是否闰年
  val y = (if (month <= 2) year - 1 else year).toLong()
  val era = (if (y >= 0) y else y - 399) / 400
  // 400年周期内的第几年[0, 399]
  val yearOfEra = y - era * 400
  // 从3月1日算起的第几天[0, 365]
  val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + dayOfMonth - 1
  // 400年周期内的第几天[0, 146096]
  val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
  // 400年共146097天，719468是0000-03-01到1970-01-01的天数
  return era * 146097 + dayOfEra - 719468
}

private fun daysOfMonth(year: Int, month: Int): Int {
  return when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 0
  }
}

private fun isLeapYear(year: Int): Boolean {
  return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}
