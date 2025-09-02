package com.sd.lib.xlog

import java.util.Calendar

internal interface LogTime {
  /** yyyyMMdd，例如：20231125 */
  val dateString: String

  /** HH:mm:ss.SSS，例如：18:18:18.888 */
  val timeString: String

  companion object {
    private val sCalendar = Calendar.getInstance()

    fun create(millis: Long): LogTime {
      return with(sCalendar) {
        timeInMillis = millis
        LogTimeModel(
          year = get(Calendar.YEAR),
          month = get(Calendar.MONTH) + 1,
          dayOfMonth = get(Calendar.DAY_OF_MONTH),
          hourOfDay = get(Calendar.HOUR_OF_DAY),
          minute = get(Calendar.MINUTE),
          second = get(Calendar.SECOND),
          millisecond = get(Calendar.MILLISECOND),
        )
      }
    }
  }
}

private data class LogTimeModel(
  val year: Int,
  val month: Int,
  val dayOfMonth: Int,
  val hourOfDay: Int,
  val minute: Int,
  val second: Int,
  val millisecond: Int,
) : LogTime {
  override val dateString: String
    get() = "${year}${month.leadingZero()}${dayOfMonth.leadingZero()}"

  override val timeString: String
    get() = "${hourOfDay.leadingZero()}:${minute.leadingZero()}:${second.leadingZero()}.${millisecond.leadingZero(3)}"
}

private fun Int.leadingZero(length: Int = 2): String {
  return toString().padStart(length = length, padChar = '0')
}