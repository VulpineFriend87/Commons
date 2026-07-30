package top.vulpine.commons.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
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
 * <p>Each line carries the plugin prefix, the calling class, an optional
 * {@link LogAction} tag, and the message. Lines are assembled as
 * {@link Component}s rather than concatenated strings, so a message containing a
 * stray {@code <} cannot break the prefix and the prefix's syntax cannot change how
 * the message parses.</p>
 *
 * <p>Static state is per-plugin, since each consumer relocates this library into
 * its own package.</p>
 */
public final class Logger {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Object TRACE_LOCK = new Object();

    private static volatile LogLevel threshold = LogLevel.INFO;
    private static volatile Component prefix = Component.empty();
    private static volatile boolean showCaller = true;

    private static BufferedWriter trace;

    private Logger() {
    }

    /**
     * Starts configuring the logger. Call {@link Builder#build()} to finish.
     *
     * <pre>{@code
     * Logger.builder()
     *       .prefix("<gray>[SimpleLobby]</gray> ")
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

        private String prefixTemplate;
        private LogLevel level = LogLevel.INFO;
        private File traceFolder;
        private boolean caller = true;

        private Builder() {
        }

        /**
         * Sets the prefix that opens every line. Required.
         *
         * <p>Pass {@code ""} for no prefix — that is a deliberate choice rather
         * than a default, which is why the value has to be supplied.</p>
         *
         * @param template the prefix, in MiniMessage; e.g.
         *        {@code "<gray>[SimpleLobby]</gray> "}
         * @return this builder
         */
        public Builder prefix(final String template) {
            this.prefixTemplate = template;
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
         * @throws IllegalStateException if no prefix was set
         */
        public void build() {

            if (prefixTemplate == null) {
                throw new IllegalStateException(
                        "Logger.builder() requires prefix(...); pass \"\" for no prefix");
            }

            prefix = Colorize.color(prefixTemplate);
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
     * Changes the level threshold on its own, leaving the prefix and trace file
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
     * Writes a line with the prefix but no level tag, caller, or color, ignoring
     * the threshold. For startup banners.
     *
     * @param message the message
     */
    public static void system(final String message) {
        Bukkit.getConsoleSender().sendMessage(prefix.append(Colorize.color(message)));
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

        Component line = prefix;

        if (caller != null) {
            line = line.append(bracket(caller, NamedTextColor.AQUA));
        }

        if (action != null) {
            line = line.append(bracket(action.name(), NamedTextColor.YELLOW));
        }

        Bukkit.getConsoleSender().sendMessage(
                line.append(Colorize.color(message).colorIfAbsent(colorFor(level))));

        writeTrace(level, caller, action, message);
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
