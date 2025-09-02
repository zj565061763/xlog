package com.sd.lib.xlog

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 日志调度器，实现类可以在任何线程上执行任务，但必须保证按提交顺序执行，一个执行完成再执行下一个
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