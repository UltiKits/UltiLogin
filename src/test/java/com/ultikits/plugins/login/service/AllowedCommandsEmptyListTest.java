package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.ultitools.manager.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

/**
 * What {@code allowed-commands: []} in {@code config/login.yml} does on UltiTools 6.3.0
 * (UltiKits/UltiLogin#52; the framework side is UltiKits/UltiTools-Reborn#630, maintainer decision of
 * 2026-10-06, row 00:46 of the overnight decisions).
 * <p>
 * The setting is declared {@code @NotEmpty}. Before 6.3.0 that did nothing on a list, so an empty list loaded as
 * empty and {@link LoginService#isCommandAllowed} refused every command for a player who had not logged in,
 * {@code /login} and {@code /register} included. Now the framework runs the declared default list in memory, logs one
 * WARNING that names the key, the value as written and the default, and leaves the file untouched.
 * <p>
 * Every test drives the REAL {@link ConfigManager}, the REAL {@link LoginConfig} and a REAL {@link LoginService}
 * bound to the same {@code LoginConfig} instance, against a {@code login.yml} written by the test, and reads the
 * WARNING from the framework's own logger. No test in this module ever assumed that an empty list blocks every
 * command: the module's other tests stub {@code getAllowedCommands()} with a non-empty list.
 */
class AllowedCommandsEmptyListTest {

    private static final List<String> DECLARED_DEFAULT =
            Arrays.asList("login", "l", "register", "reg", "panel", "regs", "recover");

    /** The framework class whose logger carries the substitution WARNING. */
    private static final Logger FRAMEWORK_LOGGER =
            Logger.getLogger("com.ultikits.ultitools.abstracts.AbstractConfigEntity");

    @TempDir
    Path configRoot;

