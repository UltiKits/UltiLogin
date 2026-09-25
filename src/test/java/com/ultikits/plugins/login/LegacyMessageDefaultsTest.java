package com.ultikits.plugins.login;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An operator file still holding a text default an earlier version shipped (the three GUI titles and
 * twenty-one messages were Chinese) is rewritten to blank and saved, at start-up and on reload, so the
 * language file's text takes over in the server's language; any other value is the operator's and is
 * kept (maintainer ruling 2026-09-24 (d), UltiKits/UltiLogin#20).
 * <p>
 * The shipped values are copied from this module's history (every revision of {@code LoginConfig}):
 * each setting had exactly one default until this change.
 */
@DisplayName("Shipped Chinese text defaults give way to the language file (UltiKits/UltiLogin#20)")
class LegacyMessageDefaultsTest {

    private static final Map<String, String> SHIPPED = new LinkedHashMap<>();

    static {
        SHIPPED.put("accountLocked", "&c登录失败次数过多！请在 {TIME} 秒后重试。");
        SHIPPED.put("adminAccountNotFound", "&c玩家 {PLAYER} 尚未注册");
        SHIPPED.put("adminForceLogin", "&a已强制登录玩家 {PLAYER}");
        SHIPPED.put("adminPasswordReset", "&a已重置玩家 {PLAYER} 的密码为: {PASSWORD}");
        SHIPPED.put("adminPlayerNotFound", "&c找不到玩家 {PLAYER}");
        SHIPPED.put("adminUnregister", "&a已删除玩家 {PLAYER} 的账号");
        SHIPPED.put("alreadyLogged", "&e你已经登录了！");
        SHIPPED.put("alreadyRegistered", "&c你已经注册过了！请直接登录。");
        SHIPPED.put("attemptsRemaining", "&c密码错误！剩余尝试次数: {COUNT}");
        SHIPPED.put("guiConfirmTitle", "&6请再次输入密码");
        SHIPPED.put("guiLoginTitle", "&6请输入密码");
        SHIPPED.put("guiPasswordInvalid", "&c密码必须是 {LENGTH} 位数字！");
        SHIPPED.put("guiRegisterTitle", "&6请设置密码");
        SHIPPED.put("loginPrompt", "&e请使用 /login <密码> 登录");
        SHIPPED.put("loginPromptGui", "&e请在弹出的界面中输入密码");
        SHIPPED.put("loginSuccess", "&a登录成功！欢迎回来！");
        SHIPPED.put("notRegistered", "&c你还没有注册！请先注册。");
        SHIPPED.put("passwordMismatch", "&c两次输入的密码不一致！");
        SHIPPED.put("passwordTooLong", "&c密码太长！最多 {MAX} 个字符。");
        SHIPPED.put("passwordTooShort", "&c密码太短！至少需要 {MIN} 个字符。");
        SHIPPED.put("registerPrompt", "&e请使用 /register <密码> <确认密码> 注册账号");
        SHIPPED.put("registerPromptGui", "&e请在弹出的界面中设置密码");
        SHIPPED.put("registerSuccess", "&a注册成功！欢迎加入服务器！");
        SHIPPED.put("timeoutKick", "&c登录超时！请重新连接。");
    }

    private static void set(LoginConfig config, String field, String value) throws Exception {
        Field f = LoginConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(config, value);
    }

    private static String get(LoginConfig config, String field) throws Exception {
        Field f = LoginConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        return (String) f.get(config);
    }

    private LoginConfig shipped() throws Exception {
        LoginConfig config = spy(new LoginConfig());
        doNothing().when(config).save();
        for (Map.Entry<String, String> e : SHIPPED.entrySet()) {
            set(config, e.getKey(), e.getValue());
        }
        return config;
    }

    private UltiLogin pluginWith(LoginConfig config, String language) {
        UltiLogin plugin = mock(UltiLogin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        when(plugin.getConfig(LoginConfig.class)).thenReturn(config);
        return plugin;
    }

    private void assertAllBlank(LoginConfig config) throws Exception {
        for (String field : SHIPPED.keySet()) {
            assertThat(get(config, field)).as(field).isEmpty();
        }
    }

    @Test
    @DisplayName("start-up blanks every shipped default and saves the file")
    void startUp() throws Exception {
        LoginConfig config = shipped();
        UltiLogin plugin = pluginWith(config, "en");
        when(plugin.registerSelf()).thenCallRealMethod();

        plugin.registerSelf();

        assertAllBlank(config);
        verify(config).save();
    }

    @Test
    @DisplayName("/ul reload blanks every shipped default and saves the file")
    void reload() throws Exception {
        LoginConfig config = shipped();
        UltiLogin plugin = pluginWith(config, "en");
        doCallRealMethod().when(plugin).onReload();

        plugin.onReload();

        assertAllBlank(config);
        verify(config).save();
    }

    @Test
    @DisplayName("a customised value is kept while the shipped ones beside it are blanked")
    void customisedIsKept() throws Exception {
        LoginConfig config = shipped();
        set(config, "loginSuccess", "&aWelcome back, adventurer!");
        set(config, "guiLoginTitle", SHIPPED.get("guiLoginTitle") + " ");
        UltiLogin plugin = pluginWith(config, "en");
        when(plugin.registerSelf()).thenCallRealMethod();

        plugin.registerSelf();

        assertThat(get(config, "loginSuccess")).isEqualTo("&aWelcome back, adventurer!");
        assertThat(get(config, "guiLoginTitle")).isEqualTo(SHIPPED.get("guiLoginTitle") + " ");
        assertThat(Arrays.asList(get(config, "registerSuccess"), get(config, "adminAccountNotFound"),
                get(config, "guiConfirmTitle"))).containsOnly("");
        verify(config).save();
    }

    @Test
    @DisplayName("a file already blank is not rewritten on the next start")
    void blankIsNotRewritten() throws Exception {
        LoginConfig config = shipped();
        for (String field : SHIPPED.keySet()) {
            set(config, field, "");
        }
        UltiLogin plugin = pluginWith(config, "en");
        when(plugin.registerSelf()).thenCallRealMethod();

        plugin.registerSelf();

        verify(config, never()).save();
    }

    @Test
    @DisplayName("under language: zh a rewritten server shows exactly the text it showed before")
    void chineseServerUnchanged() throws Exception {
        LoginConfig config = shipped();
        UltiLogin plugin = pluginWith(config, "zh");
        com.ultikits.plugins.login.i18n.LoginSeams.bind(config, plugin);
        when(plugin.registerSelf()).thenCallRealMethod();

        plugin.registerSelf();

        for (Map.Entry<String, String> e : SHIPPED.entrySet()) {
            String field = e.getKey();
            String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            assertThat(LoginConfig.class.getMethod(getter).invoke(config)).as(field).isEqualTo(e.getValue());
        }
    }

    @Test
    @DisplayName("a failed save is reported in the configured language and costs nothing else")
    void saveFailureReported() throws Exception {
        LoginConfig config = shipped();
        doThrow(new IOException("read-only")).when(config).save();
        UltiLogin plugin = pluginWith(config, "zh");
        when(plugin.registerSelf()).thenCallRealMethod();

        assertThat(plugin.registerSelf()).isTrue();

        String expected = CatalogueText.entries("zh").getOrDefault("log_config_default_save_failed",
                "<lang/zh has no log_config_default_save_failed>").replace("{FILE}", LoginConfig.CONFIG_FILE);
        verify(plugin.getLogger()).warn(any(IOException.class), eq(expected));
    }
}
