package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.ConfigManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LoginService Tests")
class LoginServiceTest {

    private LoginService service;
    private LoginConfig config;
    @SuppressWarnings("unchecked")
    private DataOperator<AccountData> dataOperator = mock(DataOperator.class);
    @SuppressWarnings("unchecked")
    private Query<AccountData> mockQuery = mock(Query.class);

    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        // Bootstrap a live test-time server, for the wiring rather than for the registry.
        // ServerMock supplies getPluginManager() and getScheduler(). It does NOT supply
        // getPlugin("UltiTools"), though an earlier revision of this comment said so: a bare
        // ServerMock has no plugins loaded, so that lookup returns null -- measured. LoginService's
        // constructor calls Bukkit.getPluginManager().getPlugin("UltiTools") (LoginService.java:95),
        // so bootstrapLiveServer() loads a plugin under that name itself. Together these replace
        // what this class used to hand-stub inline before the shared bootstrap.
        //
        // It is NOT what makes PotionEffectType resolve, though an earlier revision of this comment
        // said so. Measured: with only mock(Server.class) installed -- or with no server installed
        // at all -- PotionEffectType.BLINDNESS resolves and new PotionEffect(BLINDNESS, 100, 1)
        // constructs; drop mockbukkit-v1.21-4.101.0.jar from the classpath and the same code throws
        // ExceptionInInitializerError. That jar ships
        // META-INF/services/io.papermc.paper.registry.RegistryAccess, so the classpath dependency
        // is what fixes PotionEffectType. See UltiLoginTestHelper#bootstrapLiveServer() for the
        // full constant-resolution vs. item-construction split.
        //
        // Routed through UltiLoginTestHelper.bootstrapLiveServer() (rather than calling
        // MockBukkit.mock() inline here) so this class and UltiLoginRegistrySentinelTest share one
        // bootstrap entry point -- breaking that entry point fails both, not just whichever
        // consumer happens to still call it directly. Reconciliation pattern per
        // 14-LEDGER-UltiTrade.md, the phase's canary module, which hit the identical "test helper
        // already touches Bukkit.server" shape. The live server this returns is already installed
        // as Bukkit.server and wrapped in a Mockito spy, so the existing per-test
        // doReturn(...).when(server).method(...) stubs later in this class keep working unchanged.
        UltiLoginTestHelper.bootstrapLiveServer();

        UltiLoginTestHelper.setUp();

        config = UltiLoginTestHelper.createDefaultConfig();

        // Mock plugin.getDataOperator to return our mock
        when(UltiLoginTestHelper.getMockPlugin().getDataOperator(AccountData.class)).thenReturn(dataOperator);

        // Set up Query DSL mock: dataOperator.query() returns a fluent mock Query
        // that returns itself for all chaining methods
        when(dataOperator.query()).thenReturn(mockQuery);
        when(mockQuery.where(anyString())).thenReturn(mockQuery);
        when(mockQuery.and(anyString())).thenReturn(mockQuery);
        when(mockQuery.eq(any())).thenReturn(mockQuery);
        when(mockQuery.ne(any())).thenReturn(mockQuery);
        when(mockQuery.gt(any())).thenReturn(mockQuery);
        when(mockQuery.lt(any())).thenReturn(mockQuery);
        when(mockQuery.gte(any())).thenReturn(mockQuery);
        when(mockQuery.lte(any())).thenReturn(mockQuery);
        when(mockQuery.like(anyString())).thenReturn(mockQuery);
        when(mockQuery.in(any())).thenReturn(mockQuery);
        when(mockQuery.orderBy(anyString())).thenReturn(mockQuery);
        when(mockQuery.orderByDesc(anyString())).thenReturn(mockQuery);
        when(mockQuery.limit(anyInt())).thenReturn(mockQuery);
        when(mockQuery.offset(anyInt())).thenReturn(mockQuery);
        // Default terminal operations return empty/zero
        when(mockQuery.list()).thenReturn(Collections.emptyList());
        when(mockQuery.first()).thenReturn(null);
        when(mockQuery.exists()).thenReturn(false);
        when(mockQuery.count()).thenReturn(0L);
        when(mockQuery.delete()).thenReturn(0);

        service = new LoginService(UltiLoginTestHelper.getMockPlugin(), config);

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    @Test
    @DisplayName("Constructor resolves the UltiTools plugin its scheduler paths hand to Bukkit")
    void constructorResolvesFrameworkPlugin() throws Exception {
        // startAuthPolling passes this field to the scheduler as the task owner
        // (LoginService.java:855 and :907). Nothing currently enabled in this class reaches that
        // path, so a null here would sit latent -- and MockBukkit's scheduler accepts a null owner
        // silently, so even a test that did reach it would not necessarily fail. Pin the field
        // instead, so the fixture cannot drop the "UltiTools" plugin without something going red.
        Field bukkitPluginField = LoginService.class.getDeclaredField("bukkitPlugin");
        bukkitPluginField.setAccessible(true);

        assertThat(bukkitPluginField.get(service))
                .as("LoginService must resolve the framework plugin the scheduler paths require")
                .isNotNull();
    }

    // ==================== isRegistered ====================

    @Nested
    @DisplayName("isRegistered")
    class IsRegistered {

        @Test
        @DisplayName("Should return true when account exists")
        void accountExists() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            assertThat(service.isRegistered(playerUuid)).isTrue();
        }

