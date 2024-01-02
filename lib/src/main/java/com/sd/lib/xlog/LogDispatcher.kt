package com.sd.lib.xlog

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

internal interface LogDispatcher {
    fun dispatch(runnable: Runnable)

    companion object {
        val Main: LogDispatcher = LogDispatcherMain()
        val IO: LogDispatcher = LogDispatcherIO()
    }
}

private class LogDispatcherMain : LogDispatcher {
    private val _handler = Handler(Looper.getMainLooper())

    override fun dispatch(runnable: Runnable) {
        _handler.post(runnable)
    }
}

private class LogDispatcherIO : LogDispatcher {
    private val _thread = HandlerThread("FLog")
    private val _handler = Handler(_thread.looper)

    init {
        _thread.start()
    }

    override fun dispatch(runnable: Runnable) {
        _handler.post(runnable)
    }
}