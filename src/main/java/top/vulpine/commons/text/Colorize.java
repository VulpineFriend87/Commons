package top.vulpine.commons.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns configured strings into {@link Component}s.
 *
 * <h2>Output is always a Component</h2>
 * <p>Never a String. Serializing to a legacy string loses everything legacy cannot
 * encode — hover text, click events, fonts, translatable components — so it is a
 * last-mile concern for APIs that will not accept a Component ({@link #toLegacy}),
 * not something to do in the middle.</p>
 *
 * <h2>Configuration is static and per-plugin</h2>
 * <p>{@link #init} sets the dialect process-wide, which is safe because each
 * consuming plugin shades and relocates this library into its own package — the
 * static state is therefore per-plugin, not per-server.</p>
 */
public final class Colorize {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static volatile Dialect dialect = Dialect.MODERN;

    private Colorize() {
    }

    /**
     * Sets the syntax accepted by {@link #color}. Call once during plugin startup;
     * defaults to {@link Dialect#MODERN} if never called.
     *
     * @param value the dialect to accept
     */
    public static void init(final Dialect value) {
        dialect = Objects.requireNonNull(value, "dialect");
    }

    /**
     * @return the currently configured dialect
     */
    public static Dialect dialect() {
        return dialect;
    }

    /**
     * Renders a string to a Component using the configured dialect.
     *
     * @param message the message; may be null
     * @return the rendered component, or {@link Component#empty()} if null
     */
    public static Component color(final String message) {
        return color(message, TagResolver.empty());
    }

    /**
     * Renders a string to a Component, additionally resolving the given tags.
     *
     * <p>This is the injection-safe way to insert dynamic values. Substituting a
     * value into the template <em>before</em> parsing means a value containing
     * MiniMessage syntax is parsed as markup — a player name carrying
     * {@code <red>} changes the formatting, and one carrying a
     * {@code <click:run_command:…>} tag does considerably worse. Passing values as
     * resolvers keeps them as data.</p>
     *
     * <pre>{@code
     * Colorize.color("<gray>Welcome, <name>", Placeholder.unparsed("name", player.getName()));
     * }</pre>
     *
     * @param message the message; may be null
     * @param tags resolvers for any dynamic values
     * @return the rendered component, or {@link Component#empty()} if null
     */
    public static Component color(final String message, final TagResolver... tags) {

        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        String prepared = dialect == Dialect.LEGACY
                ? LegacyConverter.toMiniMessage(message)
                : message;

        return MINI.deserialize(prepared, tags);
    }

    /**
     * Renders every string in a list, preserving order.
     *
     * @param messages the messages; may be null
     * @return the rendered components, never null
     */
    public static List<Component> color(final List<String> messages) {

        if (messages == null) {
            return List.of();
        }

        List<Component> result = new ArrayList<>(messages.size());
        for (String message : messages) {
            result.add(color(message));
        }
        return result;
    }

    /**
     * Renders every string in an array, preserving order.
     *
     * @param messages the messages; may be null
     * @return the rendered components, never null
     */
    public static Component[] color(final String[] messages) {

        if (messages == null) {
            return new Component[0];
        }

        Component[] result = new Component[messages.length];
        for (int i = 0; i < messages.length; i++) {
            result[i] = color(messages[i]);
        }
        return result;
    }

    /**
     * Escapes MiniMessage syntax so the result renders as literal text.
     *
     * <p>Use on untrusted values that must be substituted into a template as a
     * string rather than passed as a {@link TagResolver}. Prefer
     * {@link #color(String, TagResolver...)} where possible.</p>
     *
     * @param input the raw value; may be null
     * @return the escaped value, or null if the input was null
     */
    public static String escape(final String input) {
        return input == null ? null : MINI.escapeTags(input);
    }

    /**
     * Removes all MiniMessage tags, leaving plain text.
     *
     * <p>Operates on the raw string, so legacy codes survive. Use {@link #plain}
     * to strip formatting in whichever dialect is configured.</p>
     *
     * @param input the raw string; may be null
     * @return the stripped string, or null if the input was null
     */
    public static String strip(final String input) {
        return input == null ? null : MINI.stripTags(input);
    }

    /**
     * Renders a string in the configured dialect and returns its plain text, with
     * all formatting removed. For log files and anything else that cannot show
     * colour.
     *
     * @param input the raw string; may be null
     * @return the plain text, or an empty string if null
     */
    public static String plain(final String input) {
        return input == null ? "" : PLAIN.serialize(color(input));
    }

    /**
     * Serializes a Component back to MiniMessage. Lossless.
     *
     * @param component the component; may be null
     * @return the MiniMessage string, or an empty string if null
     */
    public static String serialize(final Component component) {
        return component == null ? "" : MINI.serialize(component);
    }

    /**
     * Serializes a Component to a section-sign legacy string.
     *
     * <p><strong>Lossy.</strong> Anything legacy cannot encode is discarded. Only
     * for APIs that will not accept a Component; on Paper that should be nothing.</p>
     *
     * @param component the component; may be null
     * @return the legacy string, or an empty string if null
     */
    public static String toLegacy(final Component component) {
        return component == null ? "" : SECTION.serialize(component);
    }
}
