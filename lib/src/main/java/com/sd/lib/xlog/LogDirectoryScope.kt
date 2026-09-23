package com.sd.lib.xlog

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
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
    if (!date.all { it.isDigit() }) return null

    val dateDir = publisher.logDirOf(date)
    if (!dateDir.isDirectory) return null

    val zipFile = publisher.zipFileOf(date)
    val zipResult = zip(source = dateDir, target = zipFile)
    libLog("log zip ${zipFile.name} $zipResult")
    return if (zipResult && zipFile.exists()) zipFile else null
  }

  fun destroy() {
    _destroyed = true
  }
}

private fun zip(source: File, target: File): Boolean {
  // 先打包到临时文件，成功后再替换，替换前上次的同名压缩包一直是完整的
  val tempFile = target.resolveSibling("${target.name}.tmp")
  try {
    if (!tempFile.deleteAndCreateNewFile()) return false
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
    tempFile.delete()
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
        outputStream.putNextEntry(ZipEntry(filename))
        inputStream.copyTo(outputStream)
        outputStream.closeEntry()
      }
    }

    file.isDirectory -> {
      outputStream.putNextEntry(ZipEntry("${filename}/"))
      outputStream.closeEntry()
      file.listFiles()?.forEach { item ->
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
internal fun File.inputStreamOrNull(): InputStream? {
  return try {
    inputStream()
  } catch (e: FileNotFoundException) {
    if (exists()) throw e
    null
  }
}

private fun File.deleteAndCreateNewFile(): Boolean {
  deleteRecursively()
  parentFile?.mkdirs()
  return createNewFile()
}