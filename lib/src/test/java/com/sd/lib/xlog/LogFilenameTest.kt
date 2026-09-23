package com.sd.lib.xlog

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** [LogFilename] */
class LogFilenameTest {
  private val _filename = defaultLogFilename()
  private val _defaultTimeZone: TimeZone = TimeZone.getDefault()

  @After
  fun tearDown() {
    TimeZone.setDefault(_defaultTimeZone)
  }

  /** 日志文件名带递增序号，序号大的是新文件 */
  @Test
  fun testLogNameOf() {
    assertEquals("20231125.0.log", _filename.logNameOf("20231125", 0))
    assertEquals("20231125.1.log", _filename.logNameOf("20231125", 1))
    assertEquals("20231125.10.log", _filename.logNameOf("20231125", 10))
    assertEquals("20231125.1000.log", _filename.logNameOf("20231125", 1000))
  }

  /** [LogFilename.logNameOf]和[LogFilename.seqOf]互为逆运算 */
  @Test
  fun testSeqOf() {
    for (seq in listOf(0, 1, 10, 999, 1000)) {
      assertEquals(seq, _filename.seqOf(_filename.logNameOf("20231125", seq)))
    }

    // 不是日志文件
    assertNull(_filename.seqOf(""))
    assertNull(_filename.seqOf("20231125"))
    assertNull(_filename.seqOf("20231125.0.zip"))
    assertNull(_filename.seqOf("20231125.0.log.1"))
    // 序号不是数字
    assertNull(_filename.seqOf("20231125.abc.log"))
    // 没有序号
    assertNull(_filename.seqOf("20231125.log"))
    // 旧版本的分片文件
    assertNull(_filename.seqOf("20231125.log.1"))
  }

  /** [LogFilename.diffDays]注释里的例子 */
  @Test
  fun testDiffDays() {
    assertEquals(0, _filename.diffDays("20231125", "20231125"))
    assertEquals(5, _filename.diffDays("20231130", "20231125"))
    assertEquals(-5, _filename.diffDays("20231125", "20231130"))
  }

  /** 跨月，历史上出过bug：跨月的时候日志被全部删除 */
  @Test
  fun testCrossMonth() {
    // 7月1号往前推1到5天，全部落在6月
    assertEquals(1, _filename.diffDays("20260701", "20260630"))
    assertEquals(2, _filename.diffDays("20260701", "20260629"))
    assertEquals(3, _filename.diffDays("20260701", "20260628"))
    assertEquals(4, _filename.diffDays("20260701", "20260627"))
    assertEquals(5, _filename.diffDays("20260701", "20260626"))

    // 上个月31天
    assertEquals(1, _filename.diffDays("20260601", "20260531"))
    // 上个月30天
    assertEquals(1, _filename.diffDays("20260501", "20260430"))
    // 上个月是2月，平年28天
    assertEquals(1, _filename.diffDays("20230301", "20230228"))
    // 上个月是2月，闰年29天
    assertEquals(1, _filename.diffDays("20240301", "20240229"))
    assertEquals(2, _filename.diffDays("20240301", "20240228"))
  }

  /** 跨年 */
  @Test
  fun testCrossYear() {
    assertEquals(1, _filename.diffDays("20240101", "20231231"))
    assertEquals(7, _filename.diffDays("20240101", "20231225"))
    assertEquals(-1, _filename.diffDays("20231231", "20240101"))
    assertEquals(366, _filename.diffDays("20250101", "20240101"))
    // 能被100整除但不能被400整除，不是闰年
    assertEquals(1, _filename.diffDays("19000301", "19000228"))
    // 能被400整除，是闰年
    assertEquals(2, _filename.diffDays("20000301", "20000228"))
  }

  /**
   * 夏令时，切换的那天不是24小时，
   * 如果用[Calendar]转毫秒相减再除以86400000，会少算一天。
   */
  @Test
  fun testDaylightSavingTime() {
    /**
     * [LogTime]的时区在首次使用时固定，先让它用原来的时区初始化，
     * 否则会一直用这里临时设置的时区，影响其他测试。
     */
    LogTime.dateOf(0)

    // diffDays不依赖时区，这里设置时区是为了防止改回基于Calendar的算法
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

    // 2024-03-10进入夏令时，这天只有23小时
    assertEquals(1, _filename.diffDays("20240311", "20240310"))
    assertEquals(2, _filename.diffDays("20240311", "20240309"))
    assertEquals(3, _filename.diffDays("20240312", "20240309"))

    // 2024-11-03退出夏令时，这天有25小时
    assertEquals(1, _filename.diffDays("20241104", "20241103"))
    assertEquals(2, _filename.diffDays("20241104", "20241102"))

    // 跨越整个夏令时区间
    assertEquals(238, _filename.diffDays("20241104", "20240311"))
  }

