package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

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

import java.lang.reflect.Field;
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
        @DisplayName("Should force logout an online player when unregistering their account")
        void forceLogoutOnlinePlayerOnUnregister() {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list())
                    .thenReturn(Collections.singletonList(account));

            Location mockLocation = mock(Location.class);
            when(mockLocation.clone()).thenReturn(mockLocation);
            when(player.getLocation()).thenReturn(mockLocation);

            try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
                bukkitMock.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(player);

                boolean result = service.unregister(playerUuid);

                assertThat(result).isTrue();
                assertThat(service.isLoggedIn(playerUuid)).isFalse();
                // onPlayerJoin(player) re-runs the join flow as part of the forced logout;
                // this player is registered so it sends the (re-)login prompt, which is
                // otherwise observable only as a side effect of that call actually happening.
                verify(player).sendMessage(anyString());
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
        @DisplayName("Should handle null account during panel login")
        void nullAccountPanelLogin() throws Exception {
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

            // Should still complete login (account update is optional)
            assertThat(result).isTrue();
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            // update should NOT be called since account is null
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
