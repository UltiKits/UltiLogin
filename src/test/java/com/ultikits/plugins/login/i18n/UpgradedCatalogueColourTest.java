package com.ultikits.plugins.login.i18n;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.commands.LoginAdminCommand;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.entities.Language;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An install upgraded from an earlier version keeps the language file it extracted then, and the
 * framework reads that file first, falling back to the jar only for a key it lacks. The earlier file
 * held these keys already, with the same words and no colour codes, because no code read them. The
 * lines must still show in their colour: the colour is the module's, the words the language file's
 * (UltiKits/UltiLogin#20).
 * <p>
 * The values below are the ones the previous release shipped in {@code lang/zh.json}.
 */
@DisplayName("An earlier extracted language file still renders every line in its colour (UltiKits/UltiLogin#20)")
class UpgradedCatalogueColourTest {

    private static final Map<String, String> PREVIOUS_ZH = new HashMap<>();

    static {
        PREVIOUS_ZH.put("login_success", "登录成功！欢迎回来！");
        PREVIOUS_ZH.put("account_locked", "登录失败次数过多！请在 {TIME} 秒后重试。");
        PREVIOUS_ZH.put("gui_enter_password", "请输入密码");
        PREVIOUS_ZH.put("admin_account_not_found", "玩家 {PLAYER} 尚未注册");
        PREVIOUS_ZH.put("help_admin_header", "===== UltiLogin 管理命令 =====");
        PREVIOUS_ZH.put("help_admin_reset", "/logadmin reset <玩家> [密码] - 重置玩家密码");
        PREVIOUS_ZH.put("help_admin_forcelogin", "/logadmin forcelogin <玩家> - 强制登录玩家");
        PREVIOUS_ZH.put("help_admin_unregister", "/logadmin unregister <玩家> - 删除玩家账号");
        PREVIOUS_ZH.put("help_admin_info", "/logadmin info <玩家> - 查看玩家账号信息");
    }

    private final Language previous = new Language(PREVIOUS_ZH);

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
    }

    @Test
    @DisplayName("/logadmin help shows the earlier file's words with the command in yellow and its description in white")
    void adminHelp() {
        LoginService service = mock(LoginService.class);
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(inv -> previous.getLocalizedText(inv.getArgument(0)));
        speakPrevious(service);
        CommandSender sender = mock(CommandSender.class);

        new LoginAdminCommand(service).help(sender);

        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(lines.capture());
        assertThat(lines.getAllValues()).containsExactly(
                ChatColor.GOLD + "===== UltiLogin 管理命令 =====",
                ChatColor.YELLOW + "/logadmin reset <玩家> [密码]" + ChatColor.WHITE + " - 重置玩家密码",
                ChatColor.YELLOW + "/logadmin forcelogin <玩家>" + ChatColor.WHITE + " - 强制登录玩家",
                ChatColor.YELLOW + "/logadmin unregister <玩家>" + ChatColor.WHITE + " - 删除玩家账号",
                ChatColor.YELLOW + "/logadmin info <玩家>" + ChatColor.WHITE + " - 查看玩家账号信息");
    }

    /** The service's {@code i18n}, answering from the earlier file, when the service has one. */
    private void speakPrevious(LoginService service) {
        try {
            java.lang.reflect.Method i18n = LoginService.class.getMethod("i18n", String.class);
            org.mockito.Mockito.lenient().when(i18n.invoke(service, anyString()))
                    .thenAnswer(inv -> previous.getLocalizedText(inv.getArgument(0)));
        } catch (NoSuchMethodException e) {
            // before the language sweep the service had no pass-through; the command's text was fixed
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