  /**
   * 遍历2020-01-01到2030-12-31的每一天，
   * 覆盖所有的月份边界、年份边界和闰年。
   */
  @Test
  fun testEveryDay() {
    val calendar = GregorianCalendar().apply {
      clear()
      set(2020, Calendar.JANUARY, 1)
    }

    val start = calendar.logDate()
    var previous = start
    var days = 0

    while (true) {
      calendar.add(Calendar.DAY_OF_MONTH, 1)
      if (calendar.get(Calendar.YEAR) > 2030) break

      val current = calendar.logDate()
      days++

      // 相邻两天的差距是1
      assertEquals("$current - $previous", 1, _filename.diffDays(current, previous))
      // 距离起始日期的差距是累计天数
      assertEquals("$current - $start", days, _filename.diffDays(current, start))

      previous = current
    }

    assertEquals(4017, days)
  }

  /**
   * 日期格式不合法返回null，
   * [FLog.deleteLog]会把这类文件当作垃圾删掉。
   */
  @Test
  fun testInvalidDate() {
    // 长度不是8
    assertNull(_filename.diffDays("20231125", ""))
    assertNull(_filename.diffDays("20231125", "2023112"))
    assertNull(_filename.diffDays("20231125", "202311250"))
    // 导出的压缩包
    assertNull(_filename.diffDays("20231125", "20231125.zip"))
    // 不是数字
    assertNull(_filename.diffDays("20231125", "2023112a"))
    assertNull(_filename.diffDays("20231125", "abcdefgh"))
    // 月份不合法
    assertNull(_filename.diffDays("20231125", "20230025"))
    assertNull(_filename.diffDays("20231125", "20231325"))
    // 日期不合法
    assertNull(_filename.diffDays("20231125", "20231100"))
    assertNull(_filename.diffDays("20231125", "20231132"))
    assertNull(_filename.diffDays("20231125", "20230431"))
    assertNull(_filename.diffDays("20231125", "20230230"))
    // 平年没有2月29号
    assertNull(_filename.diffDays("20231125", "20230229"))
    // 第一个参数不合法
    assertNull(_filename.diffDays("2023112a", "20231125"))
  }

  /**
   * [FLog.deleteLog]的判断逻辑[shouldDeleteLog]。
   * 这里模拟今天是7月1号，前5天全部跨到6月的场景。
   */
  @Test
  fun testDeleteLogRule() {
    val today = "20260701"

    fun shouldDelete(date: String, saveDays: Int): Boolean {
      return _filename.shouldDeleteLog(today = today, date = date, saveDays = saveDays)
    }

    // 保留今天和前面4天
    assertEquals(false, shouldDelete("20260701", 5))
    assertEquals(false, shouldDelete("20260630", 5))
    assertEquals(false, shouldDelete("20260629", 5))
    assertEquals(false, shouldDelete("20260628", 5))
    assertEquals(false, shouldDelete("20260627", 5))
    assertEquals(true, shouldDelete("20260626", 5))

    // 保留今天和前面2天
    assertEquals(false, shouldDelete("20260701", 3))
    assertEquals(false, shouldDelete("20260630", 3))
    assertEquals(false, shouldDelete("20260629", 3))
    assertEquals(true, shouldDelete("20260628", 3))
    assertEquals(true, shouldDelete("20260627", 3))
    assertEquals(true, shouldDelete("20260626", 3))

    // 只保留今天
    assertEquals(false, shouldDelete("20260701", 1))
    assertEquals(true, shouldDelete("20260630", 1))
    assertEquals(true, shouldDelete("20260629", 1))
    assertEquals(true, shouldDelete("20260628", 1))
    assertEquals(true, shouldDelete("20260627", 1))
    assertEquals(true, shouldDelete("20260626", 1))

    // 以后的日期不会被删除
    assertEquals(false, shouldDelete("20260702", 1))

    // 小于等于0删除全部，包括今天和以后的日期
    assertEquals(true, shouldDelete("20260701", 0))
    assertEquals(true, shouldDelete("20260702", 0))
    assertEquals(true, shouldDelete("20260701", -1))

    // 日期不合法的删除
    assertEquals(true, shouldDelete("abc", 5))
  }
}

/** 和[LogTime.dateOf]保持一致 */
private fun Calendar.logDate(): String {
  val year = get(Calendar.YEAR)
  val month = get(Calendar.MONTH) + 1
  val dayOfMonth = get(Calendar.DAY_OF_MONTH)
  return "${year}${month.pad()}${dayOfMonth.pad()}"
}

private fun Int.pad(): String = toString().padStart(length = 2, padChar = '0')
