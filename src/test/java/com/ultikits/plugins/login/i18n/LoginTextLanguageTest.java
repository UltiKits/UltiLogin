package com.ultikits.plugins.login.i18n;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.commands.ChangePasswordCommand;
import com.ultikits.plugins.login.commands.EmailBindCommand;
import com.ultikits.plugins.login.commands.LoginAdminCommand;
import com.ultikits.plugins.login.commands.LoginCommand;
import com.ultikits.plugins.login.commands.PanelCommand;
import com.ultikits.plugins.login.commands.RecoverCommand;
import com.ultikits.plugins.login.commands.RegisterCommand;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Text this module shows to players and writes to the console follows the framework's
 * {@code language} setting (UltiKits/UltiLogin#20).
 * <p>
 * The module's {@code i18n} answers from the catalogue this module really ships ({@link CatalogueText}).
 * Before the language sweep the help lines, the command descriptions, the administrator's replies and
 * several chat lines were fixed Chinese text and the console lines were fixed English text, while the
 * catalogue already held English and Chinese text for most of them that no code read.
 * <p>
 * Chat lines are compared with their colour codes stripped: which colour a line carries is not what
 * this test pins, the words are.
 */
@DisplayName("UltiLogin text follows the language setting (UltiKits/UltiLogin#20)")
class LoginTextLanguageTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    /** The catalogue text for {@code key}, placeholders filled, colours stripped; or a marker naming the missing key. */
    private static String words(String code, String key, String... tokenValuePairs) {
        String value = CatalogueText.entries(code).get(key);
        if (value == null) {
            return "<lang/" + code + " has no " + key + ">";
        }
        for (int i = 0; i + 1 < tokenValuePairs.length; i += 2) {
            value = value.replace(tokenValuePairs[i], tokenValuePairs[i + 1]);
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
    }

    /** The console text for {@code key}, placeholders filled; or a marker naming the missing key. */
    private static String console(String code, String key, String... tokenValuePairs) {
        String value = CatalogueText.entries(code).get(key);
        if (value == null) {
            return "<lang/" + code + " has no " + key + ">";
        }
        for (int i = 0; i + 1 < tokenValuePairs.length; i += 2) {
            value = value.replace(tokenValuePairs[i], tokenValuePairs[i + 1]);
        }
        return value;
    }

    /** Every line {@code sender} was sent, colours stripped; none of them may hold Chinese text. */
    private static List<String> englishLinesSentTo(CommandSender sender) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        List<String> lines = new ArrayList<>();
        for (String line : captor.getAllValues()) {
            lines.add(ChatColor.stripColor(line));
        }
        assertThat(lines).noneMatch(l -> CJK.matcher(l).find());
        return lines;
    }

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
    }

    @Nested
    @DisplayName("command descriptions")
    class Descriptions {

        @Test
        @DisplayName("every command's description is a key with English text")
        void descriptions() {
            Map<Class<?>, String> expected = new LinkedHashMap<>();
            expected.put(LoginCommand.class, "Log in to your account");
            expected.put(RegisterCommand.class, "Register an account");
            expected.put(ChangePasswordCommand.class, "Change your password");
            expected.put(LoginAdminCommand.class, "Login system administration");
            expected.put(EmailBindCommand.class, "Bind an email address");
            expected.put(RecoverCommand.class, "Recover your password");
            expected.put(PanelCommand.class, "Open the UltiCloud web panel");

            for (Map.Entry<Class<?>, String> e : expected.entrySet()) {
                String key = e.getKey().getAnnotation(CmdExecutor.class).description();
                assertThat(CatalogueText.entries("en").get(key))
                        .as(e.getKey().getSimpleName() + "'s description " + key)
                        .isEqualTo(e.getValue());
            }
        }
    }

    @Nested
    @DisplayName("player commands under language: en")
    class PlayerCommandsInEnglish {

        private LoginService service;
        private LoginConfig config;
        private Player player;

        @BeforeEach
        void build() {
            service = mock(LoginService.class);
            LoginSeams.speak(service, "en");
            config = UltiLoginTestHelper.createDefaultConfig();
            when(service.getConfig()).thenReturn(config);
            player = UltiLoginTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        }

        @Test
        @DisplayName("/login with no argument shows the English usage")
        void loginHelp() {
            new LoginCommand(service).help(player);

            assertThat(englishLinesSentTo(player)).containsExactly(words("en", "help_login"));
        }

        @Test
        @DisplayName("/register help names the length rule of command mode in English")
        void registerHelpCommandMode() {
            new RegisterCommand(service).help(player);

            assertThat(englishLinesSentTo(player)).containsExactly(
                    words("en", "help_register"),
                    words("en", "help_password_length", "{MIN}", "6", "{MAX}", "32"));
        }

        @Test
        @DisplayName("/register help names the digit rule of GUI mode in English")
        void registerHelpGuiMode() {
            when(config.isGuiModeEnabled()).thenReturn(true);

            new RegisterCommand(service).help(player);

            assertThat(englishLinesSentTo(player)).containsExactly(
                    words("en", "help_register"),
                    words("en", "help_password_digits", "{LENGTH}", "4"));
        }

        @Test
        @DisplayName("/changepassword help is English in both modes")
        void changePasswordHelp() {
            new ChangePasswordCommand(service).help(player);
            when(config.isGuiModeEnabled()).thenReturn(true);
            new ChangePasswordCommand(service).help(player);

            assertThat(englishLinesSentTo(player)).containsExactly(
                    words("en", "help_change_password"),
                    words("en", "help_password_length", "{MIN}", "6", "{MAX}", "32"),
                    words("en", "help_change_password"),
                    words("en", "help_password_digits", "{LENGTH}", "4"));
        }

        @Test
        @DisplayName("/changepassword replies in English: not logged in, mismatch, success, wrong old password")
        void changePasswordReplies() {
            ChangePasswordCommand command = new ChangePasswordCommand(service);
            UUID uuid = player.getUniqueId();

            when(service.isLoggedIn(uuid)).thenReturn(false);
            command.changePassword(player, "old", "newPass1", "newPass1");

            when(service.isLoggedIn(uuid)).thenReturn(true);
            when(service.isPasswordValid(anyString())).thenReturn(true);
            command.changePassword(player, "old", "newPass1", "different");

            when(service.changePassword(uuid, "old", "newPass1")).thenReturn(true);
            command.changePassword(player, "old", "newPass1", "newPass1");

            when(service.changePassword(uuid, "old", "newPass1")).thenReturn(false);
            command.changePassword(player, "old", "newPass1", "newPass1");

            assertThat(englishLinesSentTo(player)).containsExactly(
                    words("en", "please_login_first"),
                    words("en", "change_password_mismatch"),
                    words("en", "change_password_success"),
                    words("en", "change_password_wrong"));
        }

        @Test
        @DisplayName("/panel help is English")
        void panelHelp() {
            UltiLogin plugin = UltiLoginTestHelper.getMockPlugin();
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, org.mockito.Answers.RETURNS_MOCKS)) {
                PanelCommand command = new PanelCommand(plugin, service);
                PanelSeam.help(command, player);
            }

            assertThat(englishLinesSentTo(player)).containsExactly(words("en", "help_panel"));
        }
    }

    @Nested
    @DisplayName("/logadmin under language: en")
    class AdminInEnglish {

        private LoginService service;
        private CommandSender sender;
        private LoginAdminCommand command;

        @BeforeEach
        void build() {
            service = mock(LoginService.class);
            LoginSeams.speak(service, "en");
            LoginConfig config = UltiLoginTestHelper.createDefaultConfig();
            when(service.getConfig()).thenReturn(config);
            sender = mock(CommandSender.class);
            command = new LoginAdminCommand(service);
        }

        private AccountData account(UUID uuid) {
            AccountData account = UltiLoginTestHelper.createSampleAccount(uuid, "Steve", "hash", "salt");
            account.setRegisterIp("10.0.0.1");
            account.setLastIp("10.0.0.2");
            account.setLoginCount(7);
            account.setEmail(null);
            account.setEmailVerified(false);
            return account;
        }

        @Test
        @DisplayName("help lists every subcommand in English")
        void help() {
            command.help(sender);

            assertThat(englishLinesSentTo(sender)).containsExactly(
                    words("en", "help_admin_header"),
                    words("en", "help_admin_reset"),
                    words("en", "help_admin_forcelogin"),
                    words("en", "help_admin_unregister"),
                    words("en", "help_admin_info"));
        }

        @Test
        @DisplayName("both reset forms report a failed reset in English")
        void resetFailures() {
            UUID uuid = UUID.randomUUID();
            when(service.getAccountByName("Steve")).thenReturn(account(uuid));
            when(service.resetPassword(uuid)).thenReturn(null);
            when(service.isPasswordValid("newPass1")).thenReturn(true);
            when(service.resetPassword(uuid, "newPass1")).thenReturn(false);

            command.resetPassword(sender, "Steve");
            command.resetPasswordWithValue(sender, "Steve", "newPass1");

            assertThat(englishLinesSentTo(sender)).containsExactly(
                    words("en", "admin_reset_failed"),
                    words("en", "admin_reset_failed"));
        }

        @Test
        @DisplayName("unregister reports a failure in English")
        void unregisterFailure() {
            UUID uuid = UUID.randomUUID();
            when(service.getAccountByName("Steve")).thenReturn(account(uuid));
            when(service.unregister(uuid)).thenReturn(false);

            command.unregister(sender, "Steve");

            assertThat(englishLinesSentTo(sender)).containsExactly(words("en", "admin_unregister_failed"));
        }

        @Test
        @DisplayName("force-login's already-logged-in, notification and failure lines are English")
        void forceLogin() {
            Player target = UltiLoginTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(target.isOnline()).thenReturn(true);
            UUID uuid = target.getUniqueId();
            when(service.isRegistered(uuid)).thenReturn(true);
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Steve")).thenReturn(target);

                when(service.isLoggedIn(uuid)).thenReturn(true);
                command.forceLogin(sender, "Steve");

                when(service.isLoggedIn(uuid)).thenReturn(false);
                when(service.forceLogin(target)).thenReturn(false);
                command.forceLogin(sender, "Steve");

                // The success reply to the administrator is a configured message (its own test);
                // this call is here for the line the target player reads.
                when(service.forceLogin(target)).thenReturn(true);
                command.forceLogin(mock(CommandSender.class), "Steve");
            }

            List<String> toAdmin = englishLinesSentTo(sender);
            assertThat(toAdmin.get(0)).isEqualTo(words("en", "admin_player_already_logged", "{PLAYER}", "Steve"));
            assertThat(toAdmin.get(1)).isEqualTo(words("en", "admin_force_login_failed"));
            assertThat(englishLinesSentTo(target)).containsExactly(words("en", "admin_force_login_notify"));
        }

        @Test
        @DisplayName("info labels every field in English")
        void info() {
            UUID uuid = UUID.randomUUID();
            AccountData account = account(uuid);
            when(service.getAccountByName("Steve")).thenReturn(account);

            command.showInfo(sender, "Steve");

            List<String> lines = englishLinesSentTo(sender);
            assertThat(lines).hasSize(10);
            assertThat(lines.subList(0, 8)).containsExactly(
                    words("en", "info_header"),
                    words("en", "info_player_name") + "Steve",
                    words("en", "info_uuid") + uuid,
                    words("en", "info_register_ip") + "10.0.0.1",
                    words("en", "info_last_ip") + "10.0.0.2",
                    words("en", "info_login_count") + "7",
                    words("en", "info_email") + words("en", "info_email_not_bound"),
                    words("en", "info_email_verified") + words("en", "info_not_verified"));
            assertThat(lines.get(8)).startsWith(words("en", "info_register_time"));
            assertThat(lines.get(9)).startsWith(words("en", "info_last_login"));
        }
    }

    @Nested
    @DisplayName("console lines")
    class Console {

        private UltiLogin pluginSpeaking(String code) {
            UltiLogin plugin = mock(UltiLogin.class);
            PluginLogger logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(code));
            return plugin;
        }

        @Test
        @DisplayName("the enable line is English under language: en")
        void enableLine() {
            UltiLogin plugin = pluginSpeaking("en");
            when(plugin.registerSelf()).thenCallRealMethod();

            plugin.registerSelf();

            verify(plugin.getLogger()).info(console("en", "login_enabled"));
        }

        @Test
        @DisplayName("the removed-key warning is Chinese under language: zh")
        void removedKeyWarning(@org.junit.jupiter.api.io.TempDir java.io.File dir) throws IOException {
            java.io.File file = new java.io.File(dir, "login.yml");
            java.nio.file.Files.write(file.toPath(),
                    "messages:\n  wrong-password: 'x'\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            List<String> warnings = new ArrayList<>();

            LoginSeams.warnAboutLeftovers(file, warnings::add, pluginSpeaking("zh"));

            assertThat(warnings).containsExactly(console("zh", "removed_key_warning",
                    "{FILE}", file.getPath(),
                    "{KEY}", "messages.wrong-password",
                    "{REASON}", console("zh", "removed_key_reason_wrong_password")));
        }
    }

    /** {@code PanelCommand#handleHelp} is protected; reached reflectively from this package. */
    private static final class PanelSeam {
        static void help(PanelCommand command, CommandSender sender) {
            try {
                java.lang.reflect.Method m = PanelCommand.class.getDeclaredMethod("handleHelp", CommandSender.class);
                m.setAccessible(true);
                m.invoke(command, sender);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
