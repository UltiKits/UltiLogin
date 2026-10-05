package com.ultikits.plugins.login.config;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The comments above the keys of {@code config/login.yml} and {@code config/email.yml} come from the
 * module's language files, so a server set to English writes English comments (UltiKits/UltiLogin#46;
 * framework UltiTools-Reborn#542). Every case runs the framework's real
 * {@code AbstractConfigEntity#init} on a temporary folder and answers {@code i18n} from the module's real
 * catalogues.
 * <p>
 * 注释从语言文件取：英文服务器写入英文注释，中文服务器写入中文注释；升级时只改注释，不改值。
 */
@DisplayName("login.yml and email.yml write their comments in the server's language (#46)")
class LoginConfigCommentsTest {

    /** The keys of {@code config/login.yml}: path in the file, then the catalogue key of the comment above it. */
    private static final List<String[]> LOGIN_KEYS = Arrays.asList(
            new String[] {"login-timeout", "login_config_comment_login_timeout"},
            new String[] {"session-enabled", "login_config_comment_session_enabled"},
            new String[] {"session-timeout", "login_config_comment_session_timeout"},
            new String[] {"max-register-per-ip", "login_config_comment_max_register_per_ip"},
            new String[] {"gui-mode.enabled", "login_config_comment_gui_mode_enabled"},
            new String[] {"gui-mode.password-length", "login_config_comment_gui_mode_password_length"},
            new String[] {"gui-mode.title-login", "login_config_comment_gui_mode_title_login"},
            new String[] {"gui-mode.title-register", "login_config_comment_gui_mode_title_register"},
            new String[] {"gui-mode.title-confirm", "login_config_comment_gui_mode_title_confirm"},
            new String[] {"password.min-length", "login_config_comment_password_min_length"},
            new String[] {"password.max-length", "login_config_comment_password_max_length"},
            new String[] {"security.max-login-attempts", "login_config_comment_security_max_login_attempts"},
            new String[] {"security.lockout-duration", "login_config_comment_security_lockout_duration"},
            new String[] {"security.lockout-type", "login_config_comment_security_lockout_type"},
            new String[] {"spawn-location.enabled", "login_config_comment_spawn_location_enabled"},
            new String[] {"spawn-location.world", "login_config_comment_spawn_location_world"},
            new String[] {"spawn-location.x", "login_config_comment_spawn_location_x"},
            new String[] {"spawn-location.y", "login_config_comment_spawn_location_y"},
            new String[] {"spawn-location.z", "login_config_comment_spawn_location_z"},
            new String[] {"allowed-commands", "login_config_comment_allowed_commands"},
            new String[] {"blind-effect", "login_config_comment_blind_effect"},
            new String[] {"messages.register-prompt", "login_config_comment_messages_register_prompt"},
            new String[] {"messages.register-prompt-gui", "login_config_comment_messages_register_prompt_gui"},
            new String[] {"messages.login-prompt", "login_config_comment_messages_login_prompt"},
            new String[] {"messages.login-prompt-gui", "login_config_comment_messages_login_prompt_gui"},
            new String[] {"messages.register-success", "login_config_comment_messages_register_success"},
            new String[] {"messages.login-success", "login_config_comment_messages_login_success"},
            new String[] {"messages.already-logged", "login_config_comment_messages_already_logged"},
            new String[] {"messages.not-registered", "login_config_comment_messages_not_registered"},
            new String[] {"messages.already-registered", "login_config_comment_messages_already_registered"},
            new String[] {"messages.password-mismatch", "login_config_comment_messages_password_mismatch"},
            new String[] {"messages.password-too-short", "login_config_comment_messages_password_too_short"},
            new String[] {"messages.password-too-long", "login_config_comment_messages_password_too_long"},
            new String[] {"messages.timeout-kick", "login_config_comment_messages_timeout_kick"},
            new String[] {"messages.account-locked", "login_config_comment_messages_account_locked"},
            new String[] {"messages.attempts-remaining", "login_config_comment_messages_attempts_remaining"},
            new String[] {"messages.gui-password-invalid", "login_config_comment_messages_gui_password_invalid"},
            new String[] {"messages.admin.password-reset", "login_config_comment_messages_admin_password_reset"},
            new String[] {"messages.admin.force-login", "login_config_comment_messages_admin_force_login"},
            new String[] {"messages.admin.unregister", "login_config_comment_messages_admin_unregister"},
            new String[] {"messages.admin.player-not-found", "login_config_comment_messages_admin_player_not_found"},
            new String[] {"messages.admin.account-not-found", "login_config_comment_messages_admin_account_not_found"});

    /** The keys of {@code config/email.yml}: path in the file, then the catalogue key of the comment above it. */
    private static final List<String[]> EMAIL_KEYS = Arrays.asList(
            new String[] {"verification.code-length", "login_config_comment_email_verification_code_length"},
            new String[] {"verification.code-expiry-seconds", "login_config_comment_email_verification_code_expiry_seconds"},
            new String[] {"verification.max-attempts", "login_config_comment_email_verification_max_attempts"},
            new String[] {"verification.cooldown-seconds", "login_config_comment_email_verification_cooldown_seconds"},
            new String[] {"domain-blacklist", "login_config_comment_email_domain_blacklist"},
            new String[] {"max-accounts-per-email", "login_config_comment_email_max_accounts_per_email"},
            new String[] {"reward.enabled", "login_config_comment_email_reward_enabled"},
            new String[] {"reward.commands", "login_config_comment_email_reward_commands"});

    /** What earlier versions wrote above {@code security.lockout-type}: the catalogue's text before #45 extended it. */
    private static final String OLD_LOCKOUT_TYPE_ZH = "\u5c01\u7981\u7c7b\u578b\uff1aIP / UUID / BOTH";

    @TempDir
    Path tempDir;

    private File file(String relative) {
        return new File(tempDir.toFile(), relative);
    }

    private String read(String relative) throws IOException {
        return new String(Files.readAllBytes(file(relative).toPath()), StandardCharsets.UTF_8);
    }

    private <T extends AbstractConfigEntity> T load(T config, String language) throws IOException {
        Files.createDirectories(file("config").toPath());
        // The module's own class, so the framework's real shippedCatalogueTexts reads this module's catalogues from
        // its code source and recognises a comment it wrote in either language as its own (framework #604, PR #611).
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
        config.init(plugin);
        return config;
    }

    /** The comment above {@code path}, read through YAML so that nested keys sharing a name are told apart. */
    private static String commentAbove(String text, String path) {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new AssertionError("unreadable YAML:\n" + text, e);
        }
        List<String> comments = yaml.getComments(path);
        if (comments.size() != 1) {
            throw new AssertionError("expected one comment line above " + path + " but found " + comments + " in:\n" + text);
        }
        return comments.get(0).trim();
    }

    private static void assertComments(String text, List<String[]> keys, String language) {
        for (String[] key : keys) {
            assertThat(commentAbove(text, key[0])).as("comment above " + key[0] + " (" + language + ")")
                    .isEqualTo(CatalogueText.text(language, key[1]));
        }
    }

    @Test
    @DisplayName("a fresh install under language: en writes the English comment above every key of both files, and no Chinese one")
    void freshInstallWritesEnglishComments() throws IOException {
        load(new LoginConfig(), "en");
        load(new EmailConfig(), "en");

        assertComments(read("config/login.yml"), LOGIN_KEYS, "en");
        assertComments(read("config/email.yml"), EMAIL_KEYS, "en");
        assertThat(read("config/login.yml")).as("no Chinese comment in login.yml")
                .doesNotContainPattern("#[^\\n]*[\\u4e00-\\u9fff]");
        assertThat(read("config/email.yml")).as("no Chinese comment in email.yml")
                .doesNotContainPattern("#[^\\n]*[\\u4e00-\\u9fff]");
    }

    @Test
    @DisplayName("a fresh install under language: zh writes the Chinese comment above every key of both files")
    void freshInstallWritesChineseComments() throws IOException {
        load(new LoginConfig(), "zh");
        load(new EmailConfig(), "zh");

        assertComments(read("config/login.yml"), LOGIN_KEYS, "zh");
        assertComments(read("config/email.yml"), EMAIL_KEYS, "zh");
    }

    @Test
    @DisplayName("the lockout-type comment says failed logins are counted per address, what each type counts and locks (#45, #48)")
    void lockoutTypeCommentStatesTheSharedCounter() {
        String en = CatalogueText.text("en", "login_config_comment_security_lockout_type");
        assertThat(en).contains("IP / UUID / BOTH").contains("counted by the same key the type locks")
                .contains("per IP address").contains("share the count")
                .contains("UUID counts and locks per account").contains("BOTH keeps both counts");
        String zh = CatalogueText.text("zh", "login_config_comment_security_lockout_type");
        assertThat(zh).contains("IP / UUID / BOTH").contains("\u5171\u7528\u8ba1\u6570")
                .contains("UUID \u6309\u8d26\u53f7\u8ba1\u6570\u5e76\u5c01\u7981");
    }

    @Test
    @DisplayName("an upgrade: a login.yml written with the Chinese comments gets the English ones, its values stay, and a second start leaves it byte for byte")
    void upgradeSwitchesTheCommentsAndKeepsTheValues() throws IOException {
        Files.createDirectories(file("config").toPath());
        org.bukkit.configuration.file.YamlConfiguration old = new org.bukkit.configuration.file.YamlConfiguration();
        for (String[] key : LOGIN_KEYS) {
            String oldZh = "login_config_comment_security_lockout_type".equals(key[1])
                    ? OLD_LOCKOUT_TYPE_ZH : CatalogueText.text("zh", key[1]);
            // a value of the right type for every key is written by the framework below; only the comment and
            // the values the operator changed are written here
            old.set(key[0], defaultOf(key[0]));
            old.setComments(key[0], Arrays.asList(oldZh));
        }
        old.set("login-timeout", 120);
        old.set("security.lockout-type", "UUID");
        old.set("password.min-length", 8);
        old.save(file("config/login.yml"));
        assertThat(commentAbove(read("config/login.yml"), "security.lockout-type")).isEqualTo(OLD_LOCKOUT_TYPE_ZH);

        LoginConfig first = load(new LoginConfig(), "en");

        String afterFirst = read("config/login.yml");
        assertComments(afterFirst, LOGIN_KEYS, "en");
        assertThat(first.getLoginTimeout()).isEqualTo(120);
        assertThat(first.getLockoutType()).isEqualTo("UUID");
        assertThat(first.getMinPasswordLength()).isEqualTo(8);

        load(new LoginConfig(), "en");

        assertThat(read("config/login.yml")).as("the second start").isEqualTo(afterFirst);
    }

    /** A value of the type the key holds, the module's own default. */
    private static Object defaultOf(String path) {
        LoginConfig defaults = new LoginConfig();
        for (java.lang.reflect.Field field : LoginConfig.class.getDeclaredFields()) {
            com.ultikits.ultitools.annotations.ConfigEntry entry =
                    field.getAnnotation(com.ultikits.ultitools.annotations.ConfigEntry.class);
            if (entry != null && entry.path().equals(path)) {
                field.setAccessible(true);
                try {
                    return field.get(defaults);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        throw new AssertionError("no field for " + path);
    }
}
