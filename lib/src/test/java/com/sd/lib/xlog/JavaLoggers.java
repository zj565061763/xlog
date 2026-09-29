package com.sd.lib.xlog;

/** Java定义的日志标识，类名由Java编译器生成 */
class JavaLoggers {
  /** 类名是JavaLoggers$1LocalLogger */
  static Class<? extends FLogger> localLogger() {
    class LocalLogger implements FLogger {}
    return LocalLogger.class;
  }

  /** 类名是JavaLoggers$1 */
  static Class<? extends FLogger> anonymousLogger() {
    return new FLogger() {}.getClass();
  }
}
