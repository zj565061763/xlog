package com.sd.lib.xlog

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 日志调度器，实现类可以在任何线程上执行任务，但是必须同时满足以下三点。
 *
 * 1. **每个任务有且只执行一次**，不能丢弃，也不能重复执行；
 * 2. **按提交顺序串行执行**，一个任务执行完成再执行下一个，任何时刻都不能有两个任务并发；
 * 3. **保证任务之间的内存可见性**，即前一个任务写入的数据，对后一个任务必须可见（happens-before）。
 *
 * 违反第1点：日志文件被外部删除时可能不再重建，日志等级设为[FLogLevel.Off]后也可能不再关闭文件。
 * 重复执行时，之后每次队列排空还会抛出异常，不一定是被重复执行的那个任务。
 *
 * 违反第2点：会并发访问[FLogFormatter]、[FLogStore]和库内部的状态，包括默认实现。
 * 后果是日志内容错乱、日期算错，同一个日志文件可能被重复打开。
 *
 * 违反第3点：同一个日志文件可能被重复打开，出现文件句柄泄漏、[FLog.setMaxMBPerDay]失效等问题。
 * 任务始终在同一个线程上执行，或者用[java.util.concurrent.ExecutorService]这类JDK线程池实现时，这一点天然满足；
 * 自己用队列+线程实现，或者让任务在多个线程之间轮转时，要自行保证跨线程的可见性。
 */
fun interface FLogDispatcher {
  /**
   * 提交任务[task]，执行要求见[FLogDispatcher]。
   * 不能抛异常；也不能打印日志，否则会递归调用直到栈溢出。
   */
  fun dispatch(task: Runnable)
}

internal fun defaultLogDispatcher(
  dispatcher: FLogDispatcher?,
  onIdle: () -> Unit,
): FLogDispatcher {
  return LogDispatcherWrapper(
    dispatcher = dispatcher ?: FLogDispatcher { SingleThreadExecutor.execute(it) },
    onIdle = onIdle,
  )
}

private class LogDispatcherWrapper(
  private val dispatcher: FLogDispatcher,
  private val onIdle: () -> Unit,
) : FLogDispatcher {
  private val _counter = AtomicInteger()
  override fun dispatch(task: Runnable) {
    _counter.incrementAndGet()
    dispatcher.dispatch {
      try {
        task.run()
      } finally {
        val count = _counter.decrementAndGet().also { check(it >= 0) { "task executed more than once." } }
        if (count == 0) onIdle()
      }
    }
  }
}

private val SingleThreadExecutor by lazy { Executors.newSingleThreadExecutor() }