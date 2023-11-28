package com.sd.lib.xlog

/**
 * 日志标识，一个日志标识代表一类相关的逻辑，默认的tag是子类的短类名
 */
interface FLogger

/**
 * [FLogger]配置信息
 */
class FLoggerConfig(
    /** 日志等级 */
    var level: FLogLevel? = null,

    /** 日志标识 */
    var tag: String? = null,
)

/**
 * 配置信息是否为空
 */
internal fun FLoggerConfig.isEmpty(): Boolean {
    return this.level == null &&
            this.tag.isNullOrEmpty()
}