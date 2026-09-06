package com.ultikits.plugins.login;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test helper for mocking UltiTools framework singletons.
 * <p>
 * UltiLogin is a {@code final class extends UltiToolsPlugin} — mockable.
 * This helper mocks UltiLogin singleton and logger to avoid framework dependencies.
 * Tests that need database operations mock DataOperator.
 * <p>
 * Call {@link #setUp()} in {@code @BeforeEach} and {@link #tearDown()} in {@code @AfterEach}.
 */
public final class UltiLoginTestHelper {

    private UltiLoginTestHelper() {}

    private static UltiLogin mockPlugin;
    private static PluginLogger mockLogger;

    /**
     * Set up UltiLogin mock. Must be called before each test.
     */
    @SuppressWarnings("unchecked")
    public static void setUp() throws Exception {
        // Mock UltiLogin (no singleton — plugin instance is injected via @Autowired)
        mockPlugin = mock(UltiLogin.class);

        // Mock logger
        mockLogger = mock(PluginLogger.class);
        lenient().when(mockPlugin.getLogger()).thenReturn(mockLogger);

        // Mock i18n to return the key as-is
        lenient().when(mockPlugin.i18n(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Mock getDataOperator
        lenient().when(mockPlugin.getDataOperator(any()))
                .thenReturn(mock(DataOperator.class));
    }

    /**
     * Clean up state.
     */
    public static void tearDown() throws Exception {
        mockPlugin = null;
    }

    public static UltiLogin getMockPlugin() {
        return mockPlugin;
    }

    public static PluginLogger getMockLogger() {
        return mockLogger;
    }

    /**
     * Bootstrap a live test-time Bukkit server via MockBukkit and install it as {@code Bukkit.server},
     * wrapped in a Mockito spy so per-test {@code doReturn(...).when(server)...}/{@code when(server)...}
     * stubs installed by callers keep working exactly as before. Needed because some production code
     * under test (e.g. {@code PotionEffectType} resolution in {@code LoginService#completeLogin}/
     * {@code #onPlayerJoin}) resolves through mockbukkit-v1.21's real {@code RegistryAccess}, which is
     * only populated once a live server is mocked -- a bare {@code mock(Server.class)} never resolves it.
     * <p>
     * Centralized here rather than duplicated per test class so exactly one entry point exists for
     * "does this module still bootstrap a live server for tests that need one". {@code
     * UltiLoginRegistrySentinelTest} — the reopen guard for this bootstrap — calls this same method
     * instead of its own {@code MockBukkit.mock()}, specifically so that breaking or removing this
     * method's live-server behavior fails the sentinel too, not just whichever consumer test happens
     * to still call it directly (PR #17 review, "the guard does not guard the wiring").
     * <p>
     * Defensive null-out first: {@code Bukkit.setServer()} throws {@code UnsupportedOperationException
     * ("Cannot redefine singleton Server")} if the static field is already non-null, which would
     * otherwise make this method — and therefore every caller — order-dependent on whatever the
     * previous test class in the same Surefire fork happened to leave behind. Test classes that
     * install their own {@code Server} mock directly (bypassing this method) are expected to clear
     * the field back out in their own {@code @AfterEach} — see {@link #clearBukkitServer()} — but
     * this null-out stays here too as a second line of defense: it costs one field write per call,
     * and the alternative is a suite that goes flaky the next time an as-yet-unaudited test class
     * reintroduces the same leak.
     *
     * @return the live server, wrapped in a Mockito spy, already installed as {@code Bukkit.server}
     */
    public static Server bootstrapLiveServer() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);

        MockBukkit.mock();
        Server liveServer = spy(Bukkit.getServer());
        serverField.set(null, liveServer);
        return liveServer;
    }

    /**
     * Tear down the live server installed by {@link #bootstrapLiveServer()}.
     */
    public static void tearDownLiveServer() {
        MockBukkit.unmock();
    }

    /**
     * Null out the static {@code Bukkit.server} field. Call from {@code @AfterEach} in any test class
     * that installs its own {@code Server} mock directly (bypassing {@link #bootstrapLiveServer()}),
     * so the next test class that runs in the same Surefire fork does not inherit a stale singleton.
     * This is the root-cause fix for the leak Codex's review found in three consumer classes
     * ({@code PanelCommandTest}, {@code LoginProtectionListenerTest},
     * {@code EmailVerificationServiceTest}) — each now calls this from its own teardown instead of
     * relying on {@link #bootstrapLiveServer()}'s defensive null-out to paper over the leak on the
     * next class's behalf.
     */
    public static void clearBukkitServer() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    /**
     * Create a default LoginConfig mock with all features enabled.
     */
    public static LoginConfig createDefaultConfig() {
        LoginConfig config = mock(LoginConfig.class);
        lenient().when(config.getLoginTimeout()).thenReturn(60);
        lenient().when(config.isSessionEnabled()).thenReturn(true);
        lenient().when(config.getSessionTimeout()).thenReturn(30);
        lenient().when(config.getMaxRegisterPerIp()).thenReturn(3);
        lenient().when(config.isGuiModeEnabled()).thenReturn(false);
        lenient().when(config.getGuiPasswordLength()).thenReturn(4);
        lenient().when(config.getMinPasswordLength()).thenReturn(6);
        lenient().when(config.getMaxPasswordLength()).thenReturn(32);
        lenient().when(config.getMaxLoginAttempts()).thenReturn(5);
        lenient().when(config.getLockoutDuration()).thenReturn(900);
        lenient().when(config.getLockoutType()).thenReturn("IP");
        lenient().when(config.isSpawnLocationEnabled()).thenReturn(false);
        lenient().when(config.isBlindEffect()).thenReturn(true);

        // Messages
        lenient().when(config.getRegisterSuccess()).thenReturn("&a注册成功！");
        lenient().when(config.getLoginSuccess()).thenReturn("&a登录成功！");
        lenient().when(config.getWrongPassword()).thenReturn("&c密码错误！");
        lenient().when(config.getAlreadyLogged()).thenReturn("&e已经登录了！");
        lenient().when(config.getNotRegistered()).thenReturn("&c未注册！");
        lenient().when(config.getAlreadyRegistered()).thenReturn("&c已经注册过了！");
        lenient().when(config.getPasswordMismatch()).thenReturn("&c密码不一致！");
        lenient().when(config.getPasswordTooShort()).thenReturn("&c密码太短！至少需要 {MIN} 个字符。");
        lenient().when(config.getPasswordTooLong()).thenReturn("&c密码太长！最多 {MAX} 个字符。");
        lenient().when(config.getAccountLocked()).thenReturn("&c账户被锁定！请在 {TIME} 秒后重试。");
        lenient().when(config.getAttemptsRemaining()).thenReturn("&c密码错误！剩余尝试次数: {COUNT}");
        lenient().when(config.getAdminPasswordReset()).thenReturn("&a已重置玩家 {PLAYER} 的密码为: {PASSWORD}");
        lenient().when(config.getAdminAccountNotFound()).thenReturn("&c玩家 {PLAYER} 尚未注册");
        lenient().when(config.getAdminPlayerNotFound()).thenReturn("&c找不到玩家 {PLAYER}");
        lenient().when(config.getAdminForceLogin()).thenReturn("&a已强制登录玩家 {PLAYER}");
        lenient().when(config.getAdminUnregister()).thenReturn("&a已删除玩家 {PLAYER} 的账号");

        // Prompts
        lenient().when(config.getLoginPrompt()).thenReturn("&e请使用 /login <密码> 登录");
        lenient().when(config.getRegisterPrompt()).thenReturn("&e请使用 /register <密码> <确认密码> 注册账号");
        lenient().when(config.getLoginPromptGui()).thenReturn("&e请在弹出的界面中输入密码");
        lenient().when(config.getRegisterPromptGui()).thenReturn("&e请在弹出的界面中设置密码");
        lenient().when(config.getGuiPasswordInvalid()).thenReturn("&c密码必须是 {LENGTH} 位数字！");
        lenient().when(config.getTimeoutKick()).thenReturn("&c登录超时！请重新连接。");
        lenient().when(config.getSpawnWorld()).thenReturn("world");
        lenient().when(config.getSpawnX()).thenReturn(0.0);
        lenient().when(config.getSpawnY()).thenReturn(64.0);
        lenient().when(config.getSpawnZ()).thenReturn(0.0);

        // Allowed commands
        lenient().when(config.getAllowedCommands()).thenReturn(java.util.Arrays.asList("login", "l", "register", "reg"));

        return config;
    }

    /**
     * Create a mock Player with basic properties.
     */
    public static Player createMockPlayer(String name, UUID uuid) {
        Player player = mock(Player.class);
        lenient().when(player.getName()).thenReturn(name);
        lenient().when(player.getUniqueId()).thenReturn(uuid);
        lenient().when(player.hasPermission(anyString())).thenReturn(true);
        lenient().when(player.isOnline()).thenReturn(true);

        // Mock IP address
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 25565);
        lenient().when(player.getAddress()).thenReturn(address);

        return player;
    }

    /**
     * Create a sample AccountData.
     */
    public static AccountData createSampleAccount(UUID playerUuid, String playerName, String passwordHash, String salt) {
        AccountData account = new AccountData();
        account.setPlayerUuid(playerUuid.toString());
        account.setPlayerName(playerName);
        account.setPasswordHash(passwordHash);
        account.setSalt(salt);
        account.setRegisterIp("127.0.0.1");
        account.setLastIp("127.0.0.1");
        account.setRegisterTime(System.currentTimeMillis());
        account.setLastLogin(System.currentTimeMillis());
        account.setLoginCount(5);
        account.setFailedAttempts(0);
        account.setId(String.valueOf(playerUuid.hashCode()));
        return account;
    }

    // --- Reflection ---

    public static void setStaticField(Class<?> clazz, String fieldName, Object value)
            throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    public static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
