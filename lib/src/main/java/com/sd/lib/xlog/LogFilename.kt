package com.sd.lib.xlog

/** 日志文件名 */
internal interface LogFilename {
  /** 返回时间戳[millis]对应的日期，格式yyyyMMdd */
  fun dateOf(millis: Long): String

  /**
   * 计算[date1]和[date2]之间的天数差距，例如：
   * 20231125 和 20231125 天数差距为0；
   * 20231130 和 20231125 天数差距为5；
   * 20231125 和 20231130 天数差距为-5；
   * 日期格式不合法返回null。
   */
  fun diffDays(date1: String, date2: String): Int?

  /**
   * 返回[date]和序号[seq]对应的日志文件名，例如：20231125.0.log。
   * 序号递增，序号大的是新文件。
   */
  fun logNameOf(date: String, seq: Int): String

  /** 从日志文件名[logName]中解析出序号，不是合法的日志文件名返回null */
  fun seqOf(logName: String): Int?
}

/**
 * 日期为[date]的日志是否要删除，[today]是今天的日期，[saveDays]是要保留的天数。
 * [saveDays]小于等于0时全部删除，日期不合法的也删除。
 *
 * 日期在今天之后的会保留，比如设备时间曾被调快又恢复。
 * 不删是因为当前时间被调慢的时候，这些才是真实的日志。
 */
internal fun LogFilename.shouldDeleteLog(today: String, date: String, saveDays: Int): Boolean {
  if (saveDays <= 0) return true
  val diffDays = diffDays(today, date) ?: return true
  return diffDays > saveDays - 1
}

internal fun defaultLogFilename(): LogFilename = LogFilenameImpl()

private class LogFilenameImpl(
  private val extension: String = "log",
) : LogFilename {
  override fun dateOf(millis: Long): String {
    return LogTime.dateOf(millis)
  }

  override fun diffDays(date1: String, date2: String): Int? {
    val day1 = epochDayOf(date1) ?: return null
    val day2 = epochDayOf(date2) ?: return null
    return (day1 - day2).toInt()
  }

  override fun logNameOf(date: String, seq: Int): String {
    require(date.isNotEmpty())
    require(seq >= 0)
    return "${date}.${seq}.${extension}"
  }

  override fun seqOf(logName: String): Int? {
    val suffix = ".${extension}"
    if (!logName.endsWith(suffix)) return null

    val body = logName.dropLast(suffix.length)
    val index = body.lastIndexOf('.')
    if (index < 0) return null

    val seq = body.substring(index + 1).toIntOrNull() ?: return null
    return if (seq >= 0) seq else null
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