    private final List<String> warnings = new ArrayList<>();
    private Handler capture;
    private File loginYml;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.bootstrapLiveServer();
        UltiLoginTestHelper.setUp();
        loginYml = configRoot.resolve("config").resolve("login.yml").toFile();
        assertThat(loginYml.getParentFile().mkdirs()).as("temp config directory must be created").isTrue();
        capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
                // nothing is buffered
            }

            @Override
            public void close() {
                // nothing is held
            }
        };
        FRAMEWORK_LOGGER.addHandler(capture);
    }

    @AfterEach
    void tearDown() throws Exception {
        FRAMEWORK_LOGGER.removeHandler(capture);
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    @Test
    @DisplayName("allowed-commands: [] runs the declared default list, warns once naming the key, and leaves the file alone")
    void emptyListRunsTheDeclaredDefault() throws Exception {
        write("allowed-commands: []\n");

        Loaded loaded = load();

        assertThat(loaded.config.getAllowedCommands())
                .as("the empty list is replaced in memory by the declared default, not kept empty")
                .containsExactlyElementsOf(DECLARED_DEFAULT);
        assertThat(loaded.service.isCommandAllowed("/login password"))
                .as("an empty list used to refuse /login itself, a lock-out").isTrue();
        assertThat(loaded.service.isCommandAllowed("/register pw pw")).isTrue();
        assertThat(loaded.service.isCommandAllowed("/recover")).isTrue();
        assertThat(loaded.service.isCommandAllowed("/help"))
                .as("the default list is the shipped one, so /help stays refused").isFalse();

        List<String> mine = warningsNamingTheKey();
        assertThat(mine).as("one WARNING names allowed-commands").hasSize(1);
        assertThat(mine.get(0))
                .contains("key 'allowed-commands'")
                .contains("the list is empty (found [])")
                .contains("declared @NotEmpty")
                .contains("using the declared default [login, l, register, reg, panel, regs, recover] in memory")
                .contains("(the file is not changed)");

        assertThat(warnings).as("no 'not written' or 'never saved' line beside the framework's WARNING")
                .noneMatch(w -> w != null && (w.contains("not written") || w.contains("never saved")));
        assertThat(loaded.config.isModifiedSinceSnapshot())
                .as("nothing is left unsaved, so the stop report has nothing to name").isFalse();

        assertThat(Files.readAllLines(loginYml.toPath(), StandardCharsets.UTF_8))
                .as("operator-written configuration is never overwritten: the empty list is still on disk")
                .contains("allowed-commands: []")
                .doesNotContain("  - login", "- login");
    }

    @Test
    @DisplayName("allowed-commands: ~ (null) runs the declared default list with one warning, file untouched")
    void nullListRunsTheDeclaredDefault() throws Exception {
        write("allowed-commands: ~\n");

        Loaded loaded = load();

        assertThat(loaded.config.getAllowedCommands()).containsExactlyElementsOf(DECLARED_DEFAULT);
        assertThat(loaded.service.isCommandAllowed("/login password")).isTrue();
        List<String> mine = warningsNamingTheKey();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).contains("found null").contains("the declared default [login, l, register, reg, panel, regs, recover]");
        assertThat(warnings).as("no 'not written' or 'never saved' line beside the framework's WARNING")
                .noneMatch(w -> w != null && (w.contains("not written") || w.contains("never saved")));
        assertThat(loaded.config.isModifiedSinceSnapshot()).isFalse();
        assertThat(Files.readAllLines(loginYml.toPath(), StandardCharsets.UTF_8)).contains("allowed-commands: ~");
    }

    @Test
    @DisplayName("control: a non-empty list is kept exactly as written and nothing is warned")
    void aNonEmptyListIsKept() throws Exception {
        write("allowed-commands:\n  - help\n");

        Loaded loaded = load();

        assertThat(loaded.config.getAllowedCommands()).containsExactly("help");
        assertThat(loaded.service.isCommandAllowed("/help")).isTrue();
        assertThat(loaded.service.isCommandAllowed("/login password"))
                .as("the operator's own list is theirs: login is not on it, so it stays refused").isFalse();
        assertThat(warningsNamingTheKey()).as("no substitution, no WARNING").isEmpty();
    }

    @Test
    @DisplayName("/ul reload after the operator blanks the list: the default runs again with one warning, both directions")
    void reloadAfterBlankingTheList() throws Exception {
        write("allowed-commands:\n  - help\n");
        Loaded loaded = load();
        assertThat(loaded.service.isCommandAllowed("/help")).isTrue();
        assertThat(warningsNamingTheKey()).isEmpty();

        write("allowed-commands: []\n");
        loaded.configManager.reloadConfigs(loaded.plugin);

        assertThat(loaded.config.getAllowedCommands()).containsExactlyElementsOf(DECLARED_DEFAULT);
        assertThat(loaded.service.isCommandAllowed("/login password")).isTrue();
        assertThat(loaded.service.isCommandAllowed("/help")).isFalse();
        assertThat(warningsNamingTheKey()).hasSize(1);
        assertThat(Files.readAllLines(loginYml.toPath(), StandardCharsets.UTF_8)).contains("allowed-commands: []");

        write("allowed-commands:\n  - help\n");
        loaded.configManager.reloadConfigs(loaded.plugin);

        assertThat(loaded.config.getAllowedCommands()).containsExactly("help");
        assertThat(loaded.service.isCommandAllowed("/help")).isTrue();
        assertThat(warningsNamingTheKey()).as("the second reload substitutes nothing").hasSize(1);
    }

    private List<String> warningsNamingTheKey() {
        List<String> mine = new ArrayList<>();
        for (String w : warnings) {
            if (w != null && w.contains("key 'allowed-commands'")) {
                mine.add(w);
            }
        }
        return mine;
    }

    private void write(String yaml) throws Exception {
        Files.write(loginYml.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    private Loaded load() throws Exception {
        UltiLogin plugin = mock(UltiLogin.class, (Answer<Object>) invocation -> {
            String name = invocation.getMethod().getName();
            if ("getConfigFile".equals(name)) {
                return new File(configRoot.toFile(), (String) invocation.getArguments()[0]);
            }
            if ("getConfigFolder".equals(name) || "getResourceFolderPath".equals(name)) {
                return configRoot.toFile().getAbsolutePath();
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        LoginConfig config = new LoginConfig();
        ConfigManager configManager = new ConfigManager();
        configManager.register(plugin, config);
        LoginService service = new LoginService(plugin, config);
        return new Loaded(plugin, config, configManager, service);
    }

    private static final class Loaded {
        final UltiLogin plugin;
        final LoginConfig config;
        final ConfigManager configManager;
        final LoginService service;

        Loaded(UltiLogin plugin, LoginConfig config, ConfigManager configManager, LoginService service) {
            this.plugin = plugin;
            this.config = config;
            this.configManager = configManager;
            this.service = service;
        }
    }
}
