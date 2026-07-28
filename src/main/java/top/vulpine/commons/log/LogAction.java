package top.vulpine.commons.log;

/**
 * A grep-able tag describing <em>what kind of work</em> a log line came from, as
 * opposed to which class emitted it.
 *
 * <p>Implemented by a private enum inside each class that logs. Enums get
 * {@link #name()} for free, so there is nothing to write beyond the constants:</p>
 *
 * <pre>{@code
 * private enum Action implements LogAction {
 *     SETUP, CLOSE, QUERY
 * }
 * }</pre>
 *
 * <p>The interface exists so the logger cannot accept just any enum. Typing the
 * parameter as {@code Enum<?>} compiles for unrelated values — including
 * {@link LogLevel} itself — which gives up the type safety that was the reason to
 * use an enum instead of a String in the first place.</p>
 *
 * <p>Worth skipping when a class only ever logs one kind of thing: the caller class
 * name is already on every line, so a single-constant action repeats it. Use the
 * overloads without an action there.</p>
 */
public interface LogAction {

    /**
     * @return the tag, supplied automatically when implemented by an enum
     */
    String name();
}
