package top.vulpine.commons.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorizeTest {

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @AfterEach
    void reset() {
        Colorize.init(Dialect.MODERN);
    }

    @Test
    @DisplayName("MODERN leaves legacy codes as literal text")
    void modernKeepsLegacyLiteral() {
        Colorize.init(Dialect.MODERN);
        assertEquals("&aHello", plain(Colorize.color("&aHello")));
    }

    @Test
    @DisplayName("LEGACY strips legacy codes into formatting")
    void legacyConvertsCodes() {
        Colorize.init(Dialect.LEGACY);
        assertEquals("Hello", plain(Colorize.color("&aHello")));
    }

    @Test
    @DisplayName("LEGACY accepts MiniMessage and legacy codes in one string")
    void legacyAcceptsBoth() {
        Colorize.init(Dialect.LEGACY);
        assertEquals("boldgreen", plain(Colorize.color("<bold>bold</bold>&agreen")));
    }

    @Test
    @DisplayName("a legacy code means the same thing regardless of the rest of the string")
    void legacyIsNotContextDependent() {
        Colorize.init(Dialect.LEGACY);

        // A legacy code must mean the same thing whether or not an unrelated part of
        // the string contains a MiniMessage tag.
        Component withTag = Colorize.color("<red>x</red>&7y");
        Component withoutTag = Colorize.color("&7y");

        assertEquals("y", plain(withoutTag));
        assertEquals("xy", plain(withTag));
    }

    @Test
    @DisplayName("unknown tags survive as literal text rather than throwing")
    void unknownTagsAreLiteral() {
        Colorize.init(Dialect.MODERN);
        assertEquals("Press <shift> to sneak", plain(Colorize.color("Press <shift> to sneak")));
    }

    @Test
    @DisplayName("resolver values are not parsed as markup")
    void resolversAreNotParsed() {
        Colorize.init(Dialect.MODERN);

        Component result = Colorize.color("<gray>hi <name>",
                Placeholder.unparsed("name", "<red>injected</red>"));

        assertEquals("hi <red>injected</red>", plain(result));
    }

    @Test
    @DisplayName("escape renders MiniMessage syntax literally")
    void escapeIsLiteral() {
        Colorize.init(Dialect.MODERN);
        assertEquals("<red>x</red>", plain(Colorize.color(Colorize.escape("<red>x</red>"))));
    }

    @Test
    @DisplayName("hex forms convert in LEGACY")
    void hexConverts() {
        Colorize.init(Dialect.LEGACY);
        assertEquals("hi", plain(Colorize.color("&#ff00ffhi")));
    }

    @Test
    @DisplayName("null and empty input are safe")
    void nullSafe() {
        assertEquals("", plain(Colorize.color((String) null)));
        assertEquals("", plain(Colorize.color("")));
        assertTrue(Colorize.color((java.util.List<String>) null).isEmpty());
    }

    @Test
    @DisplayName("containsLegacy detects what the converter would rewrite")
    void detectsLegacy() {
        assertTrue(LegacyConverter.containsLegacy("&aHello"));
        assertTrue(LegacyConverter.containsLegacy("&#ff00ffHello"));
        assertFalse(LegacyConverter.containsLegacy("<green>Hello"));
        assertFalse(LegacyConverter.containsLegacy("plain"));
    }

    @Test
    @DisplayName("MiniMessage round-trips through serialize")
    void roundTrips() {
        Colorize.init(Dialect.MODERN);
        Component original = Colorize.color("<green>hello <bold>world");
        assertEquals(plain(original), plain(Colorize.color(Colorize.serialize(original))));
    }

    /**
     * A purely legacy string has one correct rendering: whatever the legacy
     * serializer produces. Comparing against it is stricter than asserting on a
     * hand-written expectation, and it is the definition of "accepts legacy
     * natively".
     */
    private static void rendersLikeLegacy(final String input) {
        Colorize.init(Dialect.LEGACY);
        assertEquals(
                legacy(LegacyComponentSerializer.legacyAmpersand().deserialize(input)),
                legacy(Colorize.color(input)),
                "legacy input should render exactly as legacy: " + input);
    }

    private static String legacy(final Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    @Test
    @DisplayName("a colour code clears formatting, the way legacy does")
    void colourResetsFormatting() {
        rendersLikeLegacy("&l&aX");
    }

    @Test
    @DisplayName("formatting from a prefix does not bleed into the message")
    void formattingDoesNotBleed() {
        rendersLikeLegacy("&7[&f&lS&a&lL&7] &aYou have been teleported.");
    }

    @Test
    @DisplayName("&r clears colour as well as formatting")
    void resetCode() {
        rendersLikeLegacy("&a&lbold green&rplain");
    }

    @Test
    @DisplayName("hex codes reset formatting too")
    void hexResetsFormatting() {
        rendersLikeLegacy("&l&#ff00ffX");
    }

    @Test
    @DisplayName("a format code keeps the colour it follows")
    void formatKeepsColour() {
        rendersLikeLegacy("&a&lstill green");
    }
}
