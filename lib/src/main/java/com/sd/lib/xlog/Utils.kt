package com.sd.lib.xlog

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

/** 当前进程名，系统接口出错或取不到时读取/proc/self/cmdline */
internal fun Context.currentProcess(): String? {
  return processOf(
    system = { processOfSystem() },
    cmdline = { File("/proc/self/cmdline").readText() },
  )
}

/** 先用[system]获取进程名，出错或为空时从[cmdline]的内容中解析，都取不到时返回null */
internal fun processOf(system: () -> String?, cmdline: () -> String): String? {
  return libRunCatching { system() }.getOrNull()?.ifEmpty { null }
    ?: libRunCatching { processOfCmdline(cmdline()) }.getOrNull()
}

/** 通过系统接口获取进程名，Android 7.0到8.1跨进程调用失败时会抛异常 */
private fun Context.processOfSystem(): String? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Application.getProcessName()
  } else {
    val pid = Process.myPid()
    (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
      ?.runningAppProcesses
      ?.firstOrNull { it.pid == pid }
      ?.processName
  }
}

/** 从cmdline的内容中解析出进程名，各参数以'\u0000'分隔，第一个就是进程名 */
internal fun processOfCmdline(cmdline: String): String? {
  return cmdline.substringBefore('\u0000').trim().ifEmpty { null }
}