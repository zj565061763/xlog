package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections

/**
 * [FLog.init]的扩展点接线：目录、格式化器、仓库工厂、调度器和logger配置传进去之后真的生效。
 *
 * 这个类会初始化[FLog]，而且初始化之后无法重置，[LogTest]依赖未初始化状态，
 * 所以lib的build.gradle.kts设置了每个测试类单独一个JVM。
 * 没有Context，用[ContextWrapper]代替，它的方法都返回默认值，进程名取不到，日志直接写在日期目录下。
 */
class LogInitTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val initThread = Thread.currentThread()
    val dispatcher = AwaitDispatcher()
    val directoryThreads = Collections.synchronizedList(mutableListOf<Thread>())
    val formatter = TagMsgFormatter()
    val storeFiles = Collections.synchronizedList(mutableListOf<File>())

    val init = FLog.init(ContextWrapper(null)) {
      setLogDirectory {
        directoryThreads.add(Thread.currentThread())
        dir
      }
      setLogFormatter(formatter)
      setLogStoreFactory { file ->
        storeFiles.add(file)
        defaultLogStore(file)
      }
      setLogDispatcher(dispatcher)
      configLogger(InitLogger::class.java) { it.copy(tag = "init") }
      configLogger(LevelLogger::class.java) { it.copy(level = FLogLevel.Warning) }
    }
    assertTrue(init)
    assertFalse(FLog.init(ContextWrapper(null)))

    // 1.6.0到2.0.0版本的内联代码调用的兼容方法，有配置时按配置的等级判断，没有时按全局等级
    assertTrue(FLog.isLoggable(InitLogger::class.java, FLogLevel.Verbose))
    assertFalse(FLog.isLoggable(LevelLogger::class.java, FLogLevel.Info))
    assertTrue(FLog.isLoggable(LevelLogger::class.java, FLogLevel.Warning))

    // init不在调用线程上获取目录
    assertEquals(emptyList<Thread>(), directoryThreads.filter { it == initThread })

    flogI<InitLogger> { "msg" }
    flogI<InitLogger> { "" }
    assertTrue(dispatcher.await())

    // 目录在调度线程上获取
    val dispatchThread = checkNotNull(dispatcher.thread)
    assertNotEquals(initThread, dispatchThread)
    assertEquals(listOf(dispatchThread), directoryThreads.distinct())

    // 仓库由工厂创建，写在设置的目录下；格式化器和配置的tag都生效，空消息被丢弃
    val logFile = storeFiles.single()
    assertTrue(logFile.path, logFile.startsWith(dir))
    assertEquals(listOf(logFile), dir.walkTopDown().filter { it.isFile }.toList())
    assertEquals("init:msg\n", logFile.readText())

    // 等级设为Off后空闲时关闭文件，格式化器被重置
    assertEquals(0, formatter.resetCount)
    FLog.setLevel(FLogLevel.Off)
    assertTrue(dispatcher.await())
    assertEquals(1, formatter.resetCount)
    // 全局等级为Off时兼容方法也忽略配置的等级
    assertFalse(FLog.isLoggable(LevelLogger::class.java, FLogLevel.Error))

    // 访问目录拿到的是设置的目录，能打包
    FLog.setLevel(FLogLevel.All)
    var received: File? = null
    var zip: File? = null
    FLog.logDirectory {
      received = it
      zip = logZipOf(logFile.parentFile!!.name)
    }
    assertTrue(dispatcher.await())
    assertEquals(dir, received)
    assertEquals(true, zip?.isFile)

    // 删除全部日志，目录本身和压缩包保留
    FLog.deleteLog(0)
    assertTrue(dispatcher.await())
    assertFalse(logFile.exists())
    assertTrue(dir.isDirectory)
    assertEquals(true, zip?.isFile)

    // 删除之后继续写，文件重建
    flogI<InitLogger> { "again" }
    assertTrue(dispatcher.await())
    assertNotNull(storeFiles.getOrNull(1))
    assertEquals("init:again\n", logFile.readText())

    // 配置里只有等级没有tag时用默认tag，等级不满足的不写入
    flogI<LevelLogger> { "dropped" }
    flogW<LevelLogger> { "level" }
    assertTrue(dispatcher.await())
    assertEquals("init:again\nLevelLogger:level\n", logFile.readText())
  }
}

private interface InitLogger : FLogger

/** 只配置了等级的日志标识 */
private interface LevelLogger : FLogger

/** 格式为 tag:msg，记录重置次数 */
private class TagMsgFormatter : FLogFormatter {
  var resetCount = 0
    private set

  override fun format(record: FLogRecord): String = "${record.tag}:${record.msg}\n"

  override fun reset() {
    resetCount++
  }
}
