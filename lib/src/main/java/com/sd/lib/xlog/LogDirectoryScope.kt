package com.sd.lib.xlog

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 日志目录作用域，只在[FLog.logDirectory]的block内有效 */
interface FLogDirectoryScope {
  /**
   * 把指定日期(yyyyMMdd)的日志打包成zip，包含该日期所有进程的日志。
   * 日期不合法、没有该日期的日志目录或打包失败时返回null。
   * 日志目录存在但里面没有日志文件时，返回不含日志的压缩包。
   *
   * 压缩包只在本次进程运行期间有效，下次[FLog.init]时会被清空，需要长期保存请自行移走。
   * 同一日期再次打包会替换上次的压缩包，打包失败时保留上次的。
   */
  fun logZipOf(date: String): File?
}

internal class LogDirectoryScopeImpl(
  private val publisher: DirectoryLogPublisher,
) : FLogDirectoryScope {
  @Volatile
  private var _destroyed = false

  override fun logZipOf(date: String): File? {
    if (_destroyed) {
      libLog("log zip failed with destroyed state")
      return null
    }

    if (date.length != 8) return null
    if (!date.isAsciiDigits()) return null

    val dateDir = publisher.logDirOf(date)
    if (!dateDir.isDirectory) return null

    val zipFile = publisher.zipFileOf(date)
    if (zip(source = dateDir, target = zipFile) && zipFile.exists()) return zipFile
    libLog("log zip ${zipFile.name} failed")
    return null
  }

  fun destroy() {
    _destroyed = true
  }
}

private fun zip(source: File, target: File): Boolean {
  /**
   * 先打包到临时文件，成功后再替换，替换前上次的同名压缩包一直是完整的。
   * 临时文件名随机生成，取不到进程名时多个进程共用压缩包目录，同时打包同一日期不会互相覆盖。
   */
  var tempFile: File? = null
  try {
    target.parentFile?.mkdirs()
    tempFile = File.createTempFile("${target.name}.", ".tmp", target.parentFile)
    ZipOutputStream(tempFile.outputStream().buffered()).use { outputStream ->
      compressFile(file = source, filename = source.name, outputStream = outputStream)
    }
    if (target.isDirectory) target.deleteRecursively()
    return tempFile.renameTo(target)
  } catch (e: Throwable) {
    libLog("log zip error ${e.stackTraceToString()}")
    return false
  } finally {
    // 失败时临时文件不完整，成功时已经被重命名，删除不影响结果
    tempFile?.delete()
  }
}

private fun compressFile(
  file: File,
  filename: String,
  outputStream: ZipOutputStream,
) {
  when {
    file.isFile -> {
      // 列出之后可能被其他进程删除，比如日志滚动或者清理日志，这种文件跳过
      val input = file.inputStreamOrNull() ?: return
      input.use { inputStream ->
        // 其他进程可能还在追加，只复制打开时的长度，否则写入不比压缩慢时一直读不完
        val size = inputStream.channel.size()
        outputStream.putNextEntry(ZipEntry(filename))
        inputStream.copyLimitedTo(outputStream, size)
        outputStream.closeEntry()
      }
    }

    file.isDirectory -> {
      outputStream.putNextEntry(ZipEntry("${filename}/"))
      outputStream.closeEntry()
      // 列出之前可能被其他进程删除，这种目录跳过
      file.listFilesOrNull()?.forEach { item ->
        compressFile(
          file = item,
          filename = "${filename}/${item.name}",
          outputStream = outputStream,
        )
      }
    }
  }
}

/** 打开文件，文件已经不存在时返回null，其他原因打开失败照常抛出 */
internal fun File.inputStreamOrNull(): FileInputStream? {
  return try {
    inputStream()
  } catch (e: FileNotFoundException) {
    if (exists()) throw e
    null
  }
}

/** 最多复制[limit]字节到[out]，提前读到末尾时停止 */
internal fun InputStream.copyLimitedTo(out: OutputStream, limit: Long) {
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  var remaining = limit
  while (remaining > 0) {
    val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
    if (count < 0) break
    out.write(buffer, 0, count)
    remaining -= count
  }
}

/** 列出目录内容，目录已经不存在时返回null，其他原因读取失败照常抛出 */
internal fun File.listFilesOrNull(): Array<File>? {
  return listFiles() ?: if (exists()) throw IOException("list ${name} failed") else null
}