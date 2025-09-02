package com.sd.lib.xlog

internal inline fun <R> libRunCatching(block: () -> R): Result<R> {
  return runCatching(block)
    .onFailure { e ->
      flogConsole { "lib ${e.stackTraceToString()}" }
    }
}

internal fun DirectoryLogPublisher.safePublisher(): DirectoryLogPublisher {
  return if (this is SafeLogPublisher) this else SafeLogPublisher(this)
}

private class SafeLogPublisher(
  private val instance: DirectoryLogPublisher,
) : DirectoryLogPublisher by instance {
  override fun publish(record: FLogRecord) {
    libRunCatching { instance.publish(record) }
  }

  override fun close() {
    libRunCatching { instance.close() }
  }
}