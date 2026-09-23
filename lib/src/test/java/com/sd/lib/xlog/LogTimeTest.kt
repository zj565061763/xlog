package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/** [LogTime] */
class LogTimeTest {
  /** 月、日、时、分、秒补齐2位，毫秒补齐3位 */
  @Test
  fun testLeadingZero() {
    val millis = GregorianCalendar(2026, Calendar.JANUARY, 5, 9, 5, 3)
      .apply { set(Calendar.MILLISECOND, 7) }
      .timeInMillis
    assertEquals("20260105", LogTime.dateOf(millis))
    assertEquals("09:05:03.007", LogTime.timeOf(millis))
  }

  /** 不需要补零的情况 */
  @Test
  fun testNoLeadingZero() {
    val millis = GregorianCalendar(2026, Calendar.DECEMBER, 25, 23, 59, 58)
      .apply { set(Calendar.MILLISECOND, 999) }
      .timeInMillis
    assertEquals("20261225", LogTime.dateOf(millis))
    assertEquals("23:59:58.999", LogTime.timeOf(millis))
  }
}
