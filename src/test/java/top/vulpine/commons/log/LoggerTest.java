package top.vulpine.commons.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Logger")
class LoggerTest {

    private Recorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new Recorder();
        Logger.builder().logger(recorder.logger()).build();
    }

    @AfterEach
    void tearDown() {
        Logger.close();
    }

    private enum Action implements LogAction {
        SETUP
    }

    /**
     * Records what reaches the platform.
     *
     * <p>{@link ComponentLogger} extends the SLF4J one and carries some fifty methods, so it is
     * stood up as a dynamic proxy rather than implemented by hand. Only the level and the component
     * matter here, and both are on the call itself.</p>
     */
    private static final class Recorder {

        private final List<String> levels = new ArrayList<>();
        private final List<Component> lines = new ArrayList<>();

        ComponentLogger logger() {
            return (ComponentLogger) Proxy.newProxyInstance(
                    ComponentLogger.class.getClassLoader(),
                    new Class<?>[]{ComponentLogger.class},
                    (proxy, method, args) -> {

                        if (args != null && args.length == 1 && args[0] instanceof Component line) {
                            levels.add(method.getName());
                            lines.add(line);
                        }

                        return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
                    });
        }

        int size() {
            return lines.size();
        }

        String lastLevel() {
            return levels.get(levels.size() - 1);
        }

        String lastLine() {
            return PlainTextComponentSerializer.plainText().serialize(lines.get(lines.size() - 1));
        }
    }

    @Test
    @DisplayName("writes the message to the platform logger")
    void writesToTheLogger() {

        Logger.info("hello");

        assertEquals(1, recorder.size());
        assertTrue(recorder.lastLine().endsWith("hello"));
    }

    @Test
    @DisplayName("each level reaches the platform as that level, so filtering still works")
    void mapsLevels() {

        Logger.builder().logger(recorder.logger()).level(LogLevel.DEBUG).build();

        Logger.debug("d");
        assertEquals("debug", recorder.lastLevel());

        Logger.info("i");
        assertEquals("info", recorder.lastLevel());

        Logger.warn("w");
        assertEquals("warn", recorder.lastLevel());

        Logger.error("e");
        assertEquals("error", recorder.lastLevel());
    }

    @Test
    @DisplayName("drops anything below the configured level")
    void honoursTheThreshold() {

        Logger.builder().logger(recorder.logger()).level(LogLevel.WARN).build();

        Logger.debug("quiet");
        Logger.info("also quiet");
        Logger.warn("loud");

        assertEquals(1, recorder.size());
        assertTrue(recorder.lastLine().contains("loud"));
    }

    @Test
    @DisplayName("setLevel changes the threshold without disturbing the rest")
    void setLevelKeepsConfiguration() {

        Logger.setLevel(LogLevel.ERROR);
        Logger.warn("dropped");
        Logger.error("kept");

        assertEquals(1, recorder.size());
        assertTrue(recorder.lastLine().contains("kept"));

        Logger.setLevel(LogLevel.INFO);
    }

    @Test
    @DisplayName("tags the line with the action")
    void includesTheActionTag() {

        Logger.info(Action.SETUP, "starting");

        assertTrue(recorder.lastLine().contains("[SETUP]"));
    }

    @Test
    @DisplayName("names the calling class, and can be told not to")
    void showsTheCaller() {

        Logger.info("with caller");
        assertTrue(recorder.lastLine().contains("[LoggerTest]"));

        Logger.builder().logger(recorder.logger()).showCaller(false).build();
        Logger.info("without caller");

        assertFalse(recorder.lastLine().contains("LoggerTest"));
    }

    @Test
    @DisplayName("system lines ignore the threshold, for startup banners")
    void systemIgnoresTheThreshold() {

        Logger.builder().logger(recorder.logger()).level(LogLevel.ERROR).build();

        Logger.system("banner");

        assertEquals(1, recorder.size());
        assertEquals("info", recorder.lastLevel());
        assertTrue(recorder.lastLine().contains("banner"));
    }

    @Test
    @DisplayName("system lines carry no caller or action decoration")
    void systemIsUndecorated() {

        Logger.system("plain banner");

        assertEquals("plain banner", recorder.lastLine());
    }

    @Test
    @DisplayName("refuses to start without a logger, and says what to pass")
    void requiresALogger() {

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> Logger.builder().build());

        assertTrue(thrown.getMessage().contains("getComponentLogger"),
                "the message has to be actionable: " + thrown.getMessage());
    }

    @Test
    @DisplayName("the trace file records lines with the colour stripped")
    void writesTheTraceFile(@TempDir Path dataFolder) throws IOException {

        Logger.builder()
                .logger(recorder.logger())
                .trace(dataFolder.toFile())
                .build();

        Logger.warn(Action.SETUP, "<red>something odd");
        Logger.close();

        try (var files = Files.list(dataFolder.resolve("logs"))) {

            String contents = Files.readString(files.findFirst().orElseThrow());

            assertTrue(contents.contains("[WARN]"));
            assertTrue(contents.contains("[SETUP]"));
            assertTrue(contents.contains("something odd"));
            assertFalse(contents.contains("<red>"), "markup is resolved, not written literally");
        }
    }

}
