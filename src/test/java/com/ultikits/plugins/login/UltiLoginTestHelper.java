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
     * stubs installed by callers keep working exactly as before.
     * <p>
     * <b>What the live server is actually for.</b> {@code ServerMock} supplies the real
     * {@code getPluginManager()} and {@code getScheduler()} wiring that consumer tests would
     * otherwise hand-stub, and it is what makes item construction work:
     * {@code new ItemStack(Material.DIAMOND)} routes through
     * {@code Material.asItemType()} into mockbukkit's {@code RegistryMock.loadIfEmpty}, which
     * throws under a bare {@code mock(Server.class)} (where {@code Bukkit.getUnsafe()} is
     * {@code null}).
     * <p>
     * <b>Why this also loads a plugin named {@code UltiTools}.</b> {@code ServerMock} supplies a
     * real {@code PluginManagerMock}, but that manager starts with <i>no plugins loaded</i>, so
     * {@code Bukkit.getPluginManager().getPlugin("UltiTools")} returns {@code null} — measured
     * directly against this module's test classpath, before and after. An earlier revision of this
     * javadoc credited {@code ServerMock} with supplying that lookup; it does not, and six
     * production sites in this module resolve their scheduler owner through it
     * ({@code LoginService}, {@code PanelCommand}, {@code LoginProtectionListener},
     * {@code EmailVerificationService}, {@code LoginGUIPage}, {@code RegisterGUIPage}).
     * <p>
     * Loading the plugin, rather than stubbing the single lookup a consumer happens to make, is
     * what removes the class of defect: a fixture whose plugin manager actually has the framework
     * plugin in it cannot go stale one consumer at a time. It also matters that the failure would
     * be <i>silent</i> here — MockBukkit's scheduler accepts a {@code null} owner without
     * complaint, whereas real Paper's {@code CraftScheduler.validate} rejects it with
     * {@code IllegalArgumentException("Plugin cannot be null")} (verified by disassembling
     * {@code org.bukkit.craftbukkit.scheduler.CraftScheduler} from a Paper 1.21.4 server jar), so
     * the fixture would not surface the difference on its own.
     * <p>
     * The name must match the framework's {@code plugin.yml} {@code name: UltiTools} exactly. See
     * UltiKits/UltiEssentials#15 for the same shape as a live production defect, where two files
     * look up {@code "UltiTools-API"} and therefore always get {@code null}.
     * <p>
     * <b>What it is not for.</b> An earlier revision of this javadoc claimed
     * {@code PotionEffectType} "resolves through mockbukkit-v1.21's real {@code RegistryAccess},
     * which is only populated once a live server is mocked -- a bare {@code mock(Server.class)}
     * never resolves it". That is measurably false. With only {@code mock(Server.class)} installed
     * — indeed with no server installed at all — {@code PotionEffectType.BLINDNESS} resolves and
     * {@code new PotionEffect(BLINDNESS, 100, 1)} constructs. Drop
     * {@code mockbukkit-v1.21-4.101.0.jar} from the classpath and the same code throws
     * {@code ExceptionInInitializerError}. That jar ships
     * {@code META-INF/services/io.papermc.paper.registry.RegistryAccess}, so <b>the classpath
     * dependency is what fixes {@code PotionEffectType}; this bootstrap is not.</b>
     * <p>
     * The distinction that holds generally: <i>registry constant resolution</i>
     * ({@code PotionEffectType.X}, {@code Material.X}, {@code Sound.X} resolving, class
     * initialization succeeding) comes from the classpath {@code ServiceLoader} provider and needs
     * no server; <i>item construction</i> ({@code new ItemStack(Material.X)}, real
     * {@code ItemMeta}) needs a live one.
     * <p>
     * Centralized here rather than duplicated per test class so exactly one entry point exists for
     * "does this module still bootstrap a live server for tests that need one". {@code
     * UltiLoginRegistrySentinelTest} — the reopen guard for this bootstrap — calls this same method
     * instead of its own {@code MockBukkit.mock()}, specifically so that breaking or removing this
     * method's live-server behavior fails the sentinel too, not just whichever consumer test happens
     * to still call it directly (PR #17 review, "the guard does not guard the wiring").
     * <p>
     * Defensive reset first, and it takes <b>two</b> statements because MockBukkit has two
     * independent guards, not one:
     * <ul>
     *   <li>{@code MockBukkit.mock(T)} throws {@code IllegalStateException("Already mocking")} when
     *       MockBukkit's own static {@code mock} field is non-null. Only {@code MockBukkit.unmock()}
     *       clears that field.</li>
     *   <li>{@code Bukkit.setServer()}, which {@code MockBukkit.mock(T)} calls immediately
     *       afterwards, throws {@code UnsupportedOperationException("Cannot redefine singleton
     *       Server")} when the static {@code Bukkit.server} field is non-null.</li>
     * </ul>
     * Nulling {@code Bukkit.server} clears only the second, so it does not on its own deliver the
     * order-independence this method exists to provide. Concretely: a class that bootstraps through
     * this method and then tears down with {@link #clearBukkitServer()} — which that method's own
     * javadoc invites — leaves {@code Bukkit.server} null but MockBukkit's {@code mock} field
     * non-null, and the next {@code bootstrapLiveServer()} in a different Surefire-fork class dies
     * in {@code @BeforeEach} with {@code Already mocking}. That is the same order-dependence
     * reached through the other guard. {@code MockBukkit.unmock()} returns immediately when nothing
     * is mocked, so calling it unconditionally is safe and costs nothing on the common path.
     * <p>
     * Test classes that install their own {@code Server} mock directly (bypassing this method) are
     * still expected to clear the field back out in their own {@code @AfterEach} — see
     * {@link #clearBukkitServer()} — but both resets stay here as a second line of defense, because
     * the alternative is a suite that goes flaky the next time an as-yet-unaudited test class
     * reintroduces the same leak.
     *
     * @return the live server, wrapped in a Mockito spy, already installed as {@code Bukkit.server}
     */
    public static Server bootstrapLiveServer() throws Exception {
        MockBukkit.unmock();

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);

        MockBukkit.mock();

        // A bare ServerMock has no plugins loaded, so getPlugin("UltiTools") would return null and
        // every production site that resolves its scheduler owner that way would hold null. See
        // this method's javadoc for the measurement and for why loading beats stubbing.
        MockBukkit.createMockPlugin("UltiTools");

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
     * <p>
     * This is <b>not</b> the teardown for {@link #bootstrapLiveServer()}. Use
     * {@link #tearDownLiveServer()} there: this method clears {@code Bukkit.server} only, leaving
     * MockBukkit's own {@code mock} field set, which used to leave the next class's bootstrap
     * throwing {@code IllegalStateException("Already mocking")}.
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
