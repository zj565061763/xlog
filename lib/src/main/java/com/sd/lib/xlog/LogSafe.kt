package com.sd.lib.xlog

import java.io.File

internal inline fun <R> libRunCatching(block: () -> R): Result<R> {
  return runCatching(block)
    .onFailure { e ->
      libLog("lib ${e.stackTraceToString()}")
    }
}

internal fun DirectoryLogPublisher.safePublisher(): DirectoryLogPublisher {
  return if (this is SafeLogPublisher) this else SafeLogPublisher(this)
}

private class SafeLogPublisher(
  private val instance: DirectoryLogPublisher,
) : DirectoryLogPublisher by instance {
  /** 获取目录会调用使用方提供的方法，抛异常时按取不到处理 */
  override val directory: File?
    get() = libRunCatching { instance.directory }.getOrNull()

  override fun publish(record: FLogRecord) {
    libRunCatching { instance.publish(record) }
  }

  override fun close() {
    libRunCatching { instance.close() }
  }

  override fun onIdle() {
    libRunCatching { instance.onIdle() }
  }
}