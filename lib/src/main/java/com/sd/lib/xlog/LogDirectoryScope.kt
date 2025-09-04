package com.sd.lib.xlog

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

interface FLogDirectoryScope {
  /**
   * 获取指定年月日的日志文件
   */
  fun logOf(year: Int, month: Int, dayOfMonth: Int): List<File>

  /**
   * 获取指定年月日的日志文件压缩包
   */
  fun logZipOf(year: Int, month: Int, dayOfMonth: Int): File
}

internal class LogDirectoryScopeImpl(
  private val publisher: DirectoryLogPublisher,
) : FLogDirectoryScope {
  override fun logOf(year: Int, month: Int, dayOfMonth: Int): List<File> {
    return publisher.logOf(year = year, month = month, dayOfMonth = dayOfMonth)
  }

  override fun logZipOf(year: Int, month: Int, dayOfMonth: Int): File {
    val date = publisher.filename.filenameOf(year = year, month = month, dayOfMonth = dayOfMonth)
    val zipFile = publisher.directory.resolve("${date}.zip")
    val zipResult = logOf(year = year, month = month, dayOfMonth = dayOfMonth).toTypedArray().fZipTo(zipFile)
    libLog { "logZipOf $date $zipResult" }
    return zipFile
  }
}

private fun Array<File>?.fZipTo(target: File): Boolean {
  try {
    if (this.isNullOrEmpty()) return false
    if (!target.fCreateNewFile()) return false
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
  } catch (e: IOException) {
    e.printStackTrace()
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
          filename = filename + File.separator + item.name,
          outputStream = outputStream,
        )
      }
    }
  }
}

private fun File.fCreateNewFile(): Boolean {
  try {
    this.deleteRecursively()
    this.parentFile?.mkdirs()
    return this.createNewFile()
  } catch (e: IOException) {
    e.printStackTrace()
    return false
  }
}