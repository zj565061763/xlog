package com.sd.lib.xlog

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

interface FLogDirectoryScope {
  /**
   * 获取指定年月日(yyyyMMdd)的日志文件压缩包
   */
  fun logZipOf(date: String): File?

  /**
   * 获取指定年月日的日志文件压缩包
   */
  fun logZipOf(year: Int, month: Int, dayOfMonth: Int): File?
}

internal class LogDirectoryScopeImpl(
  private val publisher: DirectoryLogPublisher,
) : FLogDirectoryScope {
  private var _destroyed = false

  override fun logZipOf(date: String): File? {
    if (_destroyed) {
      libLog { "log zip failed with destroyed state" }
      return null
    }
    if (date.length != 8) return null
    val year = date.substring(0, 4).toIntOrNull() ?: return null
    val month = date.substring(4, 6).toIntOrNull() ?: return null
    val dayOfMonth = date.substring(6, 8).toIntOrNull() ?: return null
    return logZipOf(year = year, month = month, dayOfMonth = dayOfMonth)
  }

  override fun logZipOf(year: Int, month: Int, dayOfMonth: Int): File? {
    if (_destroyed) {
      libLog { "log zip failed with destroyed state" }
      return null
    }
    val files = publisher.logOf(year = year, month = month, dayOfMonth = dayOfMonth)
    if (files.isEmpty()) return null
    val zipFile = publisher.directory.resolve("${files.first().name.substringBefore(".")}.zip")
    val zipResult = files.zipTo(zipFile)
    libLog { "log zip ${zipFile.name} $zipResult" }
    return if (zipResult && zipFile.exists()) zipFile else null
  }

  fun destroy() {
    _destroyed = true
  }
}

private fun List<File>.zipTo(target: File): Boolean {
  try {
    if (this.isEmpty()) return false
    if (!target.deleteAndCreateNewFile()) return false
    ZipOutputStream(target.outputStream().buffered()).use { outputStream ->
      for (item in this) {
        compressFile(
          file = item,
          filename = item.name,
          outputStream = outputStream,
        )
      }
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