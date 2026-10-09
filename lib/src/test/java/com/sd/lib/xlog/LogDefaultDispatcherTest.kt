package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 不设置调度器时用默认调度器：日志在同一个后台线程上按顺序写入，空闲时关闭日志文件，文件被删除后重建。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogDefaultDispatcherTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val storeFactory = SignalStoreFactory()
    assertTrue(FLog.init(ContextWrapper(null)) {
      setLogDirectory { dir }
      setLogStoreFactory(storeFactory)
    })

    // 在同一个后台线程上按顺序写入，不丢失
    val count = 200
    repeat(count) { index -> flogI<DefaultDispatcherLogger>(FLogMode.Store) { "msg${index}" } }
    assertTrue(storeFactory.awaitAppend(count))
    val logFile = dir.walkTopDown().single { it.isFile }
    assertEquals((0 until count).map { "msg${it}" }, logFile.readLines().map { it.substringAfter("] ") })
    assertNotSame(Thread.currentThread(), storeFactory.threads.single())

    // 文件还在时空闲不关闭，等级设为Off后空闲时关闭
    assertEquals(0, storeFactory.closeCount)
    FLog.setLevel(FLogLevel.Off)
    assertTrue(storeFactory.awaitClose())

    // 文件打开期间被删除，这条日志写完后空闲时关闭
    FLog.setLevel(FLogLevel.All)
    storeFactory.deleteAfterAppend = true
    flogI<DefaultDispatcherLogger>(FLogMode.Store) { "lost" }
    assertTrue(storeFactory.awaitAppend())
    assertTrue(storeFactory.awaitClose())
    assertFalse(logFile.exists())

    // 下一条日志重建文件，首条带tag
    flogI<DefaultDispatcherLogger>(FLogMode.Store) { "rebuilt" }
    assertTrue(storeFactory.awaitAppend())
    val lines = logFile.readLines()
    assertEquals(listOf("rebuilt"), lines.map { it.substringAfter("] ") })
    assertTrue(lines[0], lines[0].contains("[DefaultDispatcherLogger|"))
    assertEquals(1, storeFactory.threads.size)
    assertEquals(2, storeFactory.closeCount)
  }
}

private interface DefaultDispatcherLogger : FLogger

/** 用默认仓库写文件，记录写入的线程和关闭次数，[awaitAppend]、[awaitClose]等待写入和关闭完成 */
private class SignalStoreFactory : FLogStore.Factory {
  private val _appends = Semaphore(0)
  private val _closes = Semaphore(0)

  val threads: MutableSet<Thread> = Collections.synchronizedSet(mutableSetOf())

  @Volatile
  var closeCount = 0
    private set

  /** 为true时，下一条日志写入后删除日志文件，模拟文件打开期间被外部删除 */
  @Volatile
  var deleteAfterAppend = false

  override fun create(file: File): FLogStore {
    val store = defaultLogStore(file)
    return object : FLogStore by store {
      override fun append(log: String) {
        store.append(log)
        threads.add(Thread.currentThread())
        if (deleteAfterAppend) {
          deleteAfterAppend = false
          check(file.delete())
        }
        _appends.release()
      }

      override fun close() {
        store.close()
        closeCount++
        _closes.release()
      }
    }
  }

  fun awaitAppend(count: Int = 1): Boolean = _appends.tryAcquire(count, 10, TimeUnit.SECONDS)

  fun awaitClose(): Boolean = _closes.tryAcquire(10, TimeUnit.SECONDS)
}
