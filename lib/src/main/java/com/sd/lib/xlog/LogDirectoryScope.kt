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

/**
 * 关闭当前日志文件后，在日志目录上执行[block]，取不到目录时不执行。
 * [block]是外部传入的，抛异常只打印不往外抛，否则会导致App崩溃。
 */
internal fun DirectoryLogPublisher.accessDirectory(block: FLogDirectoryScope.(File) -> Unit) {
  close()
  val directory = directory ?: return
  val scope = LogDirectoryScopeImpl(this)
  try {
    libRunCatching { scope.block(directory) }
  } finally {
    scope.destroy()
  }
}

internal class LogDirectoryScopeImpl(
  private val publisher: DirectoryLogPublisher,
  /** 打开要打包的文件，测试时替换成打包期间会被追加的文件 */
  private val openFile: (File) -> FileInputStream? = { it.inputStreamOrNull() },
  /** 打开压缩包的临时文件，测试时替换成写入会失败的流 */
  private val openOutput: (File) -> OutputStream = { it.outputStream() },
  /** 把临时文件重命名为压缩包，测试时替换成重命名失败 */
  private val rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
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

    val dateDir = publisher.logDirOf(date) ?: return null
    if (!dateDir.isDirectory) return null

    val zipFile = publisher.zipFileOf(date) ?: return null
    val zipped = zip(
      source = dateDir,
      target = zipFile,
      openFile = openFile,
      openOutput = openOutput,
      rename = rename,
    )
    if (zipped && zipFile.exists()) return zipFile
    libLog("log zip ${zipFile.name} failed")
    return null
  }

  fun destroy() {
    _destroyed = true
  }
}

private fun zip(
  source: File,
  target: File,
  openFile: (File) -> FileInputStream?,
  openOutput: (File) -> OutputStream,
  rename: (File, File) -> Boolean,
): Boolean {
  /**
   * 先打包到临时文件，成功后再替换，替换前上次的同名压缩包一直是完整的。
   * 临时文件名随机生成，取不到进程名时多个进程共用压缩包目录，同时打包同一日期不会互相覆盖。
   */
  var tempFile: File? = null
  try {
    // 目录在调用方检查之后可能被其他进程删除，这时按打包失败处理，不生成没有任何条目的压缩包
    val items = source.listFilesOrNull() ?: return false
    target.parentFile?.mkdirs()
    tempFile = File.createTempFile("${target.name}.", ".tmp", target.parentFile)
    /**
     * 文件流单独关闭，不能只靠[ZipOutputStream]关闭：
     * 写入失败时，它关闭前的收尾写入会再次抛异常，不再关闭底层的流，句柄要等GC才释放。
     */
    openOutput(tempFile).use { output ->
      ZipOutputStream(output.buffered()).use { outputStream ->
        compressDirectory(items = items, filename = source.name, outputStream = outputStream, openFile = openFile)
      }
    }
    if (target.isDirectory) target.deleteRecursively()
    return rename(tempFile, target)
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
  openFile: (File) -> FileInputStream?,
) {
  when {
    file.isFile -> {
      // 列出之后可能被其他进程删除，比如日志滚动或者清理日志，这种文件跳过
      val input = openFile(file) ?: return
      input.use { inputStream ->
        // 其他进程可能还在追加，只复制打开时的长度，否则写入不比压缩慢时一直读不完
        val size = inputStream.channel.size()
        outputStream.putNextEntry(ZipEntry(filename))
        inputStream.copyLimitedTo(outputStream, size)
        outputStream.closeEntry()
      }
    }

    // 列出之前可能被其他进程删除，这种目录跳过
    file.isDirectory -> file.listFilesOrNull()?.also { items ->
      compressDirectory(items = items, filename = filename, outputStream = outputStream, openFile = openFile)
    }
  }
}

/** 写入目录[filename]的条目，再打包目录里的[items] */
private fun compressDirectory(
  items: Array<File>,
  filename: String,
  outputStream: ZipOutputStream,
  openFile: (File) -> FileInputStream?,
) {
  outputStream.putNextEntry(ZipEntry("${filename}/"))
  outputStream.closeEntry()
  items.forEach { item ->
    compressFile(
      file = item,
      filename = "${filename}/${item.name}",
      outputStream = outputStream,
      openFile = openFile,
    )
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
  return listFiles() ?: if (exists()) throw IOException("list $name failed") else null
}