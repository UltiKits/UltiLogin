package com.ultikits.plugins.login.config;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LoginConfig Tests")
class LoginConfigTest {

    /**
     * UltiKits/UltiLogin#23: {@code messages.wrong-password} was declared, validated and written
     * into every operator's {@code login.yml}, and never read. Its text now comes from the language
     * catalogue ({@code wrong_password}), so the key is removed rather than wired -- the maintainer's
     * message-text decision of 2026-09-22. Asserted over the declared surface, the only place the
     * framework learns which keys to write: a field carrying this path would put the key back into
     * every fresh {@code login.yml}. The sibling {@code messages.account-locked} is the positive
     * control, proving the scan reads the annotations at all.
     */
    @Test
    @DisplayName("declares no messages.wrong-password key, while still declaring its sibling messages.account-locked")
    void declaresNoWrongPasswordKey() {
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (java.lang.reflect.Field field : LoginConfig.class.getDeclaredFields()) {
            com.ultikits.ultitools.annotations.ConfigEntry entry =
                    field.getAnnotation(com.ultikits.ultitools.annotations.ConfigEntry.class);
            if (entry != null) {
                paths.add(entry.path());
            }
        }

        assertThat(paths).contains("messages.account-locked");
        assertThat(paths).doesNotContain("messages.wrong-password");
    }

    @Nested
    @DisplayName("Default Values")
    class DefaultValues {

        @Test
        @DisplayName("Should have 60 second default login timeout")
        void loginTimeout() {
            LoginConfig config = createRealConfig();
            assertThat(config.getLoginTimeout()).isEqualTo(60);
        }

