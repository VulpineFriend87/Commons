package top.vulpine.commons.log;

/**
 * Verbosity threshold, ordered from most to least verbose. A message is emitted
 * when its level is at or above the configured one.
 */
public enum LogLevel {

    /** Diagnostic detail, off by default. */
    DEBUG,

    /** Normal operational messages. */
    INFO,

    /** Something unexpected that did not stop the operation. */
    WARN,

    /** Something that failed. */
    ERROR
}
