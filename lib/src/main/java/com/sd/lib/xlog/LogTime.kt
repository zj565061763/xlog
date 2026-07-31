package com.sd.lib.xlog

import java.util.Calendar

internal object LogTime {
  private val _calendar = Calendar.getInstance()

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