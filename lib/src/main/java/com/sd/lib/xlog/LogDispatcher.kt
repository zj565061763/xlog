package com.sd.lib.xlog

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 日志调度器，实现类可以在任何线程上执行任务，但是必须同时满足以下两点：
 *
 * 1. **按提交顺序串行执行**，一个任务执行完成再执行下一个，任何时刻都不能有两个任务并发；
 * 2. **保证任务之间的内存可见性**，即前一个任务写入的数据，对后一个任务必须可见（happens-before）。
 *
 * 第2点容易被忽略：日志库内部的可变状态（当前打开的日志文件、已写入的字节数、上一条日志的tag等）
 * 都没有加volatile，也没有加锁，完全依赖调度器提供的happens-before。
 * 如果任务始终在同一个线程上执行，或者用[java.util.concurrent.ExecutorService]这类
 * JDK线程池实现（默认实现就是单线程线程池），这一点天然满足，不需要额外处理。
 * 但如果自己用队列+线程实现，或者让任务在多个线程之间轮转，就必须自行保证跨线程的可见性，
 * 否则后一个任务可能读到过期的状态，导致同一个日志文件被重复打开（文件句柄泄漏、
 * 文件大小统计错乱进而[FLog.setMaxMBPerDay]失效）等问题。
 */
fun interface FLogDispatcher {
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