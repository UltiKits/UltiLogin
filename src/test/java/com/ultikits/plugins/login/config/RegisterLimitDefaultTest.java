package com.ultikits.plugins.login.config;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code max-register-per-ip} ships disabled ({@code 0}) because many players can share one public IP
 * address (carrier-grade NAT, notably in mainland China), so a limit can stop legitimate players from
 * registering (maintainer decision 2026-10-09). The new default reaches only a file that does not have the
 * key yet: an operator's existing value is never rewritten (framework rule: operator-written configuration
 * is never overwritten automatically).
 * <p>
 * Every case runs the framework's real {@code AbstractConfigEntity#init} on a temporary folder.
 * <p>
 * {@code max-register-per-ip} 默认关闭（0）；已有文件里的值保持不变，只有缺少该键的文件才写入新默认值。
 */
@DisplayName("max-register-per-ip ships disabled; an existing value is kept")
class RegisterLimitDefaultTest {

    private static final String KEY = "max-register-per-ip";
    /** The comment every earlier version wrote above the key, per language. */
    private static final String OLD_EN = "Maximum accounts that can be registered from one IP (0 = unlimited)";
    private static final String OLD_ZH = "同一IP最大注册账户数（0为不限制）";

    @TempDir
    Path tempDir;

    private File loginYml() {
        return new File(tempDir.toFile(), LoginConfig.CONFIG_FILE);
    }

    private String read() throws IOException {
        return new String(Files.readAllBytes(loginYml().toPath()), StandardCharsets.UTF_8);
    }

    private void write(String text) throws IOException {
        Files.write(loginYml().toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private LoginConfig load(String language) throws IOException {
        Files.createDirectories(loginYml().getParentFile().toPath());
        UltiToolsPlugin plugin = Mockito.mock(UltiLogin.class, invocation -> {
            String name = invocation.getMethod().getName();
            if ("shippedCatalogueTexts".equals(name)) {
                return invocation.callRealMethod();
            }
            if ("getConfigFolder".equals(name)) {
                return tempDir.toString();
            }
            if ("getConfigFile".equals(name)) {
                return new File(tempDir.toFile(), invocation.<String>getArgument(0));
            }
            if ("i18n".equals(name)) {
                return CatalogueText.answer(language).answer(invocation);
            }
            if ("getPluginName".equals(name)) {
                return "UltiLogin";
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        LoginConfig config = new LoginConfig();
        config.init(plugin);
        return config;
    }

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new AssertionError("unreadable YAML:\n" + text, e);
        }
        return yaml;
    }

    /** The file's lines without the key's line and the comment line directly above it. */
    private static List<String> withoutKey(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(KEY + ":")) {
                lines.remove(i);
                if (i > 0 && lines.get(i - 1).startsWith("#")) {
                    lines.remove(i - 1);
                }
                return lines;
            }
        }
        throw new AssertionError("no " + KEY + " line in:\n" + text);
    }

    @Test
    @DisplayName("a fresh install writes max-register-per-ip: 0 and runs with the limit off")
    void freshInstallIsDisabled() throws IOException {
        LoginConfig config = load("en");

        assertThat(config.getMaxRegisterPerIp()).isZero();
        assertThat(yaml(read()).getInt(KEY, -1)).as("the written value").isZero();
    }

    @Test
    @DisplayName("a file without the key gets 0, and every other line stays byte for byte")
    void missingKeyGetsDisabledDefault() throws IOException {
        load("en");
        String full = read();
        List<String> others = withoutKey(full);
        write(String.join("\n", others));

        LoginConfig config = load("en");

        assertThat(config.getMaxRegisterPerIp()).isZero();
        String after = read();
        assertThat(yaml(after).getInt(KEY, -1)).as("the inserted value").isZero();
        assertThat(withoutKey(after)).as("every other line").isEqualTo(others);
    }

    @Test
    @DisplayName("an upgraded file keeps its value 3; only the old shipped comment is replaced; a second start writes nothing")
    void existingValueIsKept() throws IOException {
        load("en");
        String fresh = read();
        // What an earlier version wrote: the old default value and the old comment.
        String old = fresh.replaceFirst("(?m)^" + KEY + ": 0$", KEY + ": 3");
        old = old.replaceFirst("(?m)^# [^\\n]*\\n(" + KEY + ": 3)", "# " + OLD_EN + "\n$1");
        assertThat(old).as("fixture").contains("# " + OLD_EN + "\n" + KEY + ": 3");
        write(old);

        LoginConfig config = load("en");

        assertThat(config.getMaxRegisterPerIp()).as("the operator's value").isEqualTo(3);
        String after = read();
        assertThat(yaml(after).getInt(KEY, -1)).isEqualTo(3);
        assertThat(yaml(after).getComments(KEY)).as("the old shipped comment is now the warning")
                .containsExactly(CatalogueText.text("en", "login_config_comment_max_register_per_ip"));
        assertThat(withoutKey(after)).as("every other line").isEqualTo(withoutKey(old));

        load("en");
        assertThat(read()).as("the second start").isEqualTo(after);
    }

    @Test
    @DisplayName("the old Chinese comment is recognised too; an operator's own comment is kept")
    void oldChineseCommentReplacedOperatorCommentKept() throws IOException {
        load("zh");
        String fresh = read();
        String old = fresh.replaceFirst("(?m)^# [^\\n]*\\n(" + KEY + ": 0)", "# " + OLD_ZH + "\n$1");
        write(old);
        assertThat(load("zh").getMaxRegisterPerIp()).isZero();
        assertThat(yaml(read()).getComments(KEY))
                .containsExactly(CatalogueText.text("zh", "login_config_comment_max_register_per_ip"));

        String own = read().replaceFirst("(?m)^# [^\\n]*\\n(" + KEY + ": 0)", "# my note\n$1");
        write(own);
        load("zh");
        assertThat(read()).as("an operator's comment").isEqualTo(own);
    }

    @Test
    @DisplayName("the comment warns that a limit can block players who share one public IP, and covers web registration")
    void commentWarnsAboutSharedAddresses() {
        String en = CatalogueText.text("en", "login_config_comment_max_register_per_ip");
        assertThat(en).contains("0 = unlimited").contains("default").contains("share one public IP")
                .contains("mainland China").contains("/panel");
        String zh = CatalogueText.text("zh", "login_config_comment_max_register_per_ip");
        assertThat(zh).contains("0").contains("公网 IP").contains("/panel");
    }
}
