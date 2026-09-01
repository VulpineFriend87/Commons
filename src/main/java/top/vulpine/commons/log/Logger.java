package top.vulpine.commons.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import top.vulpine.commons.text.Colorize;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Console logger with an optional on-disk trace file.
 *
 * <p>Each line carries the calling class, an optional {@link LogAction} tag, and the
 * message. Lines are assembled as {@link Component}s rather than concatenated
 * strings, so a message containing a stray {@code <} cannot break the line.</p>
 *
 * <p>Static state is per-plugin, since each consumer relocates this library into
 * its own package.</p>
 *
 * <h2>Platforms</h2>
 * <p>Everything goes through a {@link ComponentLogger}, which both Paper and
 * Velocity provide and which takes {@link Component}s directly — nothing is
 * serialized to a string on the way out, so hover text, click events and colour all
 * survive.</p>
 *
 * <pre>{@code
 * // Paper — Plugin#getComponentLogger() has existed since 1.18.2
 * Logger.builder().logger(getComponentLogger()).level(config.logLevel).build();
 *
 * // Velocity — the proxy injects one
 * Logger.builder().logger(logger).level(config.logLevel).build();
 * }</pre>
 *
 * <p>The plugin name comes from the logger itself, which is why there is no prefix
 * setting: naming the source of a line is the logging framework's job.</p>
 */
public final class Logger {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Object TRACE_LOCK = new Object();

    private static volatile LogLevel threshold = LogLevel.INFO;
    private static volatile boolean showCaller = true;
    private static volatile ComponentLogger console;

    private static BufferedWriter trace;

    private Logger() {
    }

    /**
     * Starts configuring the logger. Call {@link Builder#build()} to finish.
     *
     * <pre>{@code
     * Logger.builder()
     *       .logger(getComponentLogger())
     *       .level(config.logLevel)
     *       .trace(getDataFolder())
     *       .build();
     * }</pre>
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects logger settings so the optional ones do not turn into a row of
     * {@code init} overloads.
     */
    public static final class Builder {

        private ComponentLogger logger;
        private LogLevel level = LogLevel.INFO;
        private File traceFolder;
        private boolean caller = true;

        private Builder() {
        }

        /**
         * Where lines are written. Required.
         *
         * <p>On Paper this is {@code getComponentLogger()}; on Velocity it is the
         * logger the proxy injects into the plugin.</p>
         *
         * @param value the platform's component logger
         * @return this builder
         */
        public Builder logger(final ComponentLogger value) {
            this.logger = value;
            return this;
        }

        /**
         * @param value the minimum level to emit; defaults to {@link LogLevel#INFO}
         * @return this builder
         */
        public Builder level(final LogLevel value) {
            this.level = value == null ? LogLevel.INFO : value;
            return this;
        }

        /**
         * Enables a daily trace file at
         * {@code <dataFolder>/logs/trace-yyyy-MM-dd.log}, recording every line that
         * passes the level check with color stripped. Off unless called.
         *
         * <p>Requires {@link Logger#close()} from {@code onDisable}.</p>
         *
         * @param dataFolder the plugin data folder
         * @return this builder
         */
        public Builder trace(final File dataFolder) {
            this.traceFolder = dataFolder;
            return this;
        }

        /**
         * Whether to include the calling class name on each line. On by default.
         *
         * <p>Resolving the caller walks the current stack, which only happens for
         * lines that pass the level check.</p>
         *
         * @param value false to omit the caller
         * @return this builder
         */
        public Builder showCaller(final boolean value) {
            this.caller = value;
            return this;
        }

