package com.sd.lib.xlog

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 日志仓库
 *
 * 所有方法都在调度线程上调用。
 * [close]之后仍可能继续调用[append]，实现需要支持重新打开。
 */
interface FLogStore {
  /** 追加日志 */
  @Throws(Throwable::class)
  fun append(log: String)

  /** 当前日志文件的总大小(单位B)，包括打开前已有的内容，用来判断是否切换文件 */
  @Throws(Throwable::class)
  fun size(): Long

  /** 关闭 */
  @Throws(Throwable::class)
  fun close()

  /** 日志仓库工厂 */
  fun interface Factory {
    /**
     * 创建[file]对应的日志仓库。
     * 只应写入[file]，写到其他文件的话，日志轮换时不会被删除。
     * [FLogStore.append]返回后[file]必须已经存在，否则每次空闲时都会被当作文件被外部删除而关闭。
     */
    fun create(file: File): FLogStore
  }
}

internal fun defaultLogStore(file: File): FLogStore = FileLogStore(file)

private class FileLogStore(file: File) : FLogStore {
  private val _file = file
  private var _output: CounterOutputStream? = null

  override fun append(log: String) {
    with(getOutput()) {
      write(log.toByteArray())
      flush()
    }
  }

  override fun size(): Long {
    return _output?.written ?: _file.length()
  }

  override fun close() {
    try {
      _output?.close()
    } finally {
      _output = null
    }
  }

  private fun getOutput(): CounterOutputStream {
    return _output ?: kotlin.run {
      _file.fCreateFile()
      FileOutputStream(_file, true)
        .let { CounterOutputStream(it, _file.length()) }
        .also { _output = it }
    }
  }
}

private class CounterOutputStream(output: OutputStream, length: Long) : OutputStream() {
  private val _output = output
  private var _written = length

  val written: Long get() = _written

  override fun write(b: Int) {
    _output.write(b)
    _written++
  }

  override fun write(buff: ByteArray) {
    _output.write(buff)
    _written += buff.size
  }

  override fun write(buff: ByteArray, off: Int, len: Int) {
    _output.write(buff, off, len)
    _written += len
  }

  override fun flush() {
    _output.flush()
  }

  override fun close() {
    _output.close()
  }
}

private fun File.fCreateFile(): Boolean {
  if (isFile) return true
  if (isDirectory) deleteRecursively()
  parentFile?.mkdirs()
  return createNewFile()
}