        @Test
        @DisplayName("Should have session enabled by default")
        void sessionEnabled() {
            LoginConfig config = createRealConfig();
            assertThat(config.isSessionEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should have 30 minute session timeout")
        void sessionTimeout() {
            LoginConfig config = createRealConfig();
            assertThat(config.getSessionTimeout()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should have max 3 registrations per IP")
        void maxRegisterPerIp() {
            LoginConfig config = createRealConfig();
            assertThat(config.getMaxRegisterPerIp()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should have GUI mode disabled by default")
        void guiModeEnabled() {
            LoginConfig config = createRealConfig();
            assertThat(config.isGuiModeEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should have GUI password length 4")
        void guiPasswordLength() {
            LoginConfig config = createRealConfig();
            assertThat(config.getGuiPasswordLength()).isEqualTo(4);
        }

        @Test
        @DisplayName("Should have min password length 6")
        void minPasswordLength() {
            LoginConfig config = createRealConfig();
            assertThat(config.getMinPasswordLength()).isEqualTo(6);
        }

        @Test
        @DisplayName("Should have max password length 32")
        void maxPasswordLength() {
            LoginConfig config = createRealConfig();
            assertThat(config.getMaxPasswordLength()).isEqualTo(32);
        }

        @Test
        @DisplayName("Should have max 5 login attempts")
        void maxLoginAttempts() {
            LoginConfig config = createRealConfig();
            assertThat(config.getMaxLoginAttempts()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should have 900 second lockout duration")
        void lockoutDuration() {
            LoginConfig config = createRealConfig();
            assertThat(config.getLockoutDuration()).isEqualTo(900);
        }

        @Test
        @DisplayName("Should have IP lockout type")
        void lockoutType() {
            LoginConfig config = createRealConfig();
            assertThat(config.getLockoutType()).isEqualTo("IP");
        }

        @Test
        @DisplayName("Should have spawn location disabled by default")
        void spawnLocationEnabled() {
            LoginConfig config = createRealConfig();
            assertThat(config.isSpawnLocationEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should have blind effect enabled by default")
        void blindEffect() {
            LoginConfig config = createRealConfig();
            assertThat(config.isBlindEffect()).isTrue();
        }
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        @DisplayName("Should update login timeout")
        void setLoginTimeout() {
            LoginConfig config = createRealConfig();
            config.setLoginTimeout(120);
            assertThat(config.getLoginTimeout()).isEqualTo(120);
        }

        @Test
        @DisplayName("Should update session enabled")
        void setSessionEnabled() {
            LoginConfig config = createRealConfig();
            config.setSessionEnabled(false);
            assertThat(config.isSessionEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should update GUI mode enabled")
        void setGuiModeEnabled() {
            LoginConfig config = createRealConfig();
            config.setGuiModeEnabled(true);
            assertThat(config.isGuiModeEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should update max login attempts")
        void setMaxLoginAttempts() {
            LoginConfig config = createRealConfig();
            config.setMaxLoginAttempts(10);
            assertThat(config.getMaxLoginAttempts()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should update lockout type")
        void setLockoutType() {
            LoginConfig config = createRealConfig();
            config.setLockoutType("BOTH");
            assertThat(config.getLockoutType()).isEqualTo("BOTH");
        }

        @Test
        @DisplayName("Should update blind effect")
        void setBlindEffect() {
            LoginConfig config = createRealConfig();
            config.setBlindEffect(false);
            assertThat(config.isBlindEffect()).isFalse();
        }
    }

    @Nested
    @DisplayName("Effective Password Length")
    class EffectivePasswordLength {

        @Test
        @DisplayName("Should return GUI password length when GUI mode enabled")
        void guiMode() {
            LoginConfig config = createRealConfig();
            config.setGuiModeEnabled(true);
            config.setGuiPasswordLength(6);

            assertThat(config.getEffectiveMinPasswordLength()).isEqualTo(6);
            assertThat(config.getEffectiveMaxPasswordLength()).isEqualTo(6);
        }

        @Test
        @DisplayName("Should return command mode lengths when GUI mode disabled")
        void commandMode() {
            LoginConfig config = createRealConfig();
            config.setGuiModeEnabled(false);
            config.setMinPasswordLength(8);
            config.setMaxPasswordLength(20);

            assertThat(config.getEffectiveMinPasswordLength()).isEqualTo(8);
            assertThat(config.getEffectiveMaxPasswordLength()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Message Defaults")
    class MessageDefaults {

        @Test
        @DisplayName("every GUI title and message defaults to blank, so the language file supplies it (UltiKits/UltiLogin#20)")
        void textSettingsDefaultBlank() throws Exception {
            LoginConfig config = new LoginConfig();
            String[] fields = {"guiLoginTitle", "guiRegisterTitle", "guiConfirmTitle",
                    "registerPrompt", "registerPromptGui", "loginPrompt", "loginPromptGui",
                    "registerSuccess", "loginSuccess", "alreadyLogged", "notRegistered",
                    "alreadyRegistered", "passwordMismatch", "passwordTooShort", "passwordTooLong",
                    "timeoutKick", "accountLocked", "attemptsRemaining", "guiPasswordInvalid",
                    "adminPasswordReset", "adminForceLogin", "adminUnregister",
                    "adminPlayerNotFound", "adminAccountNotFound"};
            for (String name : fields) {
                java.lang.reflect.Field f = LoginConfig.class.getDeclaredField(name);
                f.setAccessible(true);
                assertThat(f.get(config)).as(name).isEqualTo("");
                assertThat(f.isAnnotationPresent(com.ultikits.ultitools.annotations.config.NotEmpty.class))
                        .as(name + " must accept a blank value").isFalse();
            }
        }

        @Test
        @DisplayName("Should have default register prompt")
        void registerPrompt() {
            LoginConfig config = createRealConfig();
            assertThat(config.getRegisterPrompt()).contains("/register");
        }

        @Test
        @DisplayName("Should have default register prompt GUI")
        void registerPromptGui() {
            LoginConfig config = createRealConfig();
            assertThat(config.getRegisterPromptGui()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default login prompt")
        void loginPrompt() {
            LoginConfig config = createRealConfig();
            assertThat(config.getLoginPrompt()).contains("/login");
        }

        @Test
        @DisplayName("Should have default login prompt GUI")
        void loginPromptGui() {
            LoginConfig config = createRealConfig();
            assertThat(config.getLoginPromptGui()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default register success message")
        void registerSuccess() {
            LoginConfig config = createRealConfig();
            assertThat(config.getRegisterSuccess()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default login success message")
        void loginSuccess() {
            LoginConfig config = createRealConfig();
            assertThat(config.getLoginSuccess()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default already logged message")
        void alreadyLogged() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAlreadyLogged()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default not registered message")
        void notRegistered() {
            LoginConfig config = createRealConfig();
            assertThat(config.getNotRegistered()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default already registered message")
        void alreadyRegistered() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAlreadyRegistered()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default password mismatch message")
        void passwordMismatch() {
            LoginConfig config = createRealConfig();
            assertThat(config.getPasswordMismatch()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default password too short message")
        void passwordTooShort() {
            LoginConfig config = createRealConfig();
            assertThat(config.getPasswordTooShort()).contains("{MIN}");
        }

        @Test
        @DisplayName("Should have default password too long message")
        void passwordTooLong() {
            LoginConfig config = createRealConfig();
            assertThat(config.getPasswordTooLong()).contains("{MAX}");
        }

        @Test
        @DisplayName("Should have default timeout kick message")
        void timeoutKick() {
            LoginConfig config = createRealConfig();
            assertThat(config.getTimeoutKick()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default account locked message")
        void accountLocked() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAccountLocked()).contains("{TIME}");
        }

        @Test
        @DisplayName("Should have default attempts remaining message")
        void attemptsRemaining() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAttemptsRemaining()).contains("{COUNT}");
        }

        @Test
        @DisplayName("Should have default GUI password invalid message")
        void guiPasswordInvalid() {
            LoginConfig config = createRealConfig();
            assertThat(config.getGuiPasswordInvalid()).contains("{LENGTH}");
        }
    }

    @Nested
    @DisplayName("Admin Message Defaults")
    class AdminMessageDefaults {

        @Test
        @DisplayName("Should have default admin password reset message")
        void adminPasswordReset() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAdminPasswordReset()).contains("{PLAYER}");
            assertThat(config.getAdminPasswordReset()).contains("{PASSWORD}");
        }

        @Test
        @DisplayName("Should have default admin force login message")
        void adminForceLogin() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAdminForceLogin()).contains("{PLAYER}");
        }

        @Test
        @DisplayName("Should have default admin unregister message")
        void adminUnregister() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAdminUnregister()).contains("{PLAYER}");
        }

        @Test
        @DisplayName("Should have default admin player not found message")
        void adminPlayerNotFound() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAdminPlayerNotFound()).contains("{PLAYER}");
        }

        @Test
        @DisplayName("Should have default admin account not found message")
        void adminAccountNotFound() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAdminAccountNotFound()).contains("{PLAYER}");
        }
    }

    @Nested
    @DisplayName("GUI Title Defaults")
    class GuiTitleDefaults {

        @Test
        @DisplayName("Should have default GUI login title")
        void guiLoginTitle() {
            LoginConfig config = createRealConfig();
            assertThat(config.getGuiLoginTitle()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default GUI register title")
        void guiRegisterTitle() {
            LoginConfig config = createRealConfig();
            assertThat(config.getGuiRegisterTitle()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have default GUI confirm title")
        void guiConfirmTitle() {
            LoginConfig config = createRealConfig();
            assertThat(config.getGuiConfirmTitle()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Spawn Location Defaults")
    class SpawnLocationDefaults {

        @Test
        @DisplayName("Should have default spawn world")
        void spawnWorld() {
            LoginConfig config = createRealConfig();
            assertThat(config.getSpawnWorld()).isEqualTo("world");
        }

        @Test
        @DisplayName("Should have default spawn X")
        void spawnX() {
            LoginConfig config = createRealConfig();
            assertThat(config.getSpawnX()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should have default spawn Y")
        void spawnY() {
            LoginConfig config = createRealConfig();
            assertThat(config.getSpawnY()).isEqualTo(64.0);
        }

        @Test
        @DisplayName("Should have default spawn Z")
        void spawnZ() {
            LoginConfig config = createRealConfig();
            assertThat(config.getSpawnZ()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Allowed Commands Default")
    class AllowedCommandsDefault {

        @Test
        @DisplayName("Should have default allowed commands list")
        void allowedCommands() {
            LoginConfig config = createRealConfig();
            assertThat(config.getAllowedCommands())
                    .containsExactly("login", "l", "register", "reg", "panel", "regs", "recover");
        }
    }

    @Nested
    @DisplayName("Additional Setters")
    class AdditionalSetters {

        @Test
        @DisplayName("Should update session timeout")
        void setSessionTimeout() {
            LoginConfig config = createRealConfig();
            config.setSessionTimeout(60);
            assertThat(config.getSessionTimeout()).isEqualTo(60);
        }

        @Test
        @DisplayName("Should update max register per IP")
        void setMaxRegisterPerIp() {
            LoginConfig config = createRealConfig();
            config.setMaxRegisterPerIp(5);
            assertThat(config.getMaxRegisterPerIp()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should update GUI password length")
        void setGuiPasswordLength() {
            LoginConfig config = createRealConfig();
            config.setGuiPasswordLength(6);
            assertThat(config.getGuiPasswordLength()).isEqualTo(6);
        }

        @Test
        @DisplayName("Should update min password length")
        void setMinPasswordLength() {
            LoginConfig config = createRealConfig();
            config.setMinPasswordLength(8);
            assertThat(config.getMinPasswordLength()).isEqualTo(8);
        }

        @Test
        @DisplayName("Should update max password length")
        void setMaxPasswordLength() {
            LoginConfig config = createRealConfig();
            config.setMaxPasswordLength(64);
            assertThat(config.getMaxPasswordLength()).isEqualTo(64);
        }

        @Test
        @DisplayName("Should update lockout duration")
        void setLockoutDuration() {
            LoginConfig config = createRealConfig();
            config.setLockoutDuration(1800);
            assertThat(config.getLockoutDuration()).isEqualTo(1800);
        }

        @Test
        @DisplayName("Should update spawn location enabled")
        void setSpawnLocationEnabled() {
            LoginConfig config = createRealConfig();
            config.setSpawnLocationEnabled(true);
            assertThat(config.isSpawnLocationEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should update spawn world")
        void setSpawnWorld() {
            LoginConfig config = createRealConfig();
            config.setSpawnWorld("world_nether");
            assertThat(config.getSpawnWorld()).isEqualTo("world_nether");
        }

        @Test
        @DisplayName("Should update spawn coordinates")
        void setSpawnCoordinates() {
            LoginConfig config = createRealConfig();
            config.setSpawnX(100.5);
            config.setSpawnY(72.0);
            config.setSpawnZ(-200.3);
            assertThat(config.getSpawnX()).isEqualTo(100.5);
            assertThat(config.getSpawnY()).isEqualTo(72.0);
            assertThat(config.getSpawnZ()).isEqualTo(-200.3);
        }

        @Test
        @DisplayName("Should update allowed commands")
        void setAllowedCommands() {
            LoginConfig config = createRealConfig();
            config.setAllowedCommands(java.util.Arrays.asList("login", "help"));
            assertThat(config.getAllowedCommands()).containsExactly("login", "help");
        }

        @Test
        @DisplayName("Should update message strings")
        void setMessages() {
            LoginConfig config = createRealConfig();

            config.setRegisterPrompt("custom register");
            assertThat(config.getRegisterPrompt()).isEqualTo("custom register");

            config.setLoginPrompt("custom login");
            assertThat(config.getLoginPrompt()).isEqualTo("custom login");

            config.setRegisterSuccess("custom success");
            assertThat(config.getRegisterSuccess()).isEqualTo("custom success");

            config.setLoginSuccess("custom login success");
            assertThat(config.getLoginSuccess()).isEqualTo("custom login success");

            config.setAlreadyLogged("custom already");
            assertThat(config.getAlreadyLogged()).isEqualTo("custom already");

            config.setNotRegistered("custom not reg");
            assertThat(config.getNotRegistered()).isEqualTo("custom not reg");

            config.setAlreadyRegistered("custom already reg");
            assertThat(config.getAlreadyRegistered()).isEqualTo("custom already reg");

            config.setPasswordMismatch("custom mismatch");
            assertThat(config.getPasswordMismatch()).isEqualTo("custom mismatch");

            config.setPasswordTooShort("custom short");
            assertThat(config.getPasswordTooShort()).isEqualTo("custom short");

            config.setPasswordTooLong("custom long");
            assertThat(config.getPasswordTooLong()).isEqualTo("custom long");

            config.setTimeoutKick("custom timeout");
            assertThat(config.getTimeoutKick()).isEqualTo("custom timeout");

            config.setAccountLocked("custom locked");
            assertThat(config.getAccountLocked()).isEqualTo("custom locked");

            config.setAttemptsRemaining("custom attempts");
            assertThat(config.getAttemptsRemaining()).isEqualTo("custom attempts");

            config.setGuiPasswordInvalid("custom gui invalid");
            assertThat(config.getGuiPasswordInvalid()).isEqualTo("custom gui invalid");
        }

        @Test
        @DisplayName("Should update admin message strings")
        void setAdminMessages() {
            LoginConfig config = createRealConfig();

            config.setAdminPasswordReset("custom admin reset");
            assertThat(config.getAdminPasswordReset()).isEqualTo("custom admin reset");

            config.setAdminForceLogin("custom admin force");
            assertThat(config.getAdminForceLogin()).isEqualTo("custom admin force");

            config.setAdminUnregister("custom admin unreg");
            assertThat(config.getAdminUnregister()).isEqualTo("custom admin unreg");

            config.setAdminPlayerNotFound("custom not found");
            assertThat(config.getAdminPlayerNotFound()).isEqualTo("custom not found");

            config.setAdminAccountNotFound("custom acc not found");
            assertThat(config.getAdminAccountNotFound()).isEqualTo("custom acc not found");
        }

        @Test
        @DisplayName("Should update GUI title strings")
        void setGuiTitles() {
            LoginConfig config = createRealConfig();

            config.setGuiLoginTitle("Custom Login");
            assertThat(config.getGuiLoginTitle()).isEqualTo("Custom Login");

            config.setGuiRegisterTitle("Custom Register");
            assertThat(config.getGuiRegisterTitle()).isEqualTo("Custom Register");

            config.setGuiConfirmTitle("Custom Confirm");
            assertThat(config.getGuiConfirmTitle()).isEqualTo("Custom Confirm");
        }

        @Test
        @DisplayName("Should update GUI prompt strings")
        void setGuiPrompts() {
            LoginConfig config = createRealConfig();

            config.setRegisterPromptGui("Custom GUI register prompt");
            assertThat(config.getRegisterPromptGui()).isEqualTo("Custom GUI register prompt");

            config.setLoginPromptGui("Custom GUI login prompt");
            assertThat(config.getLoginPromptGui()).isEqualTo("Custom GUI login prompt");
        }
    }

    @Nested
    @DisplayName("UltiCloud Settings")
    class UltiCloudSettings {

        @Test
        @DisplayName("Should have ulticloud disabled by default")
        void ulticloudDisabledByDefault() {
            LoginConfig config = createRealConfig();
            assertThat(config.isUlticloudEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should update ulticloud enabled")
        void setUlticloudEnabled() {
            LoginConfig config = createRealConfig();
            config.setUlticloudEnabled(true);
            assertThat(config.isUlticloudEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should toggle ulticloud enabled back to false")
        void toggleUlticloudEnabled() {
            LoginConfig config = createRealConfig();
            config.setUlticloudEnabled(true);
            assertThat(config.isUlticloudEnabled()).isTrue();
            config.setUlticloudEnabled(false);
            assertThat(config.isUlticloudEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Config File Path")
    class ConfigFilePath {

        @Test
        @DisplayName("Should have correct config file path")
        void configFilePath() {
            LoginConfig config = createRealConfig();
            assertThat(config.getConfigFilePath()).isEqualTo("config/login.yml");
        }
    }

    /**
     * Create a real LoginConfig instance.
     * LoginConfig has a no-arg default constructor (via Lombok @NoArgsConstructor isn't present,
     * but the explicit constructor just calls super("config/login.yml") which only sets the path).
     */
    private LoginConfig createRealConfig() {
        // Bound to a plugin answering from the Chinese language file, as the framework's init()
        // binds it: a blank text setting reads its text from there (UltiKits/UltiLogin#20).
        LoginConfig config = new LoginConfig();
        com.ultikits.ultitools.abstracts.UltiToolsPlugin plugin =
                org.mockito.Mockito.mock(com.ultikits.ultitools.abstracts.UltiToolsPlugin.class);
        org.mockito.Mockito.when(plugin.i18n(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(com.ultikits.plugins.login.i18n.CatalogueText.answer("zh"));
        com.ultikits.plugins.login.i18n.LoginSeams.bind(config, plugin);
        return config;
    }
}
