package com.sd.lib.xlog

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** 日期和时间格式化，只在调度线程上调用，[_calendar]不是线程安全的 */
internal object LogTime {
  /**
   * 固定用公历，不用[Calendar.getInstance]：JVM上它会按locale返回佛历等其他日历，日期会变成25661125这种。
   *
   * 时区在创建时确定，运行期间改时区不会反映到日志上，进程重启后恢复。
   * 这是有意不处理的：这种情况低频，
   * 而且[FLog.deleteLog]的today和日志文件的日期用的是同一个时区，保留策略不会判断错。
   *
   * 夏令时不受影响，[TimeZone]会按传入的时间戳动态计算偏移量。
   */
  private val _calendar = GregorianCalendar(TimeZone.getDefault())

  /** yyyyMMdd，例如：20231125 */
  fun dateOf(millis: Long): String {
    return with(_calendar) {
      setTimeInMillis(millis)
      val year = get(Calendar.YEAR)
      val month = get(Calendar.MONTH) + 1
      val dayOfMonth = get(Calendar.DAY_OF_MONTH)
      "${year}${month.leadingZero()}${dayOfMonth.leadingZero()}"
    }
  }

  /** HH:mm:ss.SSS，例如：18:18:18.888 */
  fun timeOf(millis: Long): String {
    return with(_calendar) {
      setTimeInMillis(millis)
      val hourOfDay = get(Calendar.HOUR_OF_DAY)
      val minute = get(Calendar.MINUTE)
      val second = get(Calendar.SECOND)
      val millisecond = get(Calendar.MILLISECOND)
      "${hourOfDay.leadingZero()}:${minute.leadingZero()}:${second.leadingZero()}.${millisecond.leadingZero(3)}"
    }
  }
}

private fun Int.leadingZero(length: Int = 2): String {
  return toString().padStart(length = length, padChar = '0')
}