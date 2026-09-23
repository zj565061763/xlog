package com.sd.lib.xlog

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 日志目录作用域，只在[FLog.logDirectory]的block内有效 */
interface FLogDirectoryScope {
  /**
   * 把指定日期(yyyyMMdd)的日志打包成zip，包含该日期所有进程的日志。
   * 日期不合法、没有该日期的日志或打包失败时返回null。
   *
   * 压缩包只在本次进程运行期间有效，下次[FLog.init]时会被清空，需要长期保存请自行移走。
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
      libLog { "log zip failed with destroyed state" }
      return null
    }

    if (date.length != 8) return null
    if (!date.all { it.isDigit() }) return null

    val dateDir = publisher.logDirOf(date)
    if (!dateDir.exists()) return null

    val zipFile = publisher.zipFileOf(date)
    val zipResult = zip(source = dateDir, target = zipFile)
    libLog { "log zip ${zipFile.name} $zipResult" }
    return if (zipResult && zipFile.exists()) zipFile else null
  }

  fun destroy() {
    _destroyed = true
  }
}

private fun zip(source: File, target: File): Boolean {
  try {
    if (!target.deleteAndCreateNewFile()) return false
    ZipOutputStream(target.outputStream().buffered()).use { outputStream ->
      compressFile(file = source, filename = source.name, outputStream = outputStream)
    }
    return true
  } catch (e: Throwable) {
    libLog { "log zip error ${e.stackTraceToString()}" }
    return false
  }
}

private fun compressFile(
  file: File,
  filename: String,
  outputStream: ZipOutputStream,
) {
  when {
    file.isFile -> {
      outputStream.putNextEntry(ZipEntry(filename))
      file.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
      outputStream.closeEntry()
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

private fun File.deleteAndCreateNewFile(): Boolean {
  deleteRecursively()
  parentFile?.mkdirs()
  return createNewFile()
}