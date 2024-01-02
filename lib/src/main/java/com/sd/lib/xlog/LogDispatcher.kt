package com.sd.lib.xlog

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

internal interface LogDispatcher {
    fun submit(runnable: Runnable)

    companion object {
        val Main: LogDispatcher = LogDispatcherMain()
        val IO: LogDispatcher = LogDispatcherIO()
    }
}

private class LogDispatcherMain : LogDispatcher {
    private val _handler = Handler(Looper.getMainLooper())

    override fun submit(runnable: Runnable) {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            runnable.run()
        } else {
            _handler.post(runnable)
        }
    }
}

private class LogDispatcherIO : LogDispatcher {
    private val _thread = HandlerThread("FLog")
    private val _handler = Handler(_thread.looper)

    init {
        _thread.start()
    }

    override fun submit(runnable: Runnable) {
        _handler.post(runnable)
    }
}