        /**
         * Applies the settings. Replaces any previous configuration, closing an
         * already-open trace file first.
         *
         * @throws IllegalStateException if no logger was set
         */
        public void build() {

            if (logger == null) {
                throw new IllegalStateException(
                        "Logger.builder() requires logger(...); on Paper pass getComponentLogger(), "
                                + "on Velocity the injected ComponentLogger");
            }

            console = logger;
            threshold = level;
            showCaller = caller;

            synchronized (TRACE_LOCK) {

                closeTrace();

                if (traceFolder == null) {
                    return;
                }

                try {
                    File logs = new File(traceFolder, "logs");
                    logs.mkdirs();

                    File file = new File(logs, "trace-" + LocalDate.now().format(DATE) + ".log");

                    trace = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                } catch (IOException e) {
                    trace = null;
                    warn("Could not open trace file: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Flushes and closes the trace file if one is open. Safe to call when there
     * is none.
     */
    public static void close() {
        synchronized (TRACE_LOCK) {
            closeTrace();
        }
    }

    /**
     * Changes the level threshold on its own, leaving the logger and trace file
     * alone.
     *
     * <p>The level usually comes from config, so a {@code /reload} command needs to
     * change it at runtime. Re-running the builder would reopen the trace file for no
     * reason.</p>
     *
     * @param level the minimum level to emit
     */
    public static void setLevel(final LogLevel level) {
        threshold = level == null ? LogLevel.INFO : level;
    }

    /** @param message the message */
    public static void debug(final String message) {
        log(LogLevel.DEBUG, null, message);
    }

    /** @param action the action tag @param message the message */
    public static void debug(final LogAction action, final String message) {
        log(LogLevel.DEBUG, action, message);
    }

    /** @param message the message */
    public static void info(final String message) {
        log(LogLevel.INFO, null, message);
    }

    /** @param action the action tag @param message the message */
    public static void info(final LogAction action, final String message) {
        log(LogLevel.INFO, action, message);
    }

    /** @param message the message */
    public static void warn(final String message) {
        log(LogLevel.WARN, null, message);
    }

    /** @param action the action tag @param message the message */
    public static void warn(final LogAction action, final String message) {
        log(LogLevel.WARN, action, message);
    }

    /** @param message the message */
    public static void error(final String message) {
        log(LogLevel.ERROR, null, message);
    }

    /** @param action the action tag @param message the message */
    public static void error(final LogAction action, final String message) {
        log(LogLevel.ERROR, action, message);
    }

    /**
     * Writes a line with no level tag or caller, ignoring the threshold.
     *
     * <p>For startup banners and anything else that must appear even when an
     * operator has raised the level to {@code ERROR}.</p>
     *
     * @param message the message
     */
    public static void system(final String message) {
        console().info(Colorize.color(message));
    }

    /**
     * Emits a line at an explicit level.
     *
     * @param level the level
     * @param action the action tag; may be null
     * @param message the message
     */
    public static void log(final LogLevel level, final LogAction action, final String message) {

        if (level.ordinal() < threshold.ordinal()) {
            return;
        }

        String caller = showCaller ? callerClass() : null;

        Component line = Component.empty();

        if (caller != null) {
            line = line.append(bracket(caller, NamedTextColor.AQUA));
        }

        if (action != null) {
            line = line.append(bracket(action.name(), NamedTextColor.YELLOW));
        }

        emit(level, line.append(Colorize.color(message).colorIfAbsent(colorFor(level))));

        writeTrace(level, caller, action, message);
    }

    /**
     * Hands one assembled line to the platform at the matching level, so a warning
     * is filterable as a warning in the server's own log rather than arriving as
     * undifferentiated output.
     */
    private static void emit(final LogLevel level, final Component line) {

        ComponentLogger target = console();

        switch (level) {
            case DEBUG -> target.debug(line);
            case INFO -> target.info(line);
            case WARN -> target.warn(line);
            case ERROR -> target.error(line);
        }
    }

    private static ComponentLogger console() {

        ComponentLogger target = console;

        if (target == null) {
            throw new IllegalStateException(
                    "Logger was used before Logger.builder()...build() was called");
        }

        return target;
    }

    private static Component bracket(final String text, final TextColor color) {
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(text, color))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));
    }

    private static TextColor colorFor(final LogLevel level) {
        return switch (level) {
            case DEBUG -> NamedTextColor.BLUE;
            case INFO -> NamedTextColor.GRAY;
            case WARN -> NamedTextColor.YELLOW;
            case ERROR -> NamedTextColor.RED;
        };
    }

    private static void writeTrace(final LogLevel level, final String caller,
                                   final LogAction action, final String message) {

        synchronized (TRACE_LOCK) {

            if (trace == null) {
                return;
            }

            StringBuilder line = new StringBuilder()
                    .append('[').append(LocalTime.now().format(TIME)).append("] ")
                    .append('[').append(level.name()).append("] ");

            if (caller != null) {
                line.append('[').append(caller).append("] ");
            }

            if (action != null) {
                line.append('[').append(action.name()).append("] ");
            }

            line.append(Colorize.plain(message));

            try {
                trace.write(line.toString());
                trace.newLine();
                trace.flush();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed writing trace line", e);
            }
        }
    }

    private static void closeTrace() {

        if (trace == null) {
            return;
        }

        try {
            trace.flush();
            trace.close();
        } catch (IOException ignored) {
            // Shutting down; nothing useful to do with a failure here.
        }

        trace = null;
    }

    private static String callerClass() {

        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stack) {

            String name = element.getClassName();

            if (name.equals(Logger.class.getName()) || name.startsWith("java.lang.Thread")) {
                continue;
            }

            return name.substring(name.lastIndexOf('.') + 1);
        }

        return "Unknown";
    }
}
