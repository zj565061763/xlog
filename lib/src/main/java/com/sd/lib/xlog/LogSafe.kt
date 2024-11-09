package com.sd.lib.xlog

internal inline fun <R> libRunCatching(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        fDebug { e.stackTraceToString() }
        Result.failure(e)
    }
}

internal fun DirectoryLogPublisher.safePublisher(): DirectoryLogPublisher {
    return if (this is SafeLogPublisher) this else SafeLogPublisher(this)
}

internal fun FLogStore.safeStore(): FLogStore {
    return if (this is SafeLogStore) this else SafeLogStore(this)
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

private class SafeLogStore(private val instance: FLogStore) : FLogStore {
    override fun append(log: String) {
        libRunCatching { instance.append(log) }.getOrElse {
            close()
            throw it
        }
    }

    override fun size(): Long {
        return libRunCatching { instance.size() }.getOrElse {
            close()
            throw it
        }
    }

    override fun close() {
        libRunCatching { instance.close() }
    }
}