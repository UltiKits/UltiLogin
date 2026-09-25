package com.ultikits.plugins.login.config;

import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.i18n.LoginSeams;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A GUI title or message left blank in {@code config/login.yml} reads the language file's text, in the
 * server's language; a non-blank value is the operator's and is used as written (maintainer ruling
 * 2026-09-24 (d), UltiKits/UltiLogin#20). Every setting is covered, each through its own getter, which
 * is what every reader of the setting calls.
 */
@DisplayName("A blank text setting reads the language file (UltiKits/UltiLogin#20)")
class BlankMessageFallbackTest {

    /** Each text setting's field, mapped to the language-file key its blank value falls back to. */
    private static final Map<String, String> KEYS = new LinkedHashMap<>();

    static {
        KEYS.put("accountLocked", "account_locked");
        KEYS.put("adminAccountNotFound", "admin_account_not_found");
        KEYS.put("adminForceLogin", "admin_force_login");
        KEYS.put("adminPasswordReset", "admin_password_reset");
        KEYS.put("adminPlayerNotFound", "admin_player_not_found");
        KEYS.put("adminUnregister", "admin_unregister");
        KEYS.put("alreadyLogged", "already_logged");
        KEYS.put("alreadyRegistered", "already_registered");
        KEYS.put("attemptsRemaining", "attempts_remaining");
        KEYS.put("guiConfirmTitle", "gui_confirm_password");
        KEYS.put("guiLoginTitle", "gui_enter_password");
        KEYS.put("guiPasswordInvalid", "gui_password_invalid");
        KEYS.put("guiRegisterTitle", "gui_set_password");
        KEYS.put("loginPrompt", "login_prompt");
        KEYS.put("loginPromptGui", "login_prompt_gui");
        KEYS.put("loginSuccess", "login_success");
        KEYS.put("notRegistered", "not_registered");
        KEYS.put("passwordMismatch", "password_mismatch");
        KEYS.put("passwordTooLong", "password_too_long");
        KEYS.put("passwordTooShort", "password_too_short");
        KEYS.put("registerPrompt", "register_prompt");
        KEYS.put("registerPromptGui", "register_prompt_gui");
        KEYS.put("registerSuccess", "register_success");
        KEYS.put("timeoutKick", "timeout_kick");
    }

    /**
     * The colour each setting's shipped default opened with. A blank setting keeps it: the colour is
     * the module's, the words are the language file's, so an install whose extracted language file
     * predates the colour codes still shows each line in its colour (gate 1 WR-01).
     */
    private static final Map<String, String> COLOUR = new LinkedHashMap<>();

    static {
        COLOUR.put("accountLocked", "&c");
        COLOUR.put("adminAccountNotFound", "&c");
        COLOUR.put("adminForceLogin", "&a");
        COLOUR.put("adminPasswordReset", "&a");
        COLOUR.put("adminPlayerNotFound", "&c");
        COLOUR.put("adminUnregister", "&a");
        COLOUR.put("alreadyLogged", "&e");
        COLOUR.put("alreadyRegistered", "&c");
        COLOUR.put("attemptsRemaining", "&c");
        COLOUR.put("guiConfirmTitle", "&6");
        COLOUR.put("guiLoginTitle", "&6");
        COLOUR.put("guiPasswordInvalid", "&c");
        COLOUR.put("guiRegisterTitle", "&6");
        COLOUR.put("loginPrompt", "&e");
        COLOUR.put("loginPromptGui", "&e");
        COLOUR.put("loginSuccess", "&a");
        COLOUR.put("notRegistered", "&c");
        COLOUR.put("passwordMismatch", "&c");
        COLOUR.put("passwordTooLong", "&c");
        COLOUR.put("passwordTooShort", "&c");
        COLOUR.put("registerPrompt", "&e");
        COLOUR.put("registerPromptGui", "&e");
        COLOUR.put("registerSuccess", "&a");
        COLOUR.put("timeoutKick", "&c");
    }

    /** {@code text} as a player reads it: colour codes applied, then stripped. */
    private static String words(String text) {
        return org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
    }

    /** A blank {@code field} reads {@code key}'s words from the language file, in the setting's colour. */
    private static void assertFallback(LoginConfig config, String field, String language, String key) throws Exception {
        String shown = read(config, field);
        assertThat(words(shown)).as(field).isEqualTo(words(catalogue(language, key)));
        assertThat(shown).as(field + " keeps its colour").startsWith(COLOUR.get(field));
    }

    private static LoginConfig bound(String language) {
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        LoginConfig config = new LoginConfig();
        LoginSeams.bind(config, plugin);
        return config;
    }

    private static void set(LoginConfig config, String field, String value) throws Exception {
        Field f = LoginConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(config, value);
    }

    private static String read(LoginConfig config, String field) throws Exception {
        Method getter = LoginConfig.class.getMethod("get" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
        return (String) getter.invoke(config);
    }

    private static String catalogue(String language, String key) {
        return CatalogueText.entries(language).getOrDefault(key, "<lang/" + language + " has no " + key + ">");
    }

    @Test
    @DisplayName("blank reads the English text under language: en, for every setting")
    void blankReadsEnglish() throws Exception {
        LoginConfig config = bound("en");
        for (Map.Entry<String, String> e : KEYS.entrySet()) {
            set(config, e.getKey(), "");
            assertFallback(config, e.getKey(), "en", e.getValue());
        }
    }

    @Test
    @DisplayName("blank reads the Chinese text under language: zh, which is the text shipped before")
    void blankReadsChinese() throws Exception {
        LoginConfig config = bound("zh");
        for (Map.Entry<String, String> e : KEYS.entrySet()) {
            set(config, e.getKey(), "");
            assertFallback(config, e.getKey(), "zh", e.getValue());
        }
    }

    @Test
    @DisplayName("whitespace only counts as blank")
    void whitespaceIsBlank() throws Exception {
        LoginConfig config = bound("en");
        set(config, "loginSuccess", "   ");
        set(config, "guiLoginTitle", "\t");

        assertFallback(config, "loginSuccess", "en", "login_success");
        assertFallback(config, "guiLoginTitle", "en", "gui_enter_password");
    }

    @Test
    @DisplayName("a customised value is used as written, in any language")
    void customisedIsUsed() throws Exception {
        LoginConfig config = bound("en");
        set(config, "loginSuccess", "&a欢迎回来，冒险者！");
        set(config, "adminPlayerNotFound", "&cNo such player: {PLAYER}");

        assertThat(config.getLoginSuccess()).isEqualTo("&a欢迎回来，冒险者！");
        assertThat(config.getAdminPlayerNotFound()).isEqualTo("&cNo such player: {PLAYER}");
    }
}
