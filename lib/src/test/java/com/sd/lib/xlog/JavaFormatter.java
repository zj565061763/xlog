package com.sd.lib.xlog;

/** 不实现reset的Java格式化器，和2.1.0及之前版本编译的实现类一样，字节码里没有reset */
class JavaFormatter implements FLogFormatter {
    @Override
    public String format(FLogRecord record) {
        return record.getMsg();
    }
}
