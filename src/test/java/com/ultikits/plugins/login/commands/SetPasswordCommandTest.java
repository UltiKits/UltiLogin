package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.i18n.LoginSeams;
import com.ultikits.plugins.login.service.LoginService;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /setpassword <new> <confirm>}: a player whose account was created on the web (empty game
 * password) sets their first game password while logged in (Phase 18 magic-link contract, section 13).
 */
@DisplayName("SetPasswordCommand")
class SetPasswordCommandTest {

    private LoginService loginService;
    private SetPasswordCommand command;
    private Player player;
    private UUID playerUuid;
    private LoginConfig config;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
        loginService = mock(LoginService.class);
        LoginSeams.speak(loginService, "zh");
        command = new SetPasswordCommand(loginService);

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);

        config = UltiLoginTestHelper.createDefaultConfig();
        when(loginService.getConfig()).thenReturn(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
    }

    private static String text(String key) {
        // Read before any verify(...) starts, and without throwing, so a missing key shows up as a
        // plain mismatch rather than as an unfinished verification.
        return CatalogueText.entries("zh").getOrDefault(key, key).replace("&c", "").replace("&a", "").replace("&e", "");
    }

    @Nested
    @DisplayName("Test 9: /setpassword <new> <confirm>")
    class SetPassword {

        @Test
        @DisplayName("logged in with an empty hash: the password is set and the player is told")
        void setsPassword() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            when(loginService.hasNoGamePassword(playerUuid)).thenReturn(true);
            when(loginService.isPasswordValid("secret123")).thenReturn(true);
            when(loginService.setInitialPassword(playerUuid, "secret123")).thenReturn(true);

            command.setPassword(player, "secret123", "secret123");

            verify(loginService).setInitialPassword(playerUuid, "secret123");
            String expected = text("setpassword_success");
            verify(player).sendMessage(contains(expected));
        }

        @Test
        @DisplayName("an account that already has a game password is refused and pointed at /changepassword")
        void refusesExistingPassword() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            when(loginService.hasNoGamePassword(playerUuid)).thenReturn(false);
            when(loginService.isPasswordValid(anyString())).thenReturn(true);

            command.setPassword(player, "secret123", "secret123");

            verify(loginService, never()).setInitialPassword(any(), anyString());
            verify(player).sendMessage(contains("/changepassword"));
        }

        @Test
        @DisplayName("not logged in: refused")
        void refusesNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.hasNoGamePassword(playerUuid)).thenReturn(true);
            when(loginService.isPasswordValid(anyString())).thenReturn(true);

            command.setPassword(player, "secret123", "secret123");

            verify(loginService, never()).setInitialPassword(any(), anyString());
            String expected = text("please_login_first");
            verify(player).sendMessage(contains(expected));
        }

        @Test
        @DisplayName("mismatched confirmation: refused")
        void refusesMismatch() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            when(loginService.hasNoGamePassword(playerUuid)).thenReturn(true);
            when(loginService.isPasswordValid(anyString())).thenReturn(true);

            command.setPassword(player, "secret123", "secret124");

            verify(loginService, never()).setInitialPassword(any(), anyString());
            String expected = text("change_password_mismatch");
            verify(player).sendMessage(contains(expected));
        }

        @Test
        @DisplayName("the /register password policy applies")
        void appliesPasswordPolicy() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            when(loginService.hasNoGamePassword(playerUuid)).thenReturn(true);
            when(loginService.isPasswordValid("123")).thenReturn(false);
            when(loginService.getPasswordValidationError("123")).thenReturn("密码太短");

            command.setPassword(player, "123", "123");

            verify(loginService, never()).setInitialPassword(any(), anyString());
            verify(player).sendMessage(contains("密码太短"));
        }

        @Test
        @DisplayName("a failed write is reported, not shown as success")
        void reportsFailedWrite() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            when(loginService.hasNoGamePassword(playerUuid)).thenReturn(true);
            when(loginService.isPasswordValid(anyString())).thenReturn(true);
            when(loginService.setInitialPassword(playerUuid, "secret123")).thenReturn(false);

            command.setPassword(player, "secret123", "secret123");

            String failed = text("setpassword_failed");
            String success = text("setpassword_success");
            verify(player).sendMessage(contains(failed));
            verify(player, never()).sendMessage(contains(success));
        }

        @Test
        @DisplayName("bare /setpassword shows the usage")
        void help() {
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(config.getMinPasswordLength()).thenReturn(6);
            when(config.getMaxPasswordLength()).thenReturn(32);

            command.help(player);

            verify(player).sendMessage(contains("/setpassword"));
            verify(player).sendMessage(contains("6-32"));
        }
    }

    @Test
    @DisplayName("setpassword is not usable before login: the default allowed-commands list does not contain it")
    void notInAllowedCommands() {
        assertThat(new LoginConfig().getAllowedCommands())
                .as("control: the list is the shipped default")
                .contains("login", "panel")
                .doesNotContain("setpassword", "setpw");
    }
}
