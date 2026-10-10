package com.sd.lib.xlog

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

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

  /** 夏令时按时间戳动态计算偏移，切换日前后的日期和时间都正确 */
  @Test
  fun testDaylightSavingTime() {
    // 2024-03-10 02:00进入夏令时，时钟跳到03:00；UTC 06:59:59是本地01:59:59，再过1秒是03:00:00
    val enter = utcMillis(2024, Calendar.MARCH, 10, 6, 59, 59)
    assertEquals("20240310", LogTime.dateOf(enter))
    assertEquals("01:59:59.000", LogTime.timeOf(enter))
    assertEquals("03:00:00.000", LogTime.timeOf(enter + 1000))

    // 2024-11-03 02:00退出夏令时，时钟回到01:00；UTC 05:59:59是本地01:59:59，再过1秒是01:00:00
    val exit = utcMillis(2024, Calendar.NOVEMBER, 3, 5, 59, 59)
    assertEquals("01:59:59.000", LogTime.timeOf(exit))
    assertEquals("01:00:00.000", LogTime.timeOf(exit + 1000))
    assertEquals("20241103", LogTime.dateOf(exit + 1000))
  }

  /** 日期按本地时区计算，和UTC日期不同时以本地为准 */
  @Test
  fun testLocalDate() {
    // UTC 2024-07-01 03:00是本地2024-06-30 23:00
    val millis = utcMillis(2024, Calendar.JULY, 1, 3, 0, 0)
    assertEquals("20240630", LogTime.dateOf(millis))
    assertEquals("23:00:00.000", LogTime.timeOf(millis))
  }

  /** 时区在首次使用时固定，之后改时区不生效 */
  @Test
  fun testTimeZoneFixed() {
    val millis = utcMillis(2024, Calendar.JULY, 1, 3, 0, 0)
    assertEquals("23:00:00.000", LogTime.timeOf(millis))

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    try {
      assertEquals("20240630", LogTime.dateOf(millis))
      assertEquals("23:00:00.000", LogTime.timeOf(millis))
    } finally {
      TimeZone.setDefault(TimeZone.getTimeZone(TIME_ZONE))
    }
  }

  /** 固定用公历，默认日历是佛历时日期不变 */
  @Test
  fun testGregorianCalendar() {
    val millis = GregorianCalendar(2026, Calendar.JANUARY, 5).timeInMillis
    // 确认默认日历确实是佛历，否则下面什么也没验证
    assertEquals(2569, Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR))
    assertEquals("20260105", LogTime.dateOf(millis))
  }

  companion object {
    /** 有夏令时的时区 */
    private const val TIME_ZONE = "America/New_York"

    /** 泰语，默认日历是佛历，年份比公历多543 */
    private const val LOCALE = "th-TH"
    private lateinit var sDefaultTimeZone: TimeZone
    private lateinit var sDefaultLocale: Locale

    /** [LogTime]的日历在首次使用时创建，时区和locale要在这之前设置；每个测试类单独一个JVM，不影响其他测试 */
    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      sDefaultTimeZone = TimeZone.getDefault()
      sDefaultLocale = Locale.getDefault()
      TimeZone.setDefault(TimeZone.getTimeZone(TIME_ZONE))
      Locale.setDefault(Locale.forLanguageTag(LOCALE))
    }

    @JvmStatic
    @AfterClass
    fun tearDownClass() {
      TimeZone.setDefault(sDefaultTimeZone)
      Locale.setDefault(sDefaultLocale)
    }
  }
}

/** UTC时间对应的时间戳 */
private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
  return GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
    clear()
    set(year, month, day, hour, minute, second)
  }.timeInMillis
}
