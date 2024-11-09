package com.sd.lib.xlog

internal inline fun <R> libTryRun(block: () -> R): Result<R> {
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
        libTryRun { instance.publish(record) }
    }

    override fun close() {
        libTryRun { instance.close() }
    }
}

private class SafeLogStore(private val instance: FLogStore) : FLogStore {
    override fun append(log: String) {
        libTryRun { instance.append(log) }.getOrElse {
            close()
            throw it
        }
    }

    override fun size(): Long {
        return libTryRun { instance.size() }.getOrElse {
            close()
            throw it
        }
    }

    override fun close() {
        libTryRun { instance.close() }
    }
}