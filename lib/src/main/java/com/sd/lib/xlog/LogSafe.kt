package com.sd.lib.xlog

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

internal inline fun <R> libTryRun(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        fDebug { Log.getStackTraceString(e) }
        Result.failure(e)
    }
}

internal fun LogPublisher.safePublisher(): LogPublisher {
    return if (this is SafeLogPublisher) this else SafeLogPublisher(this)
}

internal fun FLogStore.safeStore(): FLogStore {
    return if (this is SafeLogStore) this else SafeLogStore(this)
}

private class SafeLogPublisher(private val instance: LogPublisher) : LogPublisher {
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
        return libTryRun { instance.size() }.getOrElse { 0 }
    }

    override fun close() {
        libTryRun { instance.close() }
    }
}