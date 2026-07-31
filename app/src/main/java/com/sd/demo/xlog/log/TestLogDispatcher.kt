package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogDispatcher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 测试使用的调度器。
 * 保持异步执行，行为和默认调度器一致（单线程，按提交顺序执行），
 * 额外提供[await]等待已经提交的任务执行完成
 */
object TestLogDispatcher : FLogDispatcher {
  private val _executor = Executors.newSingleThreadExecutor()

  override fun dispatch(task: Runnable) {
    _executor.execute(task)
  }

  /**
   * 等待已经提交的任务执行完成，返回true表示等待成功。
   *
   * 这里往同一个单线程执行器里排一个空任务，排在它前面的任务执行完了才会轮到它。
   * [dispatch]收到的task是日志库内部包装过的，包含了空闲回调，
   * 所以在这一层等待，等到的是包含空闲回调在内的全部逻辑。
   * 而且不需要调用日志库的任何API，不会对被测试的状态产生副作用
   */
  fun await(timeout: Long = 10, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
    val latch = CountDownLatch(1)
    _executor.execute { latch.countDown() }
    return latch.await(timeout, unit)
  }
}
