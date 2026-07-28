# Commons

Shared text, sound and logging utilities for my plugins. Requires **Paper 1.18.2+**.

## Install

```kotlin
repositories {
    maven("https://repo.vulpine.top/repository/maven-releases/")
}

dependencies {
    implementation("top.vulpine:commons:0.1.0")
}
```

Shade and **relocate** it. Do **not** relocate `net.kyori.adventure`. Paper exposes Adventure unrelocated, and
a relocated `Component` will not satisfy Paper's own method signatures.

## Text

Output is always a `Component`. The dialect controls what operators may type in
config, not what the plugin can render.

```java
Colorize.init(Dialect.MODERN);   // MiniMessage only; "&6" is literal
Colorize.init(Dialect.LEGACY);   // also accepts &6, §6, &#RRGGBB
```

`LEGACY` rewrites legacy codes to MiniMessage tags **unconditionally**, then parses
once.

```java
Component msg = Colorize.color("<gray>Welcome, <name>",
        Placeholder.unparsed("name", player.getName()));
player.sendMessage(msg);
```

Pass dynamic values as `TagResolver`s rather than substituting them into the
template first. A value containing `<red>` would otherwise change the formatting,
and one containing `<click:run_command:…>` would do considerably worse. Where a
resolver is impractical, `Colorize.escape(value)` makes the value literal.

`toLegacy(Component)` exists for APIs that will not take a Component. It is lossy —
hover events, click events and fonts do not survive. On Paper you should not need it.

### Known limitation

Legacy treats a colour code as a reset of formatting, so `&l&aX` is green and not
bold. MiniMessage nests, so the converted `<bold><green>X` is bold green. Strings
relying on legacy reset semantics render with extra formatting.

## Logging

```java
Logger.builder()
      .prefix("<dark_gray>[<white>Simple<green>Lobby<dark_gray>]</dark_gray> ")
      .level(config.logLevel)
      .trace(getDataFolder())    // optional: daily file under logs/
      .showCaller(true)          // default
      .build();

Logger.info("Plugin Started!");
```

The trace file keeps one writer open rather than reopening per line, so it needs
`Logger.close()` in `onDisable`.

`Logger.setLevel(...)` changes the threshold on its own — the level normally comes
from config, so `/reload` has to change it without reopening the trace file.

An optional `LogAction` tags what kind of work a line came from, independent of the
class name. Implement it with a private enum where a class does several distinct
things; skip it where it would just repeat the class name:

```java
private enum Action implements LogAction { SETUP, CLOSE, QUERY }

Logger.info(Action.SETUP, "Connected to MySQL");
Logger.debug("No action tag needed here");
```