package com.sd.lib.xlog

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

interface FLogDirectoryScope {
  /**
   * 获取指定年月日的日志文件压缩包
   */
  fun logZipOf(year: Int, month: Int, dayOfMonth: Int): File
}

internal class LogDirectoryScopeImpl(
  private val publisher: DirectoryLogPublisher,
) : FLogDirectoryScope {
  override fun logZipOf(year: Int, month: Int, dayOfMonth: Int): File {
    val logFilename = publisher.filename.filenameOf(year = year, month = month, dayOfMonth = dayOfMonth)
    val zipFile = publisher.directory.resolve("${logFilename}.zip")
    val zipResult = publisher.logOf(year = year, month = month, dayOfMonth = dayOfMonth).fZipTo(zipFile)
    libLog { "lib log zip ${zipFile.name} $zipResult" }
    return zipFile
  }
}

private fun List<File>.fZipTo(target: File): Boolean {
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
    libLog { "lib log zip error ${e.stackTraceToString()}" }
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