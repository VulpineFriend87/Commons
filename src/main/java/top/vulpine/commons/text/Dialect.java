package top.vulpine.commons.text;

/**
 * Which text syntaxes a plugin accepts in its configuration files.
 *
 * <p>This is a decision about <em>input</em> only. Output is always a
 * {@link net.kyori.adventure.text.Component}, in both dialects — the dialect
 * never changes what a plugin can render, only what an operator is allowed to
 * type.</p>
 */
public enum Dialect {

    /**
     * MiniMessage only. {@code &6} and {@code §6} are literal text.
     *
     * <p>The default, and correct for anything that has never shipped with
     * legacy colour codes in its config.</p>
     */
    MODERN,

    /**
     * MiniMessage, plus legacy {@code &6} / {@code §6} / {@code &#RRGGBB} codes,
     * which are rewritten to their MiniMessage equivalents before parsing.
     *
     * <p>For plugins whose users already have legacy codes in configs written
     * against an older version. Both syntaxes may be mixed in the same string.</p>
     *
     * <p>Conversion is unconditional — see {@link LegacyConverter} for why that
     * matters.</p>
     */
    LEGACY
}
