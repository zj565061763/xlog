package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogStore
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max

private const val BUFFER_SIZE: Long = 128 * 1024
private const val INFO_BUFFER_SIZE: Long = 16

/**
 * 使用[MappedByteBuffer]映射的[FLogStore]，映射大小为[BUFFER_SIZE]，
 * 除了映射文件外会额外创建一个.info文件，用来保存映射所需的信息，该info文件也是采用[MappedByteBuffer]映射的，映射大小为[INFO_BUFFER_SIZE]
 */
class MMapLogStore(file: File) : FLogStore {
    private val _logFile = MmapFile(file)
    private val _infoFile = MmapFile(file.resolveSibling("${file.name}.mi"))

    private var _logSize = 0L

    override fun append(log: String) {
        val data = log.toByteArray()
        getBuffer(data.size).run {
            put(data)
            _logSize += data.size
            saveRemaining(remaining().toLong())
        }
    }

    override fun size(): Long {
        getBuffer(0)
        return _logSize
    }

    override fun close() {
        _logFile.close()
        _infoFile.close()
        _logSize = 0L
    }

    private fun getBuffer(appendSize: Int): MappedByteBuffer {
        val buffer = _logFile.getBuffer { channel ->
            createBuffer(
                channel = channel,
                remaining = getSavedRemaining(),
                bufferSize = max(BUFFER_SIZE, appendSize.toLong()),
            )
        }

        val remaining = buffer.remaining()
        return if (remaining >= appendSize) {
            buffer
        } else {
            // flush?
            _logFile.getBuffer(recreate = true) { channel ->
                createBuffer(
                    channel = channel,
                    remaining = remaining.toLong(),
                    bufferSize = max(BUFFER_SIZE, appendSize.toLong()),
                )
            }
        }
    }

    private fun createBuffer(
        channel: FileChannel,
        remaining: Long,
        bufferSize: Long,
    ): MappedByteBuffer {
        val size = channel.size()
        val checkedRemaining = if (remaining > size) {
            0
        } else {
            remaining.coerceAtLeast(0)
        }
        val position = (size - checkedRemaining).coerceAtLeast(0)
        return channel.map(FileChannel.MapMode.READ_WRITE, position, bufferSize).also {
            saveRemaining(it.remaining().toLong())
            _logSize = position
        }
    }

    private fun getSavedRemaining(): Long {
        return getInfoBuffer().getLong(0)
    }

    private fun saveRemaining(remaining: Long) {
        getInfoBuffer().putLong(0, remaining.coerceAtLeast(0))
    }

    private fun getInfoBuffer(): MappedByteBuffer {
        return _infoFile.getBuffer { channel ->
            channel.map(FileChannel.MapMode.READ_WRITE, 0, INFO_BUFFER_SIZE)
        }
    }
}

private class MmapFile(file: File) {
    private val _file = file
    private var _raf: RandomAccessFile? = null
    private var _buffer: MappedByteBuffer? = null

    fun getBuffer(
        recreate: Boolean = false,
        factory: (FileChannel) -> MappedByteBuffer,
    ): MappedByteBuffer {
        if (recreate) _buffer = null
        return _buffer ?: factory(getRaf().channel).also {
            _buffer = it
        }
    }

    fun close() {
        try {
            _raf?.close()
        } finally {
            _raf = null
            _buffer = null
        }
    }

    private fun getRaf(): RandomAccessFile {
        _raf?.let { return it }
        _file.fCreateFile()
        return RandomAccessFile(_file, "rw").also {
            _raf = it
        }
    }
}

private fun File?.fCreateFile(): Boolean {
    if (this == null) return false
    if (this.isFile) return true
    if (this.isDirectory) this.deleteRecursively()
    return this.parentFile.fMakeDirs() && this.createNewFile()
}

private fun File?.fMakeDirs(): Boolean {
    if (this == null) return false
    if (this.isDirectory) return true
    if (this.isFile) this.delete()
    return this.mkdirs()
}