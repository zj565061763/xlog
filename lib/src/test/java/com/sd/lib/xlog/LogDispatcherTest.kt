package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** [FLogDispatcher]的契约 */
class LogDispatcherTest {

  /** 提交的任务全部执行完成之后触发空闲回调 */
  @Test
  fun testOnIdle() {
    var idleCount = 0
    val dispatcher = defaultLogDispatcher(
      dispatcher = FLogDispatcher { it.run() },
      onIdle = { idleCount++ },
    )

    dispatcher.dispatch { }
    assertEquals(1, idleCount)

    dispatcher.dispatch { }
    assertEquals(2, idleCount)
  }

  /** 任务本身抛异常，不影响计数，空闲回调照常触发，异常继续往外抛 */
  @Test
  fun testTaskError() {
    var idleCount = 0
    val dispatcher = defaultLogDispatcher(
      dispatcher = FLogDispatcher { it.run() },
      onIdle = { idleCount++ },
    )

    val error = RuntimeException("task error")
    val thrown = assertThrows(RuntimeException::class.java) {
      dispatcher.dispatch { throw error }
    }

    assertSame(error, thrown)
    assertEquals(1, idleCount)
  }

  /**
   * 调度器违反契约，同一个任务执行了多次，
   * 调度器是整个日志库的基础，这种情况必须抛异常尽早暴露。
   */
  @Test
  fun testTaskExecutedTwice() {
    val dispatcher = defaultLogDispatcher(
      dispatcher = FLogDispatcher {
        it.run()
        it.run()
      },
      onIdle = { },
    )

    val thrown = assertThrows(IllegalStateException::class.java) {
      dispatcher.dispatch { }
    }
    assertEquals("task executed more than once.", thrown.message)
  }

  /**
   * 任务本身也抛异常的时候，抛出来的仍然是契约违反的异常，
   * 会覆盖掉任务的异常，这是有意的：
   * 调度器的问题更底层，要先解决它，业务本身的异常之后再单独排查。
   */
  @Test
  fun testTaskExecutedTwiceWithTaskError() {
    val dispatcher = defaultLogDispatcher(
      // 吞掉第一次的异常，再执行一次，制造出任务和契约同时出错的场景
      dispatcher = FLogDispatcher {
        runCatching { it.run() }
        it.run()
      },
      onIdle = { },
    )

    val thrown = assertThrows(IllegalStateException::class.java) {
      dispatcher.dispatch { throw RuntimeException("task error") }
    }
    assertEquals("task executed more than once.", thrown.message)
  }
}