        @Test
        @DisplayName("Should return false when no account")
        void noAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            assertThat(service.isRegistered(playerUuid)).isFalse();
        }
    }

    // ==================== isLoggedIn ====================

    @Nested
    @DisplayName("isLoggedIn")
    class IsLoggedIn {

        @Test
        @DisplayName("Should return false by default")
        void defaultFalse() {
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("Should return true after completeLogin")
        void afterCompleteLogin() {
            service.completeLogin(player);
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
        }
    }

    // ==================== isPasswordValid ====================

    @Nested
    @DisplayName("isPasswordValid")
    class IsPasswordValid {

        @Test
        @DisplayName("Should validate command mode password length")
        void commandMode() {
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(config.getMinPasswordLength()).thenReturn(6);
            when(config.getMaxPasswordLength()).thenReturn(32);

            assertThat(service.isPasswordValid("12345")).isFalse();
            assertThat(service.isPasswordValid("123456")).isTrue();
            assertThat(service.isPasswordValid("a".repeat(33))).isFalse();
        }

        @Test
        @DisplayName("Should validate GUI mode password")
        void guiMode() {
            when(config.isGuiModeEnabled()).thenReturn(true);
            when(config.getGuiPasswordLength()).thenReturn(4);

            assertThat(service.isPasswordValid("123")).isFalse();
            assertThat(service.isPasswordValid("1234")).isTrue();
            assertThat(service.isPasswordValid("12345")).isFalse();
            assertThat(service.isPasswordValid("abcd")).isFalse();
        }
    }

    // ==================== register ====================

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("Should return false when already registered")
        void alreadyRegistered() {
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(new AccountData()));

            assertThat(service.register(player, "password123")).isFalse();
        }

        @Test
        @DisplayName("Should create account when not registered")
        void createAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            boolean result = service.register(player, "password123");

            assertThat(result).isTrue();
            verify(dataOperator).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
        }

        @Test
        @DisplayName("Should respect IP registration limit")
        void ipLimit() {
            when(config.getMaxRegisterPerIp()).thenReturn(2);

            // First call: check if player registered (no)
            // Second call: count registrations by IP (2 found)
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList())
                    .thenReturn(Arrays.asList(new AccountData(), new AccountData()));

            boolean result = service.register(player, "password123");

            assertThat(result).isFalse();
            verify(dataOperator, never()).insert(any(AccountData.class));
        }
    }

    // ==================== login ====================

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("Should return NOT_REGISTERED when account doesn't exist")
        void notRegistered() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            LoginService.LoginResult result = service.login(player, "password");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("未注册");
        }

        @Test
        @DisplayName("Should return success when password correct")
        void correctPassword() throws Exception {
            // Create account with known password
            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            LoginService.LoginResult result = service.login(player, password);

            assertThat(result.isSuccess()).isTrue();
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            verify(dataOperator).update(any(AccountData.class));
        }

        @Test
        @DisplayName("Should return failure when password wrong")
        void wrongPassword() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "wrongHash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            LoginService.LoginResult result = service.login(player, "wrongPassword");

            assertThat(result.isSuccess()).isFalse();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("Should track failed attempts")
        void trackFailedAttempts() {
            when(config.getMaxLoginAttempts()).thenReturn(3);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            // First failed attempt
            service.login(player, "wrong1");
            assertThat(service.getRemainingAttempts(player)).isEqualTo(2);

            // Second failed attempt
            service.login(player, "wrong2");
            assertThat(service.getRemainingAttempts(player)).isEqualTo(1);
        }

        @Test
        @DisplayName("Should lock account after max attempts")
        void lockAfterMaxAttempts() {
            when(config.getMaxLoginAttempts()).thenReturn(2);
            when(config.getLockoutDuration()).thenReturn(900);
            when(config.getLockoutType()).thenReturn("IP");

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.login(player, "wrong1");
            service.login(player, "wrong2");

            assertThat(service.isLocked(player)).isTrue();
        }
    }

    // ==================== changePassword ====================

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("Should return false when account doesn't exist")
        void noAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            assertThat(service.changePassword(playerUuid, "old", "new")).isFalse();
        }

        @Test
        @DisplayName("Should return false when old password wrong")
        void wrongOldPassword() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            assertThat(service.changePassword(playerUuid, "wrongOld", "newPass")).isFalse();
        }

        @Test
        @DisplayName("Should update password when old password correct")
        void correctOldPassword() throws Exception {
            String salt = "testSalt";
            String oldPassword = "oldPass123";
            String hash = hashPasswordForTest(oldPassword, salt);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            boolean result = service.changePassword(playerUuid, oldPassword, "newPass123");

            assertThat(result).isTrue();
            verify(dataOperator).update(any(AccountData.class));
        }
    }

    // ==================== resetPassword ====================

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("Should return null when account doesn't exist")
        void noAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            assertThat(service.resetPassword(playerUuid)).isNull();
        }

        @Test
        @DisplayName("Should generate random password")
        void generateRandomPassword() throws Exception {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            String newPassword = service.resetPassword(playerUuid);

            assertThat(newPassword).isNotNull();
            assertThat(newPassword.length()).isGreaterThan(0);
            verify(dataOperator).update(any(AccountData.class));
        }

        @Test
        @DisplayName("Should set specific password")
        void setSpecificPassword() throws Exception {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            boolean result = service.resetPassword(playerUuid, "newPassword123");

            assertThat(result).isTrue();
            verify(dataOperator).update(any(AccountData.class));
        }
    }

    // ==================== unregister ====================

    @Nested
    @DisplayName("unregister")
    class Unregister {

        @Test
        @DisplayName("Should return false when account doesn't exist")
        void noAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            assertThat(service.unregister(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("Should delete account when exists")
        void deleteAccount() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            boolean result = service.unregister(playerUuid);

            assertThat(result).isTrue();
            verify(dataOperator).delById(any());
        }

        @Test
        @DisplayName("Should force logout an online player when unregistering their account, without replaying the join handler")
        void forceLogoutOnlinePlayerOnUnregister() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
                bukkitMock.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(player);

                boolean result = service.unregister(playerUuid);

                assertThat(result).isTrue();
                assertThat(service.isLoggedIn(playerUuid)).isFalse();
                // 13-06/D-08: unregister() no longer replays onPlayerJoin(player) to force a
                // logout -- that replay is the defect (it re-runs the session check, which found
                // the never-cleared session and logged the deleted account straight back in).
                // No message is sent here any more; onPlayerJoin was this test's only observable
                // side effect for "the join flow ran", so its absence is what proves the replay
                // is gone, not merely that a message happens not to fire.
                verify(player, never()).sendMessage(anyString());
                verify(player, never()).getLocation();
            }
        }
    }

    // ==================== forceLogin ====================

    @Nested
    @DisplayName("forceLogin")
    class ForceLogin {

        @Test
        @DisplayName("Should return false when not registered")
        void notRegistered() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            assertThat(service.forceLogin(player)).isFalse();
        }

        @Test
        @DisplayName("Should return false when already logged in")
        void alreadyLoggedIn() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.completeLogin(player);

            assertThat(service.forceLogin(player)).isFalse();
        }

        @Test
        @DisplayName("Should login when registered and not logged in")
        void successfulForceLogin() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            boolean result = service.forceLogin(player);

            assertThat(result).isTrue();
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
        }
    }

    // ==================== session management ====================

    @Nested
    @DisplayName("Session Management")
    class SessionManagement {

        @Test
        @DisplayName("Should create session after login")
        void createSession() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.login(player, password);

            assertThat(service.hasValidSession(player)).isTrue();
        }

        @Test
        @DisplayName("Should not create session when disabled")
        void sessionDisabled() throws Exception {
            when(config.isSessionEnabled()).thenReturn(false);

            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.login(player, password);

            assertThat(service.hasValidSession(player)).isFalse();
        }
    }

    // ==================== session invalidation (13-06 / D-08, D-09) ====================

    @Nested
    @DisplayName("Session Invalidation")
    class SessionInvalidation {

        @Test
        @DisplayName("unregister invalidates the deleted account's session")
        void unregisterInvalidatesSession() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.login(player, password);
            assertThat(service.hasValidSession(player)).isTrue();

            // Player offline -- isolates this test to the invalidation call itself, not the
            // separately-tested forced-logout-while-online branch.
            try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
                bukkitMock.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(null);

                boolean result = service.unregister(playerUuid);

                assertThat(result).isTrue();
            }

            assertThat(service.hasValidSession(player))
                    .as("unregister must end the deleted account's session")
                    .isFalse();
        }

        @Test
        @DisplayName("invalidateSession also ends a session opened from a different address than the caller's own")
        void invalidatesSessionFromAnotherAddress() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            // Log in once from the player's normal (mocked) address.
            service.login(player, password);
            assertThat(service.hasValidSession(player)).isTrue();

            // Seed a second session for the same player from a different address directly into
            // the session map -- simulating exactly the case an administrator ending someone
            // else's session needs: a session the actor is not connected from. This is why
            // invalidation matches by the player-identifier suffix of the key, not by the
            // caller's own current address.
            Field sessionsField = LoginService.class.getDeclaredField("sessions");
            sessionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Long> sessions = (Map<String, Long>) sessionsField.get(service);
            String otherAddressKey = "10.0.0.99:" + playerUuid;
            sessions.put(otherAddressKey, System.currentTimeMillis());
            assertThat(sessions).containsKey(otherAddressKey);

            service.invalidateSession(playerUuid);

            assertThat(sessions)
                    .as("a session opened from a different address must also be removed")
                    .doesNotContainKey(otherAddressKey);
            assertThat(service.hasValidSession(player))
                    .as("the player's own session must also be gone")
                    .isFalse();
        }

        @Test
        @DisplayName("invalidateSession cancels a pending panel magic-link request and its polling task")
        void invalidateSessionCancelsPendingPanelRequest() throws Exception {
            // CR-01 (13-REVIEW-UltiLogin.md): unregister() ends `sessions` entries but, before
            // this fix, left an in-flight /panel magic-link request live. Since invalidateSession
            // is the single entry point every credential-changing path already routes through
            // (13-06/D-08), the cancellation belongs here rather than duplicated at each call
            // site -- covering unregister and both resetPassword overloads and changePassword in
            // one place.
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");
            @SuppressWarnings("unchecked")
            Map<UUID, BukkitTask> pollingTasks =
                    (Map<UUID, BukkitTask>) getFieldValue(service, "pollingTasks");

            String requestId = "pending-panel-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());
            BukkitTask mockTask = mock(BukkitTask.class);
            pollingTasks.put(playerUuid, mockTask);

            service.invalidateSession(playerUuid);

            assertThat(pendingPanelRequests)
                    .as("a pending panel magic-link request for the invalidated player must be cancelled")
                    .doesNotContainKey(requestId);
            assertThat(pendingPanelTimestamps).doesNotContainKey(requestId);
            assertThat(pollingTasks)
                    .as("the polling task backing the cancelled request must be removed")
                    .doesNotContainKey(playerUuid);
            verify(mockTask).cancel();
        }

        @Test
        @DisplayName("Administrator reset (resetPassword(UUID)) invalidates the session")
        void adminResetInvalidatesSession() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.login(player, password);
            assertThat(service.hasValidSession(player)).isTrue();

            String newPassword = service.resetPassword(playerUuid);

            assertThat(newPassword).isNotNull();
            assertThat(service.hasValidSession(player))
                    .as("An administrator password reset must end the player's existing session")
                    .isFalse();
        }

        @Test
        @DisplayName("Self-service reset (resetPassword(UUID, String)) invalidates the session")
        void specificPasswordResetInvalidatesSession() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.login(player, password);
            assertThat(service.hasValidSession(player)).isTrue();

            boolean result = service.resetPassword(playerUuid, "newPassword123");

            assertThat(result).isTrue();
            assertThat(service.hasValidSession(player))
                    .as("Resetting to a specific password must end the player's existing session")
                    .isFalse();
        }

        @Test
        @DisplayName("changePassword invalidates the session on success")
        void changePasswordInvalidatesSession() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "testSalt";
            String oldPassword = "oldPass123";
            String hash = hashPasswordForTest(oldPassword, salt);
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.login(player, oldPassword);
            assertThat(service.hasValidSession(player)).isTrue();

            boolean result = service.changePassword(playerUuid, oldPassword, "newPass456");

            assertThat(result).isTrue();
            assertThat(service.hasValidSession(player))
                    .as("A successful password change must end the player's existing session")
                    .isFalse();
        }

        @Test
        @DisplayName("changePassword does not invalidate the session when the old password is wrong")
        void failedChangePasswordDoesNotInvalidateSession() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "testSalt";
            String oldPassword = "oldPass123";
            String hash = hashPasswordForTest(oldPassword, salt);
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.login(player, oldPassword);
            assertThat(service.hasValidSession(player)).isTrue();

            boolean result = service.changePassword(playerUuid, "wrongOldPassword", "newPass456");

            assertThat(result).isFalse();
            assertThat(service.hasValidSession(player))
                    .as("A rejected password change must not log the player out")
                    .isTrue();
        }

        @Test
        @DisplayName("End to end: after unregister, the session check is false and rejoining does not auto-login the deleted account")
        void endToEndDeletionPreventsAutoLogin() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.login(player, password);
            assertThat(service.hasValidSession(player)).isTrue();
            assertThat(service.isLoggedIn(playerUuid)).isTrue();

            Location mockLocation = mock(Location.class);
            when(mockLocation.clone()).thenReturn(mockLocation);
            when(player.getLocation()).thenReturn(mockLocation);

            try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
                bukkitMock.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(player);

                boolean result = service.unregister(playerUuid);

                assertThat(result).isTrue();
            }

            assertThat(service.hasValidSession(player))
                    .as("the session check must return false after account deletion")
                    .isFalse();
            assertThat(service.isLoggedIn(playerUuid))
                    .as("the deleted account is no longer logged in")
                    .isFalse();

            // Simulate the player rejoining. With the session gone, onPlayerJoin's own session
            // check must not auto-login them -- exactly the outcome the removed onPlayerJoin()
            // replay used to defeat by finding a session that had never been cleared.
            when(mockQuery.list()).thenReturn(Collections.emptyList());
            service.onPlayerJoin(player);

            assertThat(service.isLoggedIn(playerUuid))
                    .as("rejoining after deletion must not automatically log the account back in")
                    .isFalse();
        }

        @Test
        @DisplayName("Recovery path end to end: EmailVerificationService.resetPasswordAfterRecovery invalidates the session by delegation")
        void recoveryPathInvalidatesSessionByDelegation() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            String salt = "recoverySalt";
            String password = "recoveryPass123";
            String hash = hashPasswordForTest(password, salt);
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            // Log the player in for real, creating a genuine session entry keyed IP:UUID.
            service.login(player, password);
            assertThat(service.hasValidSession(player)).isTrue();

            // Drive the actual recovery entry point rather than calling LoginService.resetPassword
            // directly -- this is what proves the delegation still reaches the real invalidation,
            // not merely that the invalidation exists on the two-argument overload.
            com.ultikits.plugins.login.config.EmailConfig emailConfig =
                    mock(com.ultikits.plugins.login.config.EmailConfig.class);
            EmailVerificationService emailVerificationService =
                    new EmailVerificationService(UltiLoginTestHelper.getMockPlugin(), emailConfig, service);

            // Seed the verified-recovery state directly, bypassing the request/verify-code flow
            // EmailVerificationServiceTest already covers in full -- this test is scoped to
            // proving the delegation invalidates the session, not re-testing code verification.
            Field recoveryVerifiedField = EmailVerificationService.class.getDeclaredField("recoveryVerified");
            recoveryVerifiedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> recoveryVerified =
                    (Map<UUID, String>) recoveryVerifiedField.get(emailVerificationService);
            recoveryVerified.put(playerUuid, service.getPlayerIp(player));

            boolean result = emailVerificationService.resetPasswordAfterRecovery(player, "newRecoveredPass456");

            assertThat(result).isTrue();
            assertThat(service.hasValidSession(player))
                    .as("a recovery-flow password reset must end the player's existing session")
                    .isFalse();
        }
    }

    // ==================== command allowed ====================

    @Nested
    @DisplayName("isCommandAllowed")
    class IsCommandAllowed {

        @Test
        @DisplayName("Should allow login commands")
        void allowLoginCommands() {
            when(config.getAllowedCommands())
                    .thenReturn(Arrays.asList("login", "l", "register", "reg"));

            assertThat(service.isCommandAllowed("/login password")).isTrue();
            assertThat(service.isCommandAllowed("/l password")).isTrue();
            assertThat(service.isCommandAllowed("/register pw pw")).isTrue();
        }

        @Test
        @DisplayName("Should deny other commands")
        void denyOtherCommands() {
            when(config.getAllowedCommands())
                    .thenReturn(Arrays.asList("login", "register"));

            assertThat(service.isCommandAllowed("/help")).isFalse();
            assertThat(service.isCommandAllowed("/spawn")).isFalse();
        }
    }

    // ==================== recovery reachability across an upgrade reload (13-13, UltiLogin#13) ====================

    /**
     * UltiLogin#13: on a server that installed UltiLogin before {@code regs}/{@code recover}
     * were added to {@code allowedCommands}' default, those two commands stay unreachable even
     * after an operator corrects {@code login.yml} and reloads. The plan 13-13 measurement
     * (13-LEDGER-UltiLogin.md, "Recovery command diagnosis") found the cause is neither the
     * {@code LoginConfig} field-binding (proven working in isolation) nor {@code isCommandAllowed}'s
     * own string parsing (also proven working) -- it is {@code UltiLogin.reloadSelf()} itself,
     * which overrides {@link UltiToolsPlugin#reloadSelf()} without calling {@code super.reloadSelf()},
     * so {@code ConfigManager.reloadConfigs(...)} -- the only thing that re-reads {@code login.yml}
     * into a running {@code LoginConfig} -- is never invoked, no matter how many times
     * {@code /ul reload UltiLogin} runs or what the file says afterward.
     * <p>
     * Every test here drives the REAL {@link ConfigManager}, the REAL
     * {@link com.ultikits.plugins.login.config.LoginConfig#init}/{@code reloadConfigs} binding, and
     * the REAL {@code UltiLogin.reloadSelf()} method body -- not a re-implementation or a stub of
     * any of the three -- against a stored configuration in the shape an upgraded server actually
     * has (missing {@code regs}/{@code recover}), exactly as 13-13's plan requires.
     */
    @Nested
    @DisplayName("Recovery command reachability across an upgrade reload")
    class RecoveryReachabilityOnUpgradedServer {

        /**
         * Builds the upgraded-server fixture: a real {@link LoginConfig} registered with a real
         * {@link ConfigManager} against a temp config folder holding the pre-{@code regs}/
         * {@code recover} shape, plus a real {@link LoginService} bound to that same
         * {@code LoginConfig} instance -- the exact object identity chain
         * {@code PluginManager.assemblePluginContainer} produces in production (config entities are
         * registered as container singletons from {@code ConfigManager}'s own map, so the bean
         * {@code LoginService}'s constructor receives IS the map's entry).
         */
        private UpgradedServerFixture buildUpgradedServerFixture() throws Exception {
            Path configRoot = Files.createTempDirectory("ultilogin-13-13-upgrade-server");
            File loginYml = configRoot.resolve("config").resolve("login.yml").toFile();
            assertThat(loginYml.getParentFile().mkdirs())
                    .as("temp config directory must be created")
                    .isTrue();
            writeAllowedCommands(loginYml, "login", "l", "register", "reg", "panel");

            UltiLogin realPlugin = mock(UltiLogin.class, (Answer<Object>) invocation -> {
                String name = invocation.getMethod().getName();
                if ("getConfigFile".equals(name)) {
                    return new File(configRoot.toFile(), (String) invocation.getArguments()[0]);
                }
                if ("getConfigFolder".equals(name)) {
                    return configRoot.toFile().getAbsolutePath();
                }
                if ("getResourceFolderPath".equals(name)) {
                    // WR-02 (13-REVIEW-UltiLogin.md): ConfigManager.register(...) reads this
                    // Lombok-generated public getter (distinct from getConfigFolder/getConfigFile
                    // above, both protected final) to build `new File(getResourceFolderPath(),
                    // "config/login.yml")`. Left un-stubbed, it falls through to
                    // RETURNS_DEFAULTS -> null, and File(null, child) happens to treat that as
                    // "relative to the process CWD" -- so isDirectory() only returns false because
                    // no such directory exists relative to wherever the test JVM's CWD is. Stub it
                    // explicitly so this fixture does not depend on that accident.
                    return configRoot.toFile().getAbsolutePath();
                }
                return RETURNS_DEFAULTS.answer(invocation);
            });
            PluginLogger logger = mock(PluginLogger.class);
            when(realPlugin.getLogger()).thenReturn(logger);
            when(realPlugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
            doCallRealMethod().when(realPlugin).reloadSelf();

            LoginConfig realConfig = new LoginConfig();
            ConfigManager realConfigManager = new ConfigManager();
            // Mirrors ConfigManager.registerAll's own addConfigEntity(): init() runs against the
            // pre-upgrade file first, then the instance is stored -- the same order production
            // follows at plugin load.
            realConfigManager.register(realPlugin, realConfig);

            LoginService realService = new LoginService(realPlugin, realConfig);

            return new UpgradedServerFixture(configRoot, loginYml, realPlugin, realConfig, realConfigManager, realService);
        }

        @Test
        @DisplayName("An unauthenticated player can reach the recovery command on an upgraded server")
        void anUnauthenticatedPlayerCanReachTheRecoveryCommandOnAnUpgradedServer() throws Exception {
            UpgradedServerFixture fixture = buildUpgradedServerFixture();

            // Precondition, matching the issue's own original observation: before any correction,
            // an upgraded server's recovery command is unreachable.
            assertThat(fixture.service.isCommandAllowed("/recover"))
                    .as("an upgraded server's original login.yml has no regs/recover entries yet")
                    .isFalse();

            // The operator's own retest: correct login.yml to the current default, then reload.
            writeAllowedCommands(fixture.loginYml,
                    "login", "l", "register", "reg", "panel", "regs", "recover");

            try (MockedStatic<UltiToolsPlugin> staticMock =
                    mockStatic(UltiToolsPlugin.class, CALLS_REAL_METHODS)) {
                staticMock.when(UltiToolsPlugin::getConfigManager).thenReturn(fixture.configManager);
                fixture.plugin.reloadSelf();
            }

            assertThat(fixture.service.isCommandAllowed("/recover"))
                    .as("a corrected login.yml plus a reload must make /recover reachable")
                    .isTrue();
        }

        @Test
        @DisplayName("An unauthenticated player still cannot reach a command that is not permitted")
        void anUnauthenticatedPlayerStillCannotReachACommandThatIsNotPermitted() throws Exception {
            UpgradedServerFixture fixture = buildUpgradedServerFixture();

            writeAllowedCommands(fixture.loginYml,
                    "login", "l", "register", "reg", "panel", "regs", "recover");

            try (MockedStatic<UltiToolsPlugin> staticMock =
                    mockStatic(UltiToolsPlugin.class, CALLS_REAL_METHODS)) {
                staticMock.when(UltiToolsPlugin::getConfigManager).thenReturn(fixture.configManager);
                fixture.plugin.reloadSelf();
            }

            // The fix must not widen the gate into a hole (T-13-13-01): a command outside the
            // permitted set stays refused after the reload, exactly as before it.
            assertThat(fixture.service.isCommandAllowed("/definitelynotanallowedcommand"))
                    .as("the reload fix must not permit a command that was never on the list")
                    .isFalse();
        }

        @Test
        @DisplayName("The list the running plugin holds matches what was measured")
        void theListTheRunningPluginHoldsMatchesWhatWasMeasured() throws Exception {
            UpgradedServerFixture fixture = buildUpgradedServerFixture();

            assertThat(fixture.config.getAllowedCommands())
                    .as("in memory, before any correction, the upgraded server's list is exactly the file's")
                    .containsExactly("login", "l", "register", "reg", "panel");

            writeAllowedCommands(fixture.loginYml,
                    "login", "l", "register", "reg", "panel", "regs", "recover");

            try (MockedStatic<UltiToolsPlugin> staticMock =
                    mockStatic(UltiToolsPlugin.class, CALLS_REAL_METHODS)) {
                staticMock.when(UltiToolsPlugin::getConfigManager).thenReturn(fixture.configManager);
                fixture.plugin.reloadSelf();
            }

            // Direct assertion on the list the running plugin holds -- the mechanism, not only the
            // symptom -- reproducing 13-LEDGER-UltiLogin.md's own measured second-init() output.
            assertThat(fixture.config.getAllowedCommands())
                    .as("in memory, after a correct file plus a reload, the list must match the file")
                    .containsExactly("login", "l", "register", "reg", "panel", "regs", "recover");
        }

        private void writeAllowedCommands(File file, String... commands) throws Exception {
            StringBuilder sb = new StringBuilder("allowed-commands:\n");
            for (String c : commands) {
                sb.append("- ").append(c).append('\n');
            }
            Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        private final class UpgradedServerFixture {
            final Path configRoot;
            final File loginYml;
            final UltiLogin plugin;
            final LoginConfig config;
            final ConfigManager configManager;
            final LoginService service;

            UpgradedServerFixture(Path configRoot, File loginYml, UltiLogin plugin, LoginConfig config,
                                   ConfigManager configManager, LoginService service) {
                this.configRoot = configRoot;
                this.loginYml = loginYml;
                this.plugin = plugin;
                this.config = config;
                this.configManager = configManager;
                this.service = service;
            }
        }
    }

    // ==================== getAccount ====================

    @Nested
    @DisplayName("getAccount")
    class GetAccount {

        @Test
        @DisplayName("Should return account when exists")
        void accountExists() {
            AccountData expected = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(expected));

            AccountData result = service.getAccount(playerUuid);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("Should select canonical lowest-id account when duplicate player UUID rows exist")
        void duplicatePlayerUuidRowsSelectLowestIdAccount() {
            AccountData newer = UltiLoginTestHelper.createSampleAccount(playerUuid, "Newer", "hash2", "salt2");
            newer.setId("account-200");
            AccountData canonical = UltiLoginTestHelper.createSampleAccount(playerUuid, "Canonical", "hash1", "salt1");
            canonical.setId("account-100");
            when(mockQuery.list())
                    .thenReturn(Arrays.asList(newer, canonical));

            AccountData result = service.getAccount(playerUuid);

            assertThat(result).isSameAs(canonical);
        }

        @Test
        @DisplayName("Should return null when doesn't exist")
        void noAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            assertThat(service.getAccount(playerUuid)).isNull();
        }
    }

    // ==================== getAccountByName ====================

    @Nested
    @DisplayName("getAccountByName")
    class GetAccountByName {

        @Test
        @DisplayName("Should return account when exists")
        void accountExists() {
            AccountData expected = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(expected));

            AccountData result = service.getAccountByName("TestPlayer");

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("Should return null when doesn't exist")
        void noAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            assertThat(service.getAccountByName("Unknown")).isNull();
        }
    }

    // ==================== getPlayerIp ====================

    @Nested
    @DisplayName("getPlayerIp")
    class GetPlayerIp {

        @Test
        @DisplayName("Should return IP address")
        void returnIp() {
            String ip = service.getPlayerIp(player);
            assertThat(ip).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should return unknown when address is null")
        void nullAddress() {
            Player player2 = mock(Player.class);
            when(player2.getAddress()).thenReturn(null);

            String ip = service.getPlayerIp(player2);
            assertThat(ip).isEqualTo("unknown");
        }
    }

    // ==================== isRegisteredByName ====================

    @Nested
    @DisplayName("isRegisteredByName")
    class IsRegisteredByName {

        @Test
        @DisplayName("Should return true when account exists by name")
        void accountExists() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            assertThat(service.isRegisteredByName("TestPlayer")).isTrue();
        }

        @Test
        @DisplayName("Should return false when no account by name")
        void noAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            assertThat(service.isRegisteredByName("Unknown")).isFalse();
        }
    }

    // ==================== isLocked (extended branches) ====================

    @Nested
    @DisplayName("isLocked (extended)")
    class IsLockedExtended {

        @Test
        @DisplayName("Should return false when not locked")
        void notLocked() {
            when(config.getLockoutType()).thenReturn("IP");
            assertThat(service.isLocked(player)).isFalse();
        }

        @Test
        @DisplayName("Should detect UUID lockout type")
        void uuidLockoutType() {
            when(config.getMaxLoginAttempts()).thenReturn(2);
            when(config.getLockoutDuration()).thenReturn(900);
            when(config.getLockoutType()).thenReturn("UUID");

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.login(player, "wrong1");
            service.login(player, "wrong2");

            assertThat(service.isLocked(player)).isTrue();
        }

        @Test
        @DisplayName("Should detect BOTH lockout type")
        void bothLockoutType() {
            when(config.getMaxLoginAttempts()).thenReturn(2);
            when(config.getLockoutDuration()).thenReturn(900);
            when(config.getLockoutType()).thenReturn("BOTH");

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.login(player, "wrong1");
            service.login(player, "wrong2");

            assertThat(service.isLocked(player)).isTrue();
        }

        @Test
        @DisplayName("Should unlock expired IP lock")
        void expiredIpLock() throws Exception {
            when(config.getMaxLoginAttempts()).thenReturn(1);
            when(config.getLockoutDuration()).thenReturn(1); // 1 second
            when(config.getLockoutType()).thenReturn("IP");

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.login(player, "wrong");

            // Manually set lock to the past via reflection
            @SuppressWarnings("unchecked")
            Map<String, Long> lockedIps = (Map<String, Long>) getFieldValue(service, "lockedIps");
            for (String key : lockedIps.keySet()) {
                lockedIps.put(key, System.currentTimeMillis() - 1000);
            }

            assertThat(service.isLocked(player)).isFalse();
        }

        @Test
        @DisplayName("Should unlock expired UUID lock")
        void expiredUuidLock() throws Exception {
            when(config.getMaxLoginAttempts()).thenReturn(1);
            when(config.getLockoutDuration()).thenReturn(1);
            when(config.getLockoutType()).thenReturn("UUID");

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.login(player, "wrong");

            // Manually set lock to the past
            @SuppressWarnings("unchecked")
            Map<UUID, Long> lockedUuids = (Map<UUID, Long>) getFieldValue(service, "lockedUuids");
            for (UUID key : lockedUuids.keySet()) {
                lockedUuids.put(key, System.currentTimeMillis() - 1000);
            }

            assertThat(service.isLocked(player)).isFalse();
        }
    }

    // ==================== getRemainingLockoutTime ====================

    @Nested
    @DisplayName("getRemainingLockoutTime")
    class GetRemainingLockoutTime {

        @Test
        @DisplayName("Should return 0 when not locked")
        void notLocked() {
            assertThat(service.getRemainingLockoutTime(player)).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return remaining time for IP lock")
        void ipLockRemaining() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Long> lockedIps = (Map<String, Long>) getFieldValue(service, "lockedIps");
            lockedIps.put("127.0.0.1", System.currentTimeMillis() + 60000); // 60 seconds

            long remaining = service.getRemainingLockoutTime(player);
            assertThat(remaining).isGreaterThan(0);
            assertThat(remaining).isLessThanOrEqualTo(60);
        }

        @Test
        @DisplayName("Should return remaining time for UUID lock")
        void uuidLockRemaining() throws Exception {
            @SuppressWarnings("unchecked")
            Map<UUID, Long> lockedUuids = (Map<UUID, Long>) getFieldValue(service, "lockedUuids");
            lockedUuids.put(playerUuid, System.currentTimeMillis() + 120000); // 120 seconds

            long remaining = service.getRemainingLockoutTime(player);
            assertThat(remaining).isGreaterThan(0);
            assertThat(remaining).isLessThanOrEqualTo(120);
        }

        @Test
        @DisplayName("Should return max of IP and UUID remaining time")
        void bothLocksMaxRemaining() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Long> lockedIps = (Map<String, Long>) getFieldValue(service, "lockedIps");
            lockedIps.put("127.0.0.1", System.currentTimeMillis() + 30000);

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lockedUuids = (Map<UUID, Long>) getFieldValue(service, "lockedUuids");
            lockedUuids.put(playerUuid, System.currentTimeMillis() + 120000);

            long remaining = service.getRemainingLockoutTime(player);
            // Should pick the larger of the two
            assertThat(remaining).isGreaterThan(30);
        }
    }

    // ==================== getRemainingAttempts ====================

    @Nested
    @DisplayName("getRemainingAttempts")
    class GetRemainingAttempts {

        @Test
        @DisplayName("Should return -1 when max attempts is 0 (unlimited)")
        void unlimited() {
            when(config.getMaxLoginAttempts()).thenReturn(0);

            assertThat(service.getRemainingAttempts(player)).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should return -1 when max attempts is negative")
        void negative() {
            when(config.getMaxLoginAttempts()).thenReturn(-1);

            assertThat(service.getRemainingAttempts(player)).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should return full attempts when no failed attempts")
        void fullAttempts() {
            when(config.getMaxLoginAttempts()).thenReturn(5);

            assertThat(service.getRemainingAttempts(player)).isEqualTo(5);
        }
    }

    // ==================== getPasswordValidationError ====================

    @Nested
    @DisplayName("getPasswordValidationError")
    class GetPasswordValidationError {

        @Test
        @DisplayName("Should return GUI error in GUI mode")
        void guiModeError() {
            when(config.isGuiModeEnabled()).thenReturn(true);
            when(config.getGuiPasswordInvalid()).thenReturn("Must be {LENGTH} digits");
            when(config.getGuiPasswordLength()).thenReturn(4);

            String error = service.getPasswordValidationError("abc");
            assertThat(error).contains("4");
        }

        @Test
        @DisplayName("Should return too-short error in command mode")
        void commandModeTooShort() {
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(config.getMinPasswordLength()).thenReturn(6);
            when(config.getPasswordTooShort()).thenReturn("Too short! Min {MIN}");

            String error = service.getPasswordValidationError("abc");
            assertThat(error).contains("6");
        }

        @Test
        @DisplayName("Should return too-long error in command mode")
        void commandModeTooLong() {
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(config.getMinPasswordLength()).thenReturn(6);
            when(config.getMaxPasswordLength()).thenReturn(10);
            when(config.getPasswordTooLong()).thenReturn("Too long! Max {MAX}");

            String error = service.getPasswordValidationError("a".repeat(15));
            assertThat(error).contains("10");
        }

        @Test
        @DisplayName("Should return empty string when password is valid in command mode")
        void validPassword() {
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(config.getMinPasswordLength()).thenReturn(6);
            when(config.getMaxPasswordLength()).thenReturn(32);

            String error = service.getPasswordValidationError("validPass");
            assertThat(error).isEmpty();
        }
    }

    // ==================== hasValidSession (extended) ====================

    @Nested
    @DisplayName("hasValidSession (extended)")
    class HasValidSessionExtended {

        @Test
        @DisplayName("Should return false when no session entry exists")
        void noSessionEntry() {
            when(config.isSessionEnabled()).thenReturn(true);
            assertThat(service.hasValidSession(player)).isFalse();
        }

        @Test
        @DisplayName("Should return false when session expired")
        void expiredSession() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);
            when(config.getSessionTimeout()).thenReturn(1); // 1 minute

            // Manually inject an expired session
            @SuppressWarnings("unchecked")
            Map<String, Long> sessions = (Map<String, Long>) getFieldValue(service, "sessions");
            String key = "127.0.0.1:" + playerUuid;
            sessions.put(key, System.currentTimeMillis() - 120000); // 2 minutes ago

            assertThat(service.hasValidSession(player)).isFalse();
        }
    }

    // ==================== onPlayerQuit ====================

    @Nested
    @DisplayName("onPlayerQuit")
    class OnPlayerQuit {

        @Test
        @DisplayName("Should clear player tracking state")
        void clearTracking() {
            // Set up player as logged in
            service.completeLogin(player);
            assertThat(service.isLoggedIn(playerUuid)).isTrue();

            // Quit
            service.onPlayerQuit(player);
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }
    }

    // ==================== shutdown ====================

    @Nested
    @DisplayName("shutdown")
    class Shutdown {

        @Test
        @DisplayName("Should clear all tracking maps")
        void clearAllMaps() {
            service.completeLogin(player);
            assertThat(service.isLoggedIn(playerUuid)).isTrue();

            service.shutdown();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }
    }

    // ==================== login with locked player ====================

    @Nested
    @DisplayName("login with locked player")
    class LoginWithLockedPlayer {

        @Test
        @DisplayName("Should return locked message when player is locked")
        void lockedPlayer() throws Exception {
            // Manually lock the IP
            @SuppressWarnings("unchecked")
            Map<String, Long> lockedIps = (Map<String, Long>) getFieldValue(service, "lockedIps");
            lockedIps.put("127.0.0.1", System.currentTimeMillis() + 900000);

            when(config.getLockoutType()).thenReturn("IP");
            when(config.getAccountLocked()).thenReturn("Account locked! Try in {TIME}s");

            LoginService.LoginResult result = service.login(player, "password");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Account locked");
        }
    }

    // ==================== login with update exception ====================

    @Nested
    @DisplayName("login with update exception")
    class LoginWithUpdateException {

        @Test
        @DisplayName("Should still complete login when update throws")
        void updateThrows() throws Exception {
            String salt = "testSalt";
            String password = "password123";
            String hash = hashPasswordForTest(password, salt);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));
            doThrow(new IllegalAccessException("update failed")).when(dataOperator).update(any());

            LoginService.LoginResult result = service.login(player, password);

            // Login should still succeed (update failure is logged, not propagated)
            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ==================== changePassword with update exception ====================

    @Nested
    @DisplayName("changePassword with update exception")
    class ChangePasswordUpdateException {

        @Test
        @DisplayName("Should return false when update throws")
        void updateThrows() throws Exception {
            String salt = "testSalt";
            String oldPassword = "oldPass123";
            String hash = hashPasswordForTest(oldPassword, salt);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));
            doThrow(new IllegalAccessException("update failed")).when(dataOperator).update(any());

            boolean result = service.changePassword(playerUuid, oldPassword, "newPass");

            assertThat(result).isFalse();
        }
    }

    // ==================== resetPassword with update exception ====================

    @Nested
    @DisplayName("resetPassword with update exception")
    class ResetPasswordUpdateException {

        @Test
        @DisplayName("Random reset should return null when update throws")
        void randomResetUpdateThrows() throws Exception {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));
            doThrow(new IllegalAccessException("update failed")).when(dataOperator).update(any());

            String result = service.resetPassword(playerUuid);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Specific reset should return false when update throws")
        void specificResetUpdateThrows() throws Exception {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));
            doThrow(new IllegalAccessException("update failed")).when(dataOperator).update(any());

            boolean result = service.resetPassword(playerUuid, "newPass");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when account not found for specific reset")
        void specificResetNoAccount() {
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            boolean result = service.resetPassword(playerUuid, "newPass");

            assertThat(result).isFalse();
        }
    }

    // ==================== resetPassword GUI mode ====================

    @Nested
    @DisplayName("resetPassword GUI mode")
    class ResetPasswordGuiMode {

        @Test
        @DisplayName("Should generate numeric password in GUI mode")
        void guiModePassword() throws Exception {
            when(config.isGuiModeEnabled()).thenReturn(true);
            when(config.getGuiPasswordLength()).thenReturn(4);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            String newPassword = service.resetPassword(playerUuid);

            assertThat(newPassword).isNotNull();
            assertThat(newPassword).hasSize(4);
            assertThat(newPassword).matches("\\d{4}");
        }
    }

    // ==================== register with IP limit disabled ====================

    @Nested
    @DisplayName("register with IP limit disabled")
    class RegisterIpLimitDisabled {

        @Test
        @DisplayName("Should skip IP check when maxRegisterPerIp is 0")
        void ipLimitDisabled() {
            when(config.getMaxRegisterPerIp()).thenReturn(0);
            when(mockQuery.list())
                    .thenReturn(Collections.emptyList());

            boolean result = service.register(player, "password123");

            assertThat(result).isTrue();
            verify(dataOperator).insert(any(AccountData.class));
        }
    }

    // ==================== recordFailedAttempt when disabled ====================

    @Nested
    @DisplayName("recordFailedAttempt when disabled")
    class RecordFailedAttemptDisabled {

        @Test
        @DisplayName("Should not track failed attempts when maxLoginAttempts is 0")
        void disabled() {
            when(config.getMaxLoginAttempts()).thenReturn(0);

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            service.login(player, "wrong");

            // getRemainingAttempts returns -1 (unlimited)
            assertThat(service.getRemainingAttempts(player)).isEqualTo(-1);
        }
    }

    // ==================== login wrong password returns lock message at exactly max ====================

    @Nested
    @DisplayName("login at exact max attempts threshold")
    class LoginAtMaxAttempts {

        @Test
        @DisplayName("Should return lock message when reaching max attempts")
        void lockMessage() {
            when(config.getMaxLoginAttempts()).thenReturn(1);
            when(config.getLockoutDuration()).thenReturn(300);
            when(config.getLockoutType()).thenReturn("IP");
            when(config.getAccountLocked()).thenReturn("Locked for {TIME}s");

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            // This single failed attempt should lock and return lock message
            LoginService.LoginResult result = service.login(player, "wrong");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("300");
        }
    }

    // ==================== completeLogin with spawn location ====================

    @Nested
    @DisplayName("completeLogin with spawn location")
    class CompleteLoginWithSpawn {

        @Test
        @DisplayName("Should not teleport back when spawn location disabled")
        void spawnDisabled() {
            when(config.isSpawnLocationEnabled()).thenReturn(false);

            service.completeLogin(player);

            // Should not call teleport (only removePotionEffect)
            verify(player, never()).teleport(any(org.bukkit.Location.class));
        }

        @Test
        @DisplayName("Should teleport back when spawn location enabled and original exists")
        void spawnEnabledWithOriginal() throws Exception {
            when(config.isSpawnLocationEnabled()).thenReturn(true);

            // Manually inject an original location
            org.bukkit.Location originalLoc = mock(org.bukkit.Location.class);
            @SuppressWarnings("unchecked")
            Map<UUID, org.bukkit.Location> originalLocations =
                    (Map<UUID, org.bukkit.Location>) getFieldValue(service, "originalLocations");
            originalLocations.put(playerUuid, originalLoc);

            service.completeLogin(player);

            verify(player).teleport(originalLoc);
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
        }

        @Test
        @DisplayName("Should not teleport when spawn enabled but no original location")
        void spawnEnabledNoOriginal() {
            when(config.isSpawnLocationEnabled()).thenReturn(true);

            service.completeLogin(player);

            verify(player, never()).teleport(any(org.bukkit.Location.class));
        }
    }

    // ==================== getConfig ====================

    @Nested
    @DisplayName("getConfig")
    class GetConfig {

        @Test
        @DisplayName("Should return injected config")
        void returnConfig() {
            assertThat(service.getConfig()).isSameAs(config);
        }
    }

    // ==================== LoginResult ====================

    @Nested
    @DisplayName("LoginResult")
    class LoginResultTest {

        @Test
        @DisplayName("Should store success and message")
        void storeValues() {
            LoginService.LoginResult result = new LoginService.LoginResult(true, "Success!");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Success!");
        }

        @Test
        @DisplayName("Should store failure and message")
        void storeFailure() {
            LoginService.LoginResult result = new LoginService.LoginResult(false, "Failed!");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Failed!");
        }
    }

    // ==================== onPlayerJoin ====================

    @Nested
    @DisplayName("onPlayerJoin")
    class OnPlayerJoin {

        @Test
        @DisplayName("Should mark player as not logged in")
        void markNotLoggedIn() {
            // Mock player location for clone
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.onPlayerJoin(player);

            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("Should auto-login when session is valid")
        void autoLoginWithSession() throws Exception {
            // Set up a valid session
            when(config.isSessionEnabled()).thenReturn(true);
            when(config.getSessionTimeout()).thenReturn(30);

            @SuppressWarnings("unchecked")
            Map<String, Long> sessions = (Map<String, Long>) getFieldValue(service, "sessions");
            sessions.put("127.0.0.1:" + playerUuid, System.currentTimeMillis());

            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);

            service.onPlayerJoin(player);

            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            verify(player).sendMessage(contains("会话有效"));
        }

        @Test
        @DisplayName("Should apply blind effect when enabled and no session")
        void applyBlindEffect() {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(true);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.onPlayerJoin(player);

            verify(player).addPotionEffect(any(PotionEffect.class));
        }

        @Test
        @DisplayName("Should not apply blind effect when disabled")
        void noBlindEffect() {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.onPlayerJoin(player);

            verify(player, never()).addPotionEffect(any(PotionEffect.class));
        }

        @Test
        @DisplayName("Should teleport to spawn when spawn location enabled and world exists")
        void teleportToSpawn() {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(true);
            when(config.getSpawnWorld()).thenReturn("world");
            when(config.getSpawnX()).thenReturn(100.0);
            when(config.getSpawnY()).thenReturn(64.0);
            when(config.getSpawnZ()).thenReturn(200.0);

            // Mock Bukkit.getWorld via the server field
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                World mockWorld = mock(World.class);
                doReturn(mockWorld).when(server).getWorld("world");
            } catch (Exception e) {
                // Skip if can't mock
            }

            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.onPlayerJoin(player);

            verify(player).teleport(any(Location.class));
        }

        @Test
        @DisplayName("Should not teleport when spawn location enabled but world is null")
        void noTeleportNullWorld() {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(true);
            when(config.getSpawnWorld()).thenReturn("nonexistent_world");

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(null).when(server).getWorld("nonexistent_world");
            } catch (Exception e) {
                // Skip if can't mock
            }

            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.onPlayerJoin(player);

            verify(player, never()).teleport(any(Location.class));
        }

        @Test
        @DisplayName("Should send login prompt for registered player in command mode")
        void loginPromptCommandMode() {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(config.getLoginPrompt()).thenReturn("&eLogin with /login");

            // Player IS registered
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.onPlayerJoin(player);

            verify(player).sendMessage(contains("Login with /login"));
        }

        @Test
        @DisplayName("Should send login prompt for registered player in GUI mode")
        void loginPromptGuiMode() {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(config.isGuiModeEnabled()).thenReturn(true);
            when(config.getLoginPromptGui()).thenReturn("&eUse GUI to login");

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.onPlayerJoin(player);

            verify(player).sendMessage(contains("Use GUI to login"));
        }

        @Test
        @DisplayName("Should send register prompt for unregistered player in command mode")
        void registerPromptCommandMode() {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(config.getRegisterPrompt()).thenReturn("&eRegister with /register");

            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.onPlayerJoin(player);

            verify(player).sendMessage(contains("Register with /register"));
        }

        @Test
        @DisplayName("Should send register prompt for unregistered player in GUI mode")
        void registerPromptGuiMode() {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(config.isGuiModeEnabled()).thenReturn(true);
            when(config.getRegisterPromptGui()).thenReturn("&eUse GUI to register");

            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.onPlayerJoin(player);

            verify(player).sendMessage(contains("Use GUI to register"));
        }

        @Test
        @DisplayName("Should store original location on join")
        void storeOriginalLocation() throws Exception {
            Location mockLoc = mock(Location.class);
            Location clonedLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(clonedLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.onPlayerJoin(player);

            @SuppressWarnings("unchecked")
            Map<UUID, Location> originalLocations =
                    (Map<UUID, Location>) getFieldValue(service, "originalLocations");
            assertThat(originalLocations).containsKey(playerUuid);
            assertThat(originalLocations.get(playerUuid)).isSameAs(clonedLoc);
        }

        @Test
        @DisplayName("Should track join time")
        void trackJoinTime() throws Exception {
            Location mockLoc = mock(Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            when(player.getLocation()).thenReturn(mockLoc);
            when(config.isSessionEnabled()).thenReturn(false);
            when(config.isBlindEffect()).thenReturn(false);
            when(config.isSpawnLocationEnabled()).thenReturn(false);
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            long before = System.currentTimeMillis();
            service.onPlayerJoin(player);

            @SuppressWarnings("unchecked")
            Map<UUID, Long> joinTimes = (Map<UUID, Long>) getFieldValue(service, "joinTimes");
            assertThat(joinTimes).containsKey(playerUuid);
            assertThat(joinTimes.get(playerUuid)).isGreaterThanOrEqualTo(before);
        }
    }

    // ==================== onPlayerQuit with polling task ====================

    @Nested
    @DisplayName("onPlayerQuit with polling task")
    class OnPlayerQuitWithPolling {

        @Test
        @DisplayName("Should cancel polling task on quit")
        void cancelPollingTask() throws Exception {
            @SuppressWarnings("unchecked")
            Map<UUID, BukkitTask> pollingTasks =
                    (Map<UUID, BukkitTask>) getFieldValue(service, "pollingTasks");

            BukkitTask mockTask = mock(BukkitTask.class);
            pollingTasks.put(playerUuid, mockTask);

            service.onPlayerQuit(player);

            verify(mockTask).cancel();
            assertThat(pollingTasks).doesNotContainKey(playerUuid);
        }

        @Test
        @DisplayName("Should handle quit when no polling task exists")
        void noPollingTask() {
            // Should not throw
            service.onPlayerQuit(player);
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }
    }

    // ==================== checkTimeouts ====================

    @Nested
    @DisplayName("checkTimeouts")
    class CheckTimeouts {

        @Test
        @DisplayName("Should kick timed-out players")
        void kickTimedOut() throws Exception {
            when(config.getLoginTimeout()).thenReturn(60);
            when(config.getTimeoutKick()).thenReturn("&cLogin timeout!");

            // Inject join time in the past (2 minutes ago)
            @SuppressWarnings("unchecked")
            Map<UUID, Long> joinTimes = (Map<UUID, Long>) getFieldValue(service, "joinTimes");
            joinTimes.put(playerUuid, System.currentTimeMillis() - 120000);

            // Mock Bukkit.getPlayer
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            service.checkTimeouts();

            verify(player).kickPlayer(contains("Login timeout"));
        }

        @Test
        @DisplayName("Should not kick players within timeout period")
        void noKickWithinTimeout() throws Exception {
            when(config.getLoginTimeout()).thenReturn(60);

            // Inject recent join time (just now)
            @SuppressWarnings("unchecked")
            Map<UUID, Long> joinTimes = (Map<UUID, Long>) getFieldValue(service, "joinTimes");
            joinTimes.put(playerUuid, System.currentTimeMillis());

            service.checkTimeouts();

            verify(player, never()).kickPlayer(anyString());
        }

        @Test
        @DisplayName("Should handle offline player in timeout check")
        void offlinePlayerTimeout() throws Exception {
            when(config.getLoginTimeout()).thenReturn(60);

            @SuppressWarnings("unchecked")
            Map<UUID, Long> joinTimes = (Map<UUID, Long>) getFieldValue(service, "joinTimes");
            UUID offlineUuid = UUID.randomUUID();
            joinTimes.put(offlineUuid, System.currentTimeMillis() - 120000);

            // Bukkit.getPlayer returns null for offline
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(null).when(server).getPlayer(offlineUuid);
            } catch (Exception e) {
                // Skip
            }

            // Should not throw
            service.checkTimeouts();

            verify(player, never()).kickPlayer(anyString());
        }

        @Test
        @DisplayName("Should handle player online check returning false")
        void playerNotOnline() throws Exception {
            when(config.getLoginTimeout()).thenReturn(60);

            @SuppressWarnings("unchecked")
            Map<UUID, Long> joinTimes = (Map<UUID, Long>) getFieldValue(service, "joinTimes");
            joinTimes.put(playerUuid, System.currentTimeMillis() - 120000);

            Player offlinePlayer = mock(Player.class);
            when(offlinePlayer.isOnline()).thenReturn(false);

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(offlinePlayer).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            service.checkTimeouts();

            verify(offlinePlayer, never()).kickPlayer(anyString());
        }
    }

    // ==================== Panel Methods ====================

    @Nested
    @DisplayName("isPanelEnabled")
    class IsPanelEnabled {

        @Test
        @DisplayName("Should return true when ulticloud enabled")
        void enabled() {
            when(config.isUlticloudEnabled()).thenReturn(true);
            assertThat(service.isPanelEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should return false when ulticloud disabled")
        void disabled() {
            when(config.isUlticloudEnabled()).thenReturn(false);
            assertThat(service.isPanelEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("generateVerificationCode")
    class GenerateVerificationCode {

        @Test
        @DisplayName("Should generate 6-digit code")
        void sixDigits() {
            String code = service.generateVerificationCode();

            assertThat(code).hasSize(6);
            assertThat(code).matches("\\d{6}");
        }

        @Test
        @DisplayName("Should generate codes >= 100000")
        void minValue() {
            // Generate multiple codes to verify range
            for (int i = 0; i < 20; i++) {
                String code = service.generateVerificationCode();
                int value = Integer.parseInt(code);
                assertThat(value).isGreaterThanOrEqualTo(100000);
                assertThat(value).isLessThanOrEqualTo(999999);
            }
        }

        @Test
        @DisplayName("Should generate different codes")
        void differentCodes() {
            Set<String> codes = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                codes.add(service.generateVerificationCode());
            }
            // With 10 random 6-digit codes, duplicates are extremely unlikely
            assertThat(codes.size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("requestPanelLink")
    class RequestPanelLink {

        @Test
        @DisplayName("Should return failure when panel not enabled")
        void panelNotEnabled() {
            when(config.isUlticloudEnabled()).thenReturn(false);

            LoginService.PanelLinkResult result = service.requestPanelLink(player);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getUrl()).isNull();
            assertThat(result.getError()).contains("not enabled");
        }

        @Test
        @DisplayName("Should not have pending request when panel not enabled")
        void noPendingRequestWhenDisabled() {
            when(config.isUlticloudEnabled()).thenReturn(false);

            service.requestPanelLink(player);

            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        }
    }

    @Nested
    @DisplayName("completePanelLogin")
    class CompletePanelLogin {

        @Test
        @DisplayName("Should return false for unknown request ID")
        void unknownRequestId() {
            boolean result = service.completePanelLogin("nonexistent-id");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for unknown request ID with role")
        void unknownRequestIdWithRole() {
            boolean result = service.completePanelLogin("nonexistent-id", true);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should complete login for valid pending request")
        void validRequest() throws Exception {
            // Inject pending request
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-request-123";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            // Mock Bukkit.getPlayer to return our player
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            // Return account for update
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            boolean result = service.completePanelLogin(requestId, false);

            assertThat(result).isTrue();
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            // Request should be cleaned up
            assertThat(pendingPanelRequests).doesNotContainKey(requestId);
            assertThat(pendingPanelTimestamps).doesNotContainKey(requestId);
        }

        @Test
        @DisplayName("Should send owner message when isServerOwner true")
        void ownerMessage() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-owner-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            when(mockQuery.list()).thenReturn(Collections.singletonList(
                    UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt")));

            boolean result = service.completePanelLogin(requestId, true);

            assertThat(result).isTrue();
            // Verify i18n was called with owner key
            verify(UltiLoginTestHelper.getMockPlugin()).i18n("panel_auth_success_owner");
        }

        @Test
        @DisplayName("Should send player message when isServerOwner false")
        void playerMessage() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-player-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            when(mockQuery.list()).thenReturn(Collections.singletonList(
                    UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt")));

            boolean result = service.completePanelLogin(requestId, false);

            assertThat(result).isTrue();
            verify(UltiLoginTestHelper.getMockPlugin()).i18n("panel_auth_success_player");
        }

        @Test
        @DisplayName("Should return false when player is offline")
        void playerOffline() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-offline-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            // Bukkit.getPlayer returns null (offline)
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(null).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            boolean result = service.completePanelLogin(requestId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should refuse to complete login when the account is no longer registered")
        void refusesLoginForDeletedAccount() throws Exception {
            // CR-01 (13-REVIEW-UltiLogin.md): a second, independent layer of defense alongside
            // invalidateSession's cancellation. If a magic-link request survives account deletion
            // for any reason (e.g. it was created after the account row was already gone, or the
            // cancellation path itself regresses), completePanelLogin must still refuse to mark a
            // non-existent account as logged in.
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-deleted-account-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            // No account for this UUID -- the account row was deleted (unregister) while the
            // magic-link request was still pending.
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            boolean result = service.completePanelLogin(requestId, false);

            assertThat(result)
                    .as("completePanelLogin must not log in a player whose account no longer exists")
                    .isFalse();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("Should create session after panel login when enabled")
        void createSessionAfterPanelLogin() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);

            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-session-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            when(mockQuery.list()).thenReturn(Collections.singletonList(
                    UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt")));

            service.completePanelLogin(requestId, false);

            assertThat(service.hasValidSession(player)).isTrue();
        }

        @Test
        @DisplayName("Should update account on panel login")
        void updateAccountOnPanelLogin() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-update-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));

            service.completePanelLogin(requestId, false);

            verify(dataOperator).update(any(AccountData.class));
        }

        @Test
        @DisplayName("Should handle update exception during panel login")
        void updateExceptionOnPanelLogin() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-exception-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));
            doThrow(new IllegalAccessException("update failed")).when(dataOperator).update(any());

            // Should still return true (login succeeds, update failure is logged)
            boolean result = service.completePanelLogin(requestId, false);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should refuse panel login when no account exists for the request")
        void nullAccountPanelLogin() throws Exception {
            // CR-01 (13-REVIEW-UltiLogin.md): this test previously asserted the bug itself --
            // that completePanelLogin "should still complete login" with no backing account.
            // That is exactly the deleted-account-logs-back-in defect the phase closes, so the
            // expectation is corrected here rather than left pinning the old behavior.
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-null-account-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            // No account exists
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            boolean result = service.completePanelLogin(requestId, false);

            assertThat(result).isFalse();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            // update should NOT be called since there is no account to log in or update
            verify(dataOperator, never()).update(any());
        }

        @Test
        @DisplayName("Single-arg completePanelLogin delegates to two-arg with isServerOwner=false")
        void singleArgDelegatesToTwoArg() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            String requestId = "test-delegate-request";
            pendingPanelRequests.put(requestId, playerUuid);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());

            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                Server server = (Server) serverField.get(null);
                doReturn(player).when(server).getPlayer(playerUuid);
            } catch (Exception e) {
                // Skip
            }

            when(mockQuery.list()).thenReturn(Collections.singletonList(
                    UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt")));

            boolean result = service.completePanelLogin(requestId);

            assertThat(result).isTrue();
            // Should use player message key (not owner)
            verify(UltiLoginTestHelper.getMockPlugin()).i18n("panel_auth_success_player");
        }
    }

    // ==================== hasPendingPanelRequest ====================

    @Nested
    @DisplayName("hasPendingPanelRequest")
    class HasPendingPanelRequest {

        @Test
        @DisplayName("Should return false when no pending requests")
        void noPendingRequests() {
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("Should return true when player has pending request")
        void hasPendingRequest() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            pendingPanelRequests.put("some-request-id", playerUuid);

            assertThat(service.hasPendingPanelRequest(playerUuid)).isTrue();
        }

        @Test
        @DisplayName("Should return false for different player UUID")
        void differentPlayer() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            pendingPanelRequests.put("some-request-id", UUID.randomUUID());

            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        }
    }

    // ==================== cleanupExpiredPanelRequests ====================

    @Nested
    @DisplayName("cleanupExpiredPanelRequests")
    class CleanupExpiredPanelRequests {

        @Test
        @DisplayName("Should remove expired requests")
        void removeExpired() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            // Add an expired request (6 minutes ago)
            pendingPanelRequests.put("expired-id", UUID.randomUUID());
            pendingPanelTimestamps.put("expired-id", System.currentTimeMillis() - 6 * 60 * 1000L);

            service.cleanupExpiredPanelRequests();

            assertThat(pendingPanelRequests).doesNotContainKey("expired-id");
            assertThat(pendingPanelTimestamps).doesNotContainKey("expired-id");
        }

        @Test
        @DisplayName("Should keep non-expired requests")
        void keepNonExpired() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            UUID someUuid = UUID.randomUUID();
            pendingPanelRequests.put("fresh-id", someUuid);
            pendingPanelTimestamps.put("fresh-id", System.currentTimeMillis());

            service.cleanupExpiredPanelRequests();

            assertThat(pendingPanelRequests).containsKey("fresh-id");
            assertThat(pendingPanelTimestamps).containsKey("fresh-id");
        }

        @Test
        @DisplayName("Should remove only expired requests when mixed")
        void mixedRequests() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            UUID expired1Uuid = UUID.randomUUID();
            UUID freshUuid = UUID.randomUUID();
            UUID expired2Uuid = UUID.randomUUID();

            pendingPanelRequests.put("expired-1", expired1Uuid);
            pendingPanelTimestamps.put("expired-1", System.currentTimeMillis() - 10 * 60 * 1000L);

            pendingPanelRequests.put("fresh-1", freshUuid);
            pendingPanelTimestamps.put("fresh-1", System.currentTimeMillis());

            pendingPanelRequests.put("expired-2", expired2Uuid);
            pendingPanelTimestamps.put("expired-2", System.currentTimeMillis() - 7 * 60 * 1000L);

            service.cleanupExpiredPanelRequests();

            assertThat(pendingPanelRequests).hasSize(1);
            assertThat(pendingPanelRequests).containsKey("fresh-1");
            assertThat(pendingPanelTimestamps).hasSize(1);
            assertThat(pendingPanelTimestamps).containsKey("fresh-1");
        }

        @Test
        @DisplayName("Should handle empty maps")
        void emptyMaps() {
            // Should not throw
            service.cleanupExpiredPanelRequests();
        }
    }

    // ==================== PanelLinkResult ====================

    @Nested
    @DisplayName("PanelLinkResult")
    class PanelLinkResultTest {

        @Test
        @DisplayName("Should store success result with URL")
        void successResult() {
            LoginService.PanelLinkResult result = new LoginService.PanelLinkResult(true, "https://panel.example.com/login", null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getUrl()).isEqualTo("https://panel.example.com/login");
            assertThat(result.getError()).isNull();
        }

        @Test
        @DisplayName("Should store failure result with error")
        void failureResult() {
            LoginService.PanelLinkResult result = new LoginService.PanelLinkResult(false, null, "API error");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getUrl()).isNull();
            assertThat(result.getError()).isEqualTo("API error");
        }

        @Test
        @DisplayName("Should handle null URL and null error")
        void nullValues() {
            LoginService.PanelLinkResult result = new LoginService.PanelLinkResult(false, null, null);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getUrl()).isNull();
            assertThat(result.getError()).isNull();
        }
    }

    // ==================== shutdown with polling tasks ====================

    @Nested
    @DisplayName("shutdown with polling tasks")
    class ShutdownWithPolling {

        @Test
        @DisplayName("Should cancel all polling tasks on shutdown")
        void cancelAllPollingTasks() throws Exception {
            @SuppressWarnings("unchecked")
            Map<UUID, BukkitTask> pollingTasks =
                    (Map<UUID, BukkitTask>) getFieldValue(service, "pollingTasks");

            BukkitTask task1 = mock(BukkitTask.class);
            BukkitTask task2 = mock(BukkitTask.class);
            pollingTasks.put(UUID.randomUUID(), task1);
            pollingTasks.put(UUID.randomUUID(), task2);

            service.shutdown();

            verify(task1).cancel();
            verify(task2).cancel();
            assertThat(pollingTasks).isEmpty();
        }

        @Test
        @DisplayName("Should clear pending panel requests on shutdown")
        void clearPendingRequests() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, UUID> pendingPanelRequests =
                    (Map<String, UUID>) getFieldValue(service, "pendingPanelRequests");
            @SuppressWarnings("unchecked")
            Map<String, Long> pendingPanelTimestamps =
                    (Map<String, Long>) getFieldValue(service, "pendingPanelTimestamps");

            pendingPanelRequests.put("req-1", UUID.randomUUID());
            pendingPanelTimestamps.put("req-1", System.currentTimeMillis());

            service.shutdown();

            assertThat(pendingPanelRequests).isEmpty();
            assertThat(pendingPanelTimestamps).isEmpty();
        }
    }

    // ==================== Helper methods ====================

    private String hashPasswordForTest(String password, String salt) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String input = password + salt;
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Object getFieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
