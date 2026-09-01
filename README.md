# Commons

Text rendering and console logging for Paper and Velocity plugins.

- **`Colorize`** — turns configured strings into Adventure `Component`s, with optional
  support for legacy `&` colour codes alongside MiniMessage.
- **`Logger`** — a console logger with levels, an optional daily trace file,
  and grep-friendly tags.

**Requires Paper 1.18.2 or newer, or Velocity 3.** Adventure and MiniMessage come from the
platform; both ship everything this library needs.

---

## Contents

- [Install](#install)
- [Colorize](#colorize)
  - [Dialects](#dialects)
  - [Inserting values safely](#inserting-values-safely)
  - [Converting back](#converting-back)
- [Logger](#logger)
  - [Setup](#setup)
  - [Log actions](#log-actions)
  - [Trace file](#trace-file)
  - [On Velocity](#on-velocity)
- [Full example](#full-example)

---

## Install

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.vulpine.top/repository/maven-open/")
}

dependencies {
    implementation("top.vulpine:commons:0.1.0")
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
}

tasks.shadowJar {
    relocate("top.vulpine.commons", "com.example.myplugin.libs.commons")
}
```

Relocate `top.vulpine.commons` so two plugins on the same server do not share
configuration. Do **not** relocate `net.kyori.adventure` — the platform provides it
unrelocated, and a relocated `Component` will not satisfy the platform's own method
signatures.

On Velocity, swap the `paper-api` line for `velocity-api`; nothing else changes.

---

## Colorize

`Colorize.color` returns a `Component`, which is what Paper's APIs accept:

```java
Component message = Colorize.color("<green>Welcome to the server");

player.sendMessage(message);
player.sendActionBar(Colorize.color("<yellow>Loading…"));
Bukkit.getConsoleSender().sendMessage(Colorize.color("<gray>Plugin ready"));
```

Lists and arrays are handled too:

```java
List<Component> lines = Colorize.color(config.motd);      // List<String> in
Component[] parts = Colorize.color(new String[]{"a", "b"});
```

### Dialects

Call `init` once during startup to choose which syntax your config accepts:

```java
Colorize.init(Dialect.MODERN);   // MiniMessage only
Colorize.init(Dialect.LEGACY);   // MiniMessage plus legacy & codes
```

| Dialect | `<green>x` | `&ax` | `&#ff00ffx` |
|---|---|---|---|
| `MODERN` | green | literal text `&ax` | literal text |
| `LEGACY` | green | green | pink |

`MODERN` is the default and the right choice for a new plugin. Use `LEGACY` when
server owners already have `&` codes in their config files from an earlier version.

In `LEGACY`, both syntaxes can appear in the same string, and legacy codes are
converted the same way regardless of what else the string contains:

```java
Colorize.init(Dialect.LEGACY);

Colorize.color("&aGreen and <bold>bold");     // both apply
Colorize.color("&7Just legacy");              // grey
```

Unknown MiniMessage tags render as literal text rather than throwing, so a message like
`"Press <shift> to sneak"` is safe.

> **Note on legacy formatting:** legacy treats a colour code as a reset of formatting,
> so `&l&aX` is green and not bold. MiniMessage nests instead, so after conversion
> `<bold><green>X` is bold *and* green. Strings that rely on the legacy reset will show
> extra formatting.

### Inserting values safely

Pass dynamic values as `TagResolver`s rather than concatenating them into the template:

```java
Component greeting = Colorize.color("<gray>Welcome, <name>",
        Placeholder.unparsed("name", player.getName()));
```

Substituting a value into the string first means the value is then parsed as markup — a
player whose name contains `<red>` would change the message's colours, and one
containing a `<click:run_command:…>` tag would make the line clickable. Passing values
as resolvers keeps them as data.

Where a resolver is impractical, escape the value:

```java
String safe = Colorize.escape(untrustedValue);
Component line = Colorize.color("<gray>Reported: " + safe);
```

### Converting back

```java
Colorize.serialize(component);   // Component -> MiniMessage string, lossless
Colorize.plain("&aHello");       // -> "Hello", formatting removed, dialect-aware
Colorize.strip("<green>Hello");  // -> "Hello", removes MiniMessage tags only
Colorize.toLegacy(component);    // Component -> §-coded string, lossy
```

`plain` is what you want for log files and anything else that cannot show colour, since
it renders through the configured dialect first. `strip` works on the raw string and so
leaves legacy codes in place.

`toLegacy` is only for APIs that will not take a `Component`; hover events, click
events and fonts do not survive it.

---

## Logger

### Setup

```java
Logger.builder()
        .logger(getComponentLogger())
        .level(LogLevel.INFO)
        .build();
```

| Method | Default | |
|---|---|---|
| `logger(ComponentLogger)` | — | required; where lines are written |
| `level(LogLevel)` | `INFO` | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `trace(File)` | off | plugin data folder; see [Trace file](#trace-file) |
| `showCaller(boolean)` | `true` | include the calling class name |

`ComponentLogger` takes Adventure components directly, so nothing is flattened to a
string on the way out. `Plugin#getComponentLogger()` has existed since Paper 1.18.2.

There is no prefix setting. Naming the source of a line is the logging framework's job,
and both platforms already put the plugin name on every line. Levels are passed through
too, so a `warn` is filterable as a warning in the server's own log.

Then:

```java
Logger.debug("Loaded 3 arenas");
Logger.info("Connected to MySQL");
Logger.warn("Config value out of range, using default");
Logger.error("Could not save data: " + e.getMessage());

Logger.system("");                       // no level tag, no caller, ignores the level
Logger.system("<green>  MyPlugin v1.0");
```

`system` is for startup banners — it always prints, whatever the level is set to, and
adds no caller or action decoration.

Output looks like:

```
[12:00:00 INFO]: [MyPlugin] [ArenaManager] Loaded 3 arenas
[12:00:01 DEBUG]: [MyPlugin] [SlotManager] Slot 4 released
```

Message bodies go through `Colorize`, so if your plugin uses `Dialect.LEGACY` you can
log with `&` codes.

To change the level at runtime — a `/reload` command, for example — without reopening
the trace file:

```java
Logger.setLevel(config.logLevel);
```

### On Velocity

Nothing changes. Velocity injects a `ComponentLogger` into the plugin, so pass that one
instead:

```java
@Inject
public MyPlugin(final ComponentLogger logger) {
    Logger.builder().logger(logger).build();
}
```

`Colorize` needs nothing either: Velocity ships Adventure, MiniMessage and both
serializers, so it behaves identically on both platforms.

### Log actions

An optional tag says *what kind of work* a line came from, independent of the class
name. Implement `LogAction` with a private enum:

```java
public final class StorageManager {

    private enum Action implements LogAction {
        SETUP, CLOSE, QUERY
    }

    public void connect() {
        Logger.info(Action.SETUP, "Connected to MySQL");
    }

    public void query(final String sql) {
        Logger.debug(Action.QUERY, "Running: " + sql);
    }
}
```

```
[MyPlugin] [StorageManager] [SETUP] Connected to MySQL
```

Enums provide `name()` already, so there is nothing to implement beyond the constants.
Tags make logs greppable by operation rather than by class, which survives renaming a
class.

Worth skipping where a class only does one kind of work — the caller name is already on
every line, so a single-constant tag just repeats it. Use the overloads without a tag
there.

### Trace file

Pass the plugin data folder to also write every emitted line to
`<dataFolder>/logs/trace-yyyy-MM-dd.log`, with colour removed:

```java
Logger.builder()
        .logger(getComponentLogger())
        .level(LogLevel.DEBUG)
        .trace(getDataFolder())
        .build();
```

```
[14:32:07] [INFO] [StorageManager] [SETUP] Connected to MySQL
[14:32:09] [DEBUG] [SlotManager] Slot 4 released
```

The writer stays open, so close it on shutdown:

```java
@Override
public void onDisable() {
    Logger.close();
}
```

`close()` is safe to call when no trace file is open.

---

## Full example

```java
public final class MyPlugin extends JavaPlugin {

    private Config configuration;

    @Override
    public void onEnable() {

        // Legacy because earlier versions of this plugin shipped & codes
        Colorize.init(Dialect.LEGACY);

        Logger.builder()
                .logger(getComponentLogger())
                .level(LogLevel.INFO)
                .trace(getDataFolder())
                .build();

        Logger.system("<green>  MyPlugin v" + getDescription().getVersion());

        this.configuration = loadConfig();
        Logger.setLevel(configuration.logLevel);

        Logger.info("Enabled");
    }

    @Override
    public void onDisable() {
        Logger.close();
    }
}
```

```java
public final class JoinListener implements Listener {

    private enum Action implements LogAction {
        JOIN, QUIT
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {

        Player player = event.getPlayer();

        event.joinMessage(Colorize.color("<gray>+ <white><name>",
                Placeholder.unparsed("name", player.getName())));

        player.sendMessage(Colorize.color(configuration.welcome,
                Placeholder.unparsed("player", player.getName())));

        Logger.debug(Action.JOIN, "Greeted " + player.getName());
    }
}
```

---

## Notes

Configuration set through `Colorize.init` and `Logger.builder` is process-wide. Because
each plugin relocates the library into its own package, that state is per-plugin rather
than shared across the server.

---

## Licence

MIT
