package top.vulpine.commons.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites legacy colour codes into their MiniMessage equivalents, so that
 * everything downstream can assume a single syntax.
 *
 * <p>Conversion runs unconditionally in {@link Dialect#LEGACY}, rather than only when
 * the string looks legacy. Deciding per string — "does it contain a {@code <tag>}?" —
 * would make the meaning of {@code &7} depend on whether some <em>unrelated part of
 * the same string</em> contains angle brackets, so appending a MiniMessage prefix to a
 * legacy message would change how the message parses.</p>
 *
 * <h2>Colour codes clear formatting</h2>
 * <p>The two syntaxes disagree about what a colour means. In legacy a colour code is
 * also a full reset, so {@code &l&aX} is green and <em>not</em> bold. MiniMessage
 * nests instead, and {@code <bold><green>X} stays bold until something closes it.</p>
 *
 * <p>Translating a colour to a bare tag therefore leaks formatting into everything
 * that follows: a prefix such as {@code &7[&f&lS&a&lL&7] &aHello} would render the
 * whole message bold, because nothing ever closes the {@code <bold>}. So every colour
 * is emitted as {@code <reset>} followed by the colour, which is what legacy means.</p>
 *
 * <p>The consequence for mixed strings is that a legacy colour also closes any
 * MiniMessage tag open at that point — a gradient, a hover event. That is the legacy
 * contract applied consistently, and the reason to reach for one syntax per string.</p>
 */
public final class LegacyConverter {

    /** {@code &#RRGGBB} — the Essentials-style hex form. */
    private static final Pattern AMPERSAND_HEX = Pattern.compile("[&§]#([A-Fa-f0-9]{6})");

    /** {@code §x§R§R§G§G§B§B} — the form legacy serializers emit for hex. */
    private static final Pattern SECTION_HEX =
            Pattern.compile("§x(?:§([A-Fa-f0-9])){6}");

    /** A single legacy colour or format code. */
    private static final Pattern CODE = Pattern.compile("[&§]([0-9A-FK-ORa-fk-or])");

    private LegacyConverter() {
    }

    /**
     * Converts every legacy code in the input to a MiniMessage tag, leaving all
     * other text — including existing MiniMessage tags — untouched.
     *
     * @param input the raw string; may be null
     * @return the converted string, or null if the input was null
     */
    public static String toMiniMessage(final String input) {

        if (input == null || input.isEmpty()) {
            return input;
        }

        String out = SECTION_HEX.matcher(input).replaceAll(match -> {
            StringBuilder hex = new StringBuilder(7).append('#');
            for (int group = 1; group <= 6; group++) {
                hex.append(match.group(group));
            }
            return "<reset><" + hex + ">";
        });

        out = AMPERSAND_HEX.matcher(out).replaceAll("<reset><#$1>");

        Matcher matcher = CODE.matcher(out);
        StringBuilder result = new StringBuilder(out.length());

        while (matcher.find()) {
            String tag = tagFor(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(result, Matcher.quoteReplacement(tag));
        }

        return matcher.appendTail(result).toString();
    }

    /**
     * Whether the input contains anything this class would rewrite. Used to decide
     * if a config is worth migrating; not used on the rendering path, which
     * converts unconditionally.
     *
     * @param input the raw string; may be null
     * @return true if at least one legacy code is present
     */
    public static boolean containsLegacy(final String input) {

        if (input == null || input.isEmpty()) {
            return false;
        }

        return SECTION_HEX.matcher(input).find()
                || AMPERSAND_HEX.matcher(input).find()
                || CODE.matcher(input).find();
    }

    /**
     * Colours carry a {@code <reset>} because that is what they mean in legacy;
     * format codes only add, so they stand alone.
     */
    private static String tagFor(final char code) {
        return switch (code) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }
}
