package com.sd.lib.xlog

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 日志目录为[dir]、进程名为[process]的日志发布 */
internal fun newPublisher(
  dir: File,
  process: String? = null,
  storeFactory: FLogStore.Factory = FLogStore.Factory { defaultLogStore(it) },
): DirectoryLogPublisher {
  return defaultLogPublisher(
    processProvider = { process },
    directoryProvider = { dir },
    filename = defaultLogFilename(),
    formatter = defaultLogFormatter(),
    storeFactory = storeFactory,
  )
}

/** 测试日志记录的时间戳 */
internal const val RECORD_MILLIS = 1_700_000_000_000L

/** 测试用的日志记录，默认参数格式化之后约61字节 */
internal fun testLogRecord(
  tag: String = "T",
  level: FLogLevel = FLogLevel.Info,
  msg: String = "0123456789012345678901234567890123456789",
  millis: Long = RECORD_MILLIS,
  isMainThread: Boolean = false,
): FLogRecord = object : FLogRecord {
  override val logger: Class<out FLogger> = RecordLogger::class.java
  override val level: FLogLevel = level
  override val tag: String = tag
  override val msg: String = msg
  override val millis: Long = millis
  override val isMainThread: Boolean = isMainThread
  override val threadID: String = "1"
}

private interface RecordLogger : FLogger

/** 当前时间往前推[days]天的日期，负数表示以后的日期，格式和[LogTime.dateOf]一致 */
internal fun dateOfDaysAgo(days: Int): String {
  val calendar = calendarOfDaysAgo(days)
  val year = calendar.get(Calendar.YEAR)
  val month = calendar.get(Calendar.MONTH) + 1
  val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
  return "%04d%02d%02d".format(Locale.US, year, month, dayOfMonth)
}

/** 当前时间往前推[days]天的时间戳，负数表示以后的时间；按日历推算，夏令时切换日也落在对应的日期 */
internal fun millisOfDaysAgo(days: Int): Long = calendarOfDaysAgo(days).timeInMillis

private fun calendarOfDaysAgo(days: Int): Calendar = GregorianCalendar().apply { add(Calendar.DAY_OF_MONTH, -days) }

/**
 * 测试期间跨过零点时，失败的测试改为跳过。
 * 日志按打印时的日期分目录，跨天时会分到两个目录里，按单个文件或文件顺序断言会失败。
 */
class SameDayRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement = object : Statement() {
    override fun evaluate() {
      val date = dateOfDaysAgo(0)
      try {
        base.evaluate()
      } catch (e: Throwable) {
        if (date != dateOfDaysAgo(0)) throw AssumptionViolatedException("crossed midnight", e)
        throw e
      }
    }
  }
}

/** 单线程调度器，记录执行任务的线程，[await]等待已提交的任务执行完成 */
internal class AwaitDispatcher : FLogDispatcher {
  private val _executor = Executors.newSingleThreadExecutor()

  @Volatile
  var thread: Thread? = null
    private set

  override fun dispatch(task: Runnable) {
    _executor.execute {
      thread = Thread.currentThread()
      task.run()
    }
  }

  fun await(): Boolean {
    val latch = CountDownLatch(1)
    _executor.execute { latch.countDown() }
    return latch.await(10, TimeUnit.SECONDS)
  }
}
