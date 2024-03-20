package com.sd.lib.xlog

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 日志仓库
 */
interface FLogStore {
    /**
     * 添加日志
     */
    @Throws(Throwable::class)
    fun append(log: String)

    /**
     * 日志大小(单位B)
     */
    @Throws(Throwable::class)
    fun size(): Long

    /**
     * 关闭
     */
    @Throws(Throwable::class)
    fun close()

    /**
     * 日志仓库工厂
     */
    fun interface Factory {
        /**
         * 创建[file]对应的日志仓库
         */
        fun create(file: File): FLogStore
    }
}

internal fun defaultLogStore(file: File): FLogStore = FileLogStore(file)

private class FileLogStore(file: File) : FLogStore {
    private val _file = file
    private var _output: CounterOutputStream? = null

    override fun append(log: String) {
        val data = log.toByteArray()
        getOutput().run {
            write(data)
            flush()
        }
    }

    override fun size(): Long {
        return getOutput().written
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
                .buffered()
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

private fun File?.fCreateFile(): Boolean {
    if (this == null) return false
    if (this.isFile) return true
    if (this.isDirectory) this.deleteRecursively()
    this.parentFile?.mkdirs()
    return this.createNewFile()
}