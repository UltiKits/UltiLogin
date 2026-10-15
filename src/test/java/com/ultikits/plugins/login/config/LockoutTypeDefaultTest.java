package com.ultikits.plugins.login.config;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** Real framework initialisation, not a mock of config reads or writes. */
@DisplayName("BOTH default and byte-preserving configuration upgrades")
class LockoutTypeDefaultTest {
    private static final String KEY = "security.lockout-type";
    private static final String COMMENT_KEY = "login_config_comment_security_lockout_type";
    private static final String OLD_LITERAL = "封禁类型：IP / UUID / BOTH";
    private static final String OLD_EN = "Lockout type: IP / UUID / BOTH. Failed logins are counted by the same key the type locks: IP counts and locks per IP address (accounts behind one address share the count, and one player's wrong passwords can lock the others out); UUID counts and locks per account; BOTH keeps both counts and locks the address when its count reaches the limit and the account when its own count does";
    private static final String OLD_ZH = "封禁类型：IP / UUID / BOTH。失败次数按与封禁相同的键计数：IP 按 IP 地址计数并封禁（同一地址的账号共用计数，一名玩家输错密码可能把其他人一起锁住）；UUID 按账号计数并封禁；BOTH 两种计数都保留，地址的计数达到上限时封禁该地址，账号自己的计数达到上限时封禁该账号";

    @TempDir Path tempDir;
    private UltiToolsPlugin plugin;
    private final PluginLogger logger = mock(PluginLogger.class);

    private Path file() { return tempDir.resolve(LoginConfig.CONFIG_FILE); }
    private String read() throws Exception { return new String(Files.readAllBytes(file()), StandardCharsets.UTF_8); }
    private void write(String text) throws Exception { Files.write(file(), text.getBytes(StandardCharsets.UTF_8)); }
    private byte[] sha256() throws Exception { return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file())); }

    private LoginConfig load(String language) throws Exception {
        Files.createDirectories(file().getParent());
        LoginConfig config = new LoginConfig();
        plugin = mock(UltiLogin.class, invocation -> {
            String name = invocation.getMethod().getName();
            if ("shippedCatalogueTexts".equals(name)) { return invocation.callRealMethod(); }
            if ("getConfigFolder".equals(name)) { return tempDir.toString(); }
            if ("getConfigFile".equals(name)) { return new File(tempDir.toFile(), invocation.<String>getArgument(0)); }
            if ("i18n".equals(name)) { return CatalogueText.answer(language).answer(invocation); }
            if ("getPluginName".equals(name)) { return "UltiLogin"; }
            if ("getLogger".equals(name)) { return logger; }
            if ("getConfig".equals(name)) { return config; }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        config.init(plugin);
        return config;
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static String value(String text, String value) {
        return text.replaceFirst("(?m)^(\\s*lockout-type: )[^\\n]*", "$1" + Matcher.quoteReplacement(value));
    }

    private static String comment(String text, String comment) {
        return text.replaceFirst("(?m)^  # [^\\n]*\\n(  lockout-type:)",
                Matcher.quoteReplacement("  # " + comment + "\n") + "$1");
    }

    private static String withoutKeyAndComment(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("lockout-type:")) {
                lines.remove(i);
                if (i > 0 && lines.get(i - 1).trim().startsWith("#")) { lines.remove(i - 1); }
                return String.join("\n", lines);
            }
        }
        throw new AssertionError("fixture has no lockout-type key");
    }

    @Test
    @DisplayName("A fresh installation writes and applies BOTH")
    void freshInstallWritesAndAppliesBoth() throws Exception {
        assertThat(load("en").getLockoutType()).isEqualTo("BOTH");
        assertThat(yaml(read()).getString(KEY)).isEqualTo("BOTH");
    }

    @Test
    @DisplayName("A missing key gets BOTH without changing any other line")
    void missingKeyPreservesEveryOtherByte() throws Exception {
        load("en");
        String others = withoutKeyAndComment(read());
        write(others);
        assertThat(load("en").getLockoutType()).isEqualTo("BOTH");
        assertThat(yaml(read()).getString(KEY)).isEqualTo("BOTH");
        assertThat(withoutKeyAndComment(read())).isEqualTo(others);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IP", "uuid"})
    @DisplayName("Existing IP and lowercase uuid value bytes survive two starts, proved by SHA-256")
    void existingValueIsByteIdenticalAcrossTwoStarts(String value) throws Exception {
        load("en");
        write(value(read(), value));
        byte[] before = sha256();
        for (int start = 0; start < 2; start++) {
            LoginConfig config = load("en");
            assertThat(config.getLockoutType()).isEqualTo(value);
            assertThat(LoginConfig.lockoutTypeOrDefault(config.getLockoutType())).isEqualTo(value.toUpperCase(java.util.Locale.ROOT));
            assertThat(sha256()).as("start %s", start + 1).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("An invalid ADDRESS value falls back to BOTH and warns without rewriting the file")
    void invalidValueFallsBackWarnsAndKeepsBytes() throws Exception {
        load("en");
        write(value(read(), "ADDRESS"));
        byte[] before = sha256();
        LoginConfig config = load("en");
        assertThat(config.getLockoutType()).isEqualTo("ADDRESS");
        assertThat(LoginConfig.lockoutTypeOrDefault(config.getLockoutType())).isEqualTo("BOTH");
        Method warning = UltiLogin.class.getDeclaredMethod("warnAboutUnusableLockoutType");
        warning.setAccessible(true);
        warning.invoke(plugin);
        verify(logger).warn(argThat((String message) -> message.contains("'ADDRESS'")
                && message.contains("the default, BOTH")));
        assertThat(sha256()).isEqualTo(before);
        load("en");
        assertThat(sha256()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "zh"})
    @DisplayName("Every previously shipped comment migrates while value bytes and second-start bytes stay intact")
    void everyOldCommentMigratesWithoutChangingValues(String language) throws Exception {
        load(language);
        String fresh = value(read(), "uuid");
        for (String oldComment : Arrays.asList(OLD_LITERAL, OLD_EN, OLD_ZH)) {
            String old = comment(fresh, oldComment);
            assertThat(old).as("old comment fixture").contains("  # " + oldComment + "\n  lockout-type: uuid");
            write(old);
            assertThat(load(language).getLockoutType()).isEqualTo("uuid");
            String after = read();
            assertThat(yaml(after).getComments(KEY)).containsExactly(CatalogueText.text(language, COMMENT_KEY));
            assertThat(withoutKeyAndComment(after)).isEqualTo(withoutKeyAndComment(old));
            assertThat(after).contains("  lockout-type: uuid");
            byte[] firstStart = sha256();
            load(language);
            assertThat(sha256()).isEqualTo(firstStart);
        }
    }

    @Test
    @DisplayName("An operator-written comment and mixed-case value are never replaced")
    void operatorCommentAndValueRemainUntouched() throws Exception {
        load("en");
        String own = comment(value(read(), "iP"), "my own lockout policy");
        assertThat(own).contains("# my own lockout policy");
        write(own);
        load("en");
        assertThat(read()).isEqualTo(own);
    }
}
