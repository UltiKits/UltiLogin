package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkCreation;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkEventHandler;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkFailure;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkProof;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.utils.SimpleHttpClient;
import com.ultikits.ultitools.utils.UltiCloudRequests;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /panel} on the framework's player-link API: what the create carries, the per-address precheck, every
 * failure, the invalidation fences, and the login-timeout pause while a link is pending.
 * <p>
 * Links are created through a fake {@link PlayerLinkPort}; main-thread callbacks run inline. The framework's
 * {@link UltiCloudRequests} and {@link SimpleHttpClient} are statically mocked, and every test ends by proving neither
 * was called: the module makes no HTTP request of its own.
 */
@DisplayName("LoginService /panel creation, fences and timeout pause")
class LoginServicePanelTest {

    private static final String LINK_URL = "https://panel.example/link/";

    private LoginService service;
    private LoginConfig config;
    @SuppressWarnings("unchecked")
    private final DataOperator<AccountData> dataOperator = mock(DataOperator.class);
    @SuppressWarnings("unchecked")
    private final Query<AccountData> mockQuery = mock(Query.class);

    private Server server;
    private Player player;
    private UUID playerUuid;

    private MockedStatic<UltiTools> ultiTools;
    private MockedStatic<SimpleHttpClient> http;
    private MockedStatic<UltiCloudRequests> cloud;

    /** The service's clock, in milliseconds; tests move it with {@link #advance(int)}. */
    private final long[] now = {1_000_000_000L};

    private FakePort port;
    private BukkitScheduler scheduler;

    /** One recorded create. */
    private static final class Create {
        private final UUID uuid;
        private final String name;
        private final boolean authenticated;
        private final boolean accountExists;
        private final Set<LinkProof> proofs;

        private Create(UUID uuid, String name, boolean authenticated, boolean accountExists, Set<LinkProof> proofs) {
            this.uuid = uuid;
            this.name = name;
            this.authenticated = authenticated;
            this.accountExists = accountExists;
            this.proofs = proofs;
        }
    }

    /** A fake player-link API: records every create and answers it with {@link #answer}. */
    private final class FakePort implements PlayerLinkPort {
        private final List<Create> creates = new ArrayList<>();
        private Runnable duringCreate = () -> { };
        private Supplier<CompletableFuture<LinkCreation>> answer = this::created;
        private int next = 1;

        CompletableFuture<LinkCreation> created() {
            return createdExpiringIn(300);
        }

        CompletableFuture<LinkCreation> createdExpiringIn(long seconds) {
            String id = "L" + next++;
            return CompletableFuture.completedFuture(LinkCreation.created(id, LINK_URL + id,
                    Instant.ofEpochMilli(now[0] + seconds * 1000L), EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT)));
        }

        @Override
        public CompletableFuture<LinkCreation> create(UUID playerUuid, String playerName, boolean authenticated,
                                                      boolean accountExists, Set<LinkProof> offeredProofs) {
            creates.add(new Create(playerUuid, playerName, authenticated, accountExists, offeredProofs));
            duringCreate.run();
            return answer.get();
        }

        @Override
        public boolean registerHandler(LinkEventHandler handler) {
            return true;
        }

        @Override
        public void unregisterHandler() {
            // no handler is installed in these tests
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        server = UltiLoginTestHelper.bootstrapLiveServer();
        UltiLoginTestHelper.setUp();

        config = UltiLoginTestHelper.createDefaultConfig();
        when(config.isUlticloudEnabled()).thenReturn(true);
        when(config.getMaxRegisterPerIp()).thenReturn(0);

        when(UltiLoginTestHelper.getMockPlugin().getDataOperator(AccountData.class)).thenReturn(dataOperator);
        when(dataOperator.query()).thenReturn(mockQuery);
        when(dataOperator.updateCounted(any(AccountData.class))).thenReturn(1);
        when(mockQuery.where(anyString())).thenReturn(mockQuery);
        when(mockQuery.and(anyString())).thenReturn(mockQuery);
        when(mockQuery.eq(any())).thenReturn(mockQuery);
        when(mockQuery.orderBy(anyString())).thenReturn(mockQuery);
        when(mockQuery.orderByDesc(anyString())).thenReturn(mockQuery);
        when(mockQuery.limit(anyInt())).thenReturn(mockQuery);

        service = new LoginService(UltiLoginTestHelper.getMockPlugin(), config);
        service.setClock(() -> now[0]);
        port = new FakePort();
        service.setPlayerLinkPort(port);

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        doReturn(player).when(server).getPlayer(playerUuid);
        setRegistered(true);

        scheduler = spy(server.getScheduler());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        doReturn(scheduler).when(server).getScheduler();

        ultiTools = mockStatic(UltiTools.class);
        http = mockStatic(SimpleHttpClient.class);
        cloud = mockStatic(UltiCloudRequests.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            // D-04: the module makes no HTTP request to UltiCloud, in any test.
            http.verifyNoInteractions();
            cloud.verifyNoInteractions();
        } finally {
            cloud.close();
            http.close();
            ultiTools.close();
            UltiLoginTestHelper.tearDown();
            UltiLoginTestHelper.tearDownLiveServer();
        }
    }

    // ==================== fixture helpers ====================

    private void setRegistered(boolean registered) {
        if (registered) {
            AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
            when(mockQuery.list()).thenReturn(Collections.singletonList(account));
        } else {
            when(mockQuery.list()).thenReturn(Collections.emptyList());
        }
    }

    private AccountData registerWith(String passwordHash, String salt) {
        AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", passwordHash, salt);
        when(mockQuery.list()).thenReturn(Collections.singletonList(account));
        return account;
    }

    /** One other account registered from the player's address (127.0.0.1); the player's own lookup stays empty. */
    @SuppressWarnings("unchecked")
    private void oneAccountAlreadyFromThisAddress() {
        AccountData other = UltiLoginTestHelper.createSampleAccount(UUID.randomUUID(), "Other", "h", "s");
        when(mockQuery.where("register_ip")).thenAnswer(invocation -> {
            Query<AccountData> byIp = mock(Query.class);
            when(byIp.eq(any())).thenReturn(byIp);
            when(byIp.list()).thenReturn(Collections.singletonList(other));
            return byIp;
        });
    }

    /** {@code /panel} as the command's worker runs it: publish the link and return its id. */
    private String openLink() {
        LoginService.PanelLinkResult result = service.requestPanelLink(player);
        assertThat(result.isSuccess()).as("the link was published: %s", result.getError()).isTrue();
        service.announcePanelPause(player);
        return result.getLinkId();
    }

    private void advance(int seconds) {
        now[0] += seconds * 1000L;
    }

    private void failWith(LinkFailure failure) {
        port.answer = () -> CompletableFuture.completedFuture(LinkCreation.failed(failure, "reason-" + failure));
    }

    private void markLoggedIn() throws Exception {
        @SuppressWarnings("unchecked")
        Map<UUID, Boolean> loggedIn = (Map<UUID, Boolean>) field("loggedInPlayers");
        loggedIn.put(playerUuid, true);
    }

    private Object field(String name) throws Exception {
        java.lang.reflect.Field field = LoginService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(service);
    }

    /** The player saw {@code key}'s text exactly {@code count} times, and it was looked up that often. */
    private void assertMessage(String key, int count) {
        verify(UltiLoginTestHelper.getMockPlugin(), times(count)).i18n(key);
        String text = ChatColor.translateAlternateColorCodes('&', CatalogueText.text("zh", key));
        verify(player, times(count)).sendMessage(text);
    }

    private String loggedFailure(String reason) {
        return CatalogueText.text("zh", "log_panel_link_failed").replace("{ERROR}", reason);
    }

    // ==================== what the create carries ====================

    @Nested
    @DisplayName("create request facts and offered proofs")
    class CreateFacts {

        @ParameterizedTest(name = "logged in {0}, has account {1} -> {2}")
        @CsvSource({
                "true,  true,  IN_GAME_SESSION LINKED_WEB_ACCOUNT",
                "false, true,  LINKED_WEB_ACCOUNT",
                "false, false, LINKED_WEB_ACCOUNT WEB_REGISTRATION",
                "true,  false, IN_GAME_SESSION LINKED_WEB_ACCOUNT WEB_REGISTRATION"
        })
        @DisplayName("the two facts equal isLoggedIn and isRegistered, and the offered proofs follow the table")
        void proofTable(boolean loggedIn, boolean hasAccount, String expected) throws Exception {
            setRegistered(hasAccount);
            if (loggedIn) {
                markLoggedIn();
            }

            LoginService.PanelLinkResult result = service.requestPanelLink(player);

            assertThat(result.isSuccess()).isTrue();
            assertThat(port.creates).hasSize(1);
            Create create = port.creates.get(0);
            assertThat(create.uuid).isEqualTo(playerUuid);
            assertThat(create.name).isEqualTo("TestPlayer");
            assertThat(create.authenticated).isEqualTo(service.isLoggedIn(playerUuid)).isEqualTo(loggedIn);
            assertThat(create.accountExists).isEqualTo(service.isRegistered(playerUuid)).isEqualTo(hasAccount);
            Set<LinkProof> proofs = EnumSet.noneOf(LinkProof.class);
            for (String name : expected.trim().split("\\s+")) {
                proofs.add(LinkProof.valueOf(name));
            }
            assertThat(create.proofs).isEqualTo(proofs);
        }

        @Test
        @DisplayName("web registration is never offered for a name that already has an account (T-01-05)")
        void webRegistrationNeverOfferedWithAnAccount() {
            assertThat(LoginService.offeredProofs(true, true)).doesNotContain(LinkProof.WEB_REGISTRATION);
            assertThat(LoginService.offeredProofs(false, true)).doesNotContain(LinkProof.WEB_REGISTRATION);
        }

        @Test
        @DisplayName("a published link is pending under the gateway's link id, not under a local id")
        void publishedUnderTheLinkId() {
            LoginService.PanelLinkResult result = service.requestPanelLink(player);

            assertThat(result.getLinkId()).isEqualTo("L1");
            assertThat(result.getUrl()).isEqualTo(LINK_URL + "L1");
            assertThat(service.isPanelRequestCurrent(playerUuid, "L1", service.getInvalidationGeneration(playerUuid)))
                    .isTrue();
            @SuppressWarnings("unchecked")
            Map<String, UUID> pending = (Map<String, UUID>) fieldOrFail("pendingPanelRequests");
            assertThat(pending).containsOnlyKeys("L1");
        }

        private Object fieldOrFail(String name) {
            try {
                return field(name);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    // ==================== the per-address precheck ====================

    @Nested
    @DisplayName("max-register-per-ip precheck")
    class IpPrecheck {

        @Test
        @DisplayName("a player without an account from an address at the limit is refused locally: no create, nothing pending, no pause")
        void refusedAtTheLimit() {
            setRegistered(false);
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();
            service.onPlayerJoin(player);

            LoginService.PanelLinkResult result = service.requestPanelLink(player);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailure()).isEqualTo(LoginService.PanelLinkResult.Failure.IP_LIMIT);
            assertThat(port.creates).as("no link was requested").isEmpty();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            @SuppressWarnings("unchecked")
            Map<UUID, Long> paused = (Map<UUID, Long>) fieldOrFail();
            assertThat(paused).as("the countdown never paused").doesNotContainKey(playerUuid);
            verify(UltiLoginTestHelper.getMockPlugin(), never()).i18n("panel_timeout_paused");
        }

        @Test
        @DisplayName("control: with the limit at 0 the same player gets a link")
        void unlimitedGetsALink() {
            setRegistered(false);
            oneAccountAlreadyFromThisAddress();

            assertThat(service.requestPanelLink(player).isSuccess()).isTrue();
            assertThat(port.creates).hasSize(1);
        }

        @Test
        @DisplayName("a player who has an account is never counted against the limit")
        void accountHolderIsNotPrechecked() {
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();

            assertThat(service.requestPanelLink(player).isSuccess()).isTrue();
        }

        private Object fieldOrFail() {
            try {
                return field("panelPausedAt");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    // ==================== failures ====================

    @Nested
    @DisplayName("a create that produces no link")
    class CreateFailures {

        @ParameterizedTest(name = "{0} -> {1}, logged {2}")
        @CsvSource({
                "NOT_CONNECTED, LINK_ERROR,    true",
                "RATE_LIMITED,  RATE_LIMITED,  false",
                "NOT_PERMITTED, NOT_PERMITTED, false",
                "TIMED_OUT,     GENERIC,       true",
                "OTHER,         GENERIC,       true"
        })
        @DisplayName("each failure has its own result, is logged once where the plan says, and leaves nothing pending")
        void failureMapping(LinkFailure failure, LoginService.PanelLinkResult.Failure expected, boolean logged) {
            failWith(failure);

            LoginService.PanelLinkResult result = service.requestPanelLink(player);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailure()).isEqualTo(expected);
            assertThat(result.getLinkId()).isNull();
            assertThat(result.getUrl()).isNull();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            verify(UltiLoginTestHelper.getMockLogger(), times(logged ? 1 : 0)).warn(loggedFailure("reason-" + failure));
            verify(UltiLoginTestHelper.getMockLogger(), times(logged ? 1 : 0)).warn(anyString());
        }

        @Test
        @DisplayName("a create that never answers ends at the local cap as a timeout: generic failure, logged, nothing pending")
        void createThatNeverAnswersTimesOut() {
            service.setPanelCreateCap(50L);
            port.answer = CompletableFuture::new;

            LoginService.PanelLinkResult result = service.requestPanelLink(player);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailure()).isEqualTo(LoginService.PanelLinkResult.Failure.GENERIC);
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            verify(UltiLoginTestHelper.getMockLogger(), times(1)).warn(loggedFailure("no answer within 50 ms"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(LinkFailure.class)
        @DisplayName("every failure restores the countdown with exactly the time that remained, without a pause or resume line")
        void failureResumesTheRemainingCountdownSilently(LinkFailure failure) {
            service.onPlayerJoin(player);
            advance(55);
            failWith(failure);
            port.duringCreate = () -> advance(3);

            assertThat(service.requestPanelLink(player).isSuccess()).isFalse();

            service.checkTimeouts();
            advance(2);
            now[0]++; // Just past the original 60 s deadline: the 3 s pause must be credited.
            service.checkTimeouts();
            verify(player, never()).kickPlayer(anyString());

            now[0] += 2_999; // Exactly 63 s after joining: timeout uses a strict greater-than check.
            service.checkTimeouts();
            verify(player, never()).kickPlayer(anyString());

            now[0]++; // The first millisecond past the deadline shifted by exactly 3 s.
            service.checkTimeouts();

            verify(player, times(1)).kickPlayer(anyString());
            verify(UltiLoginTestHelper.getMockPlugin(), never()).i18n("panel_timeout_paused");
            verify(UltiLoginTestHelper.getMockPlugin(), never()).i18n("panel_timeout_resumed");
        }

        @Test
        @DisplayName("a failure cleans only its own request: an earlier link stays pending and current")
        void failureCleansOnlyItsOwnRequest() {
            String first = openLink();
            failWith(LinkFailure.NOT_CONNECTED);

            LoginService.PanelLinkResult second = service.requestPanelLink(player);

            assertThat(second.isSuccess()).isFalse();
            assertThat(service.isPanelRequestCurrent(playerUuid, first, service.getInvalidationGeneration(playerUuid)))
                    .as("the earlier link is untouched").isTrue();
        }
    }

    // ==================== fences ====================

    @Nested
    @DisplayName("invalidation and quit fences (T-01-04)")
    class Fences {

        @Test
        @DisplayName("an admin reset while the create is in flight: no link delivered and nothing pending")
        void resetWhileCreateInFlight() {
            long generation = service.getInvalidationGeneration(playerUuid);
            port.duringCreate = () -> service.invalidateSession(playerUuid, false);

            LoginService.PanelLinkResult result = service.requestPanelLink(player, generation);

            assertThat(port.creates).hasSize(1);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("invalidated");
            assertThat(result.getUrl()).isNull();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("an invalidation captured before the worker ran: refused before any create")
        void invalidationBeforeTheWorker() {
            long generation = service.getInvalidationGeneration(playerUuid);
            service.invalidateSession(playerUuid, false);

            LoginService.PanelLinkResult result = service.requestPanelLink(player, generation);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("invalidated");
            assertThat(port.creates).isEmpty();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("an invalidation after the create succeeded but before the main-thread callback: the link is no longer current")
        void invalidationBeforeTheCallback() {
            long generation = service.getInvalidationGeneration(playerUuid);
            LoginService.PanelLinkResult result = service.requestPanelLink(player, generation);
            assertThat(result.isSuccess()).isTrue();

            service.invalidateSession(playerUuid, false);

            assertThat(service.isPanelRequestCurrent(playerUuid, result.getLinkId(), generation)).isFalse();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            verify(UltiLoginTestHelper.getMockPlugin(), never()).i18n("panel_timeout_paused");
        }

        @Test
        @DisplayName("a quit while the create is in flight: no link delivered and nothing pending")
        void quitWhileCreateInFlight() {
            port.duringCreate = () -> service.onPlayerQuit(player);

            LoginService.PanelLinkResult result = service.requestPanelLink(player);

            assertThat(result.isSuccess()).isFalse();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("a quit drops the pending link, so it is no longer current and a completion logs nobody in")
        void quitDropsThePendingLink() {
            String link = openLink();

            service.onPlayerQuit(player);

            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertThat(service.isPanelRequestCurrent(playerUuid, link, service.getInvalidationGeneration(playerUuid)))
                    .isFalse();
            CompletableFuture<PlayerLinkPort.CompletionResult> answer = service.onLinkCompleted(
                    new PlayerLinkPort.LinkCompletion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, playerUuid,
                            "TestPlayer", false, null));
            assertThat(answer).isCompletedWithValue(PlayerLinkPort.CompletionResult.NOT_REQUESTED);
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("a second /panel while the first link is pending keeps both pending and the countdown paused until neither holds")
        void secondPanelKeepsBothPending() throws Exception {
            service.onPlayerJoin(player);
            advance(55);
            String first = openLink();
            advance(10);
            String second = openLink();

            long generation = service.getInvalidationGeneration(playerUuid);
            assertThat(first).isNotEqualTo(second);
            assertThat(service.isPanelRequestCurrent(playerUuid, first, generation)).isTrue();
            assertThat(service.isPanelRequestCurrent(playerUuid, second, generation)).isTrue();
            assertMessage("panel_timeout_paused", 1);

            advance(100);
            service.checkTimeouts();
            verify(player, never()).kickPlayer(anyString());

            // Ending the second link alone (a refused completion) leaves the first one holding the countdown.
            setRegistered(false);
            assertThat(service.onLinkCompleted(new PlayerLinkPort.LinkCompletion("C2", second,
                    LinkProof.LINKED_WEB_ACCOUNT, playerUuid, "TestPlayer", false, null)))
                    .isCompletedWithValue(PlayerLinkPort.CompletionResult.NOT_REQUESTED);
            @SuppressWarnings("unchecked")
            Map<UUID, Long> paused = (Map<UUID, Long>) field("panelPausedAt");
            assertThat(paused).containsKey(playerUuid);
            verify(UltiLoginTestHelper.getMockPlugin(), never()).i18n("panel_timeout_resumed");

            assertThat(service.onLinkCompleted(new PlayerLinkPort.LinkCompletion("C1", first,
                    LinkProof.LINKED_WEB_ACCOUNT, playerUuid, "TestPlayer", false, null)))
                    .isCompletedWithValue(PlayerLinkPort.CompletionResult.NOT_REQUESTED);
            assertThat(paused).doesNotContainKey(playerUuid);
            assertMessage("panel_timeout_resumed", 1);
        }
    }

    // ==================== local expiry at expiresAt (D-10) ====================

    @Nested
    @DisplayName("a link ends locally at its clamped expiresAt")
    class LocalExpiry {

        private void assertKicked(int times) {
            verify(player, times(times)).kickPlayer(anyString());
        }

        @Test
        @DisplayName("expiresAt = now + 300 s: pending at 299 s, ended at 301 s with one expiry line, countdown resumes with the time that was left")
        void endsAtExpiresAt() {
            service.onPlayerJoin(player);
            advance(55);
            openLink();                       // expires at 355 s; 5 s of the countdown were left

            advance(299);                     // 354 s
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isTrue();
            assertKicked(0);

            advance(2);                       // 356 s
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertMessage("panel_outcome_expired", 1);
            assertMessage("panel_timeout_resumed", 1);
            assertKicked(0);

            advance(4);                       // 4 s after the resume
            service.checkTimeouts();
            assertKicked(0);

            advance(2);                       // 6 s after the resume: past the 5 s that were left
            service.checkTimeouts();
            assertKicked(1);
            assertMessage("panel_outcome_expired", 1);
        }

        @Test
        @DisplayName("expiresAt in the past counts as now + 60 s")
        void pastExpiryIsClampedToSixtySeconds() {
            port.answer = () -> port.createdExpiringIn(-10);
            openLink();

            advance(59);
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isTrue();

            advance(2);
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertMessage("panel_outcome_expired", 1);
        }

        @Test
        @DisplayName("expiresAt two hours ahead counts as now + 600 s")
        void farExpiryIsClampedToTenMinutes() {
            port.answer = () -> port.createdExpiringIn(7200);
            openLink();

            advance(599);
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isTrue();

            advance(2);
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertMessage("panel_outcome_expired", 1);
        }

        @Test
        @DisplayName("a request whose create has not answered ends at the 45-second local cap, silently, and its late answer is discarded")
        void inFlightCreateEndsAtTheLocalCap() throws Exception {
            CompletableFuture<LinkCreation> inFlight = new CompletableFuture<>();
            CountDownLatch created = new CountDownLatch(1);
            port.answer = () -> inFlight;
            port.duringCreate = created::countDown;
            AtomicReference<LoginService.PanelLinkResult> result = new AtomicReference<>();
            Thread worker = new Thread(() -> result.set(service.requestPanelLink(player)), "panel-test-worker");
            worker.start();
            assertThat(created.await(10, TimeUnit.SECONDS)).as("the worker reached the create").isTrue();

            advance(44);
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isTrue();

            advance(2);
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();

            inFlight.complete(LinkCreation.created("LATE", LINK_URL + "LATE", Instant.ofEpochMilli(now[0] + 300_000L),
                    EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT)));
            worker.join(10_000L);
            assertThat(worker.isAlive()).as("the worker returned").isFalse();
            assertThat(result.get().isSuccess()).as("the late link is discarded").isFalse();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertMessage("panel_outcome_expired", 0);
        }

        @Test
        @DisplayName("two links: the earlier ends silently, only the player's latest link announces its expiry")
        void onlyTheLatestLinkAnnounces() {
            openLink();                       // A, expires at +300 s
            advance(10);
            openLink();                       // B, expires at +310 s

            advance(291);                     // A is past its expiry, B is not
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("B is still pending").isTrue();
            assertMessage("panel_outcome_expired", 0);

            advance(10);
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertMessage("panel_outcome_expired", 1);
        }

        @Test
        @DisplayName("an earlier link that outlives the latest one still ends silently")
        void earlierLinkOutlivingTheLatestStaysSilent() {
            port.answer = () -> port.createdExpiringIn(600);
            openLink();                       // A, expires at +600 s
            port.answer = () -> port.createdExpiringIn(100);
            openLink();                       // B (latest), expires at +100 s

            advance(101);
            service.checkTimeouts();
            assertMessage("panel_outcome_expired", 1);

            advance(500);
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertMessage("panel_outcome_expired", 1);
        }

        @Test
        @DisplayName("/panel starts no repeating scheduler task")
        void noRepeatingTask() {
            service.onPlayerJoin(player);
            openLink();
            advance(30);
            service.checkTimeouts();

            verify(scheduler, never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class), anyLong(),
                    anyLong());
            verify(scheduler, never()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        }

        @Test
        @DisplayName("shutdown forgets every pending link")
        void shutdownForgetsPendingLinks() {
            openLink();

            service.shutdown();

            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        }
    }

    // ==================== login timeout while a link is pending ====================

    @Nested
    @DisplayName("login timeout while a link is pending")
    class TimeoutPause {

        @Test
        @DisplayName("a pending link holds the countdown: not kicked at 70 s or 290 s; a player without one is kicked at 70 s")
        void pendingLinkIsNeverKicked() {
            UUID otherUuid = UUID.randomUUID();
            Player other = UltiLoginTestHelper.createMockPlayer("Other", otherUuid);
            when(other.getLocation()).thenReturn(new Location(null, 0, 64, 0));
            doReturn(other).when(server).getPlayer(otherUuid);
            service.onPlayerJoin(other);

            service.onPlayerJoin(player);
            advance(55);
            openLink();
            advance(15);
            service.checkTimeouts();

            verify(other, times(1)).kickPlayer(anyString());
            verify(player, never()).kickPlayer(anyString());

            advance(220);
            service.checkTimeouts();

            verify(player, never()).kickPlayer(anyString());
            assertMessage("panel_timeout_paused", 1);
        }

        @Test
        @DisplayName("a logged-in player's link pauses nothing and announces nothing")
        void loggedInPlayerIsNotPaused() {
            service.completeLogin(player);

            openLink();

            verify(UltiLoginTestHelper.getMockPlugin(), never()).i18n("panel_timeout_paused");
        }

        @Test
        @DisplayName("every other unauthenticated restriction stays while the link is pending")
        void restrictionsStayWhileLinkIsPending() {
            service.onPlayerJoin(player);
            advance(55);
            openLink();
            LoginProtectionListener listener = new LoginProtectionListener(UltiLoginTestHelper.getMockPlugin(), service);

            org.bukkit.World world = mock(org.bukkit.World.class);
            Location from = new Location(world, 0, 64, 0);
            PlayerMoveEvent move = new PlayerMoveEvent(player, from, new Location(world, 3, 64, 0));
            listener.onPlayerMove(move);
            assertThat(move.getTo()).as("movement is held").isEqualTo(from);

            AsyncPlayerChatEvent chat = new AsyncPlayerChatEvent(true, player, "hello", new HashSet<>());
            listener.onPlayerChat(chat);
            assertThat(chat.isCancelled()).as("chat is refused").isTrue();

            doReturn(server).when(player).getServer();
            PlayerCommandPreprocessEvent command = new PlayerCommandPreprocessEvent(player, "/spawn");
            listener.onPlayerCommand(command);
            assertThat(command.isCancelled()).as("a command outside allowed-commands is refused").isTrue();

            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }
    }

    // ==================== an account without a game password ====================

    @Nested
    @DisplayName("an account without a game password")
    class EmptyGamePassword {

        @Test
        @DisplayName("Test 8: /login against an empty hash fails closed and says the account was made on the web")
        void loginFailsClosed() {
            registerWith("", "");

            LoginService.LoginResult result = service.login(player, "anything");

            assertThat(result.isSuccess()).isFalse();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertThat(service.getRemainingAttempts(player)).as("not counted as a wrong password").isEqualTo(5);
            verify(UltiLoginTestHelper.getMockPlugin()).i18n("login_no_game_password");
            assertThat(result.getMessage()).isEqualTo(CatalogueText.text("zh", "login_no_game_password"));
        }

        @Test
        @DisplayName("Test 8: a null hash fails closed the same way")
        void loginFailsClosedOnNullHash() {
            registerWith(null, null);

            LoginService.LoginResult result = service.login(player, "anything");

            assertThat(result.isSuccess()).isFalse();
            assertThat(service.getRemainingAttempts(player)).as("not counted as a wrong password").isEqualTo(5);
            verify(UltiLoginTestHelper.getMockPlugin()).i18n("login_no_game_password");
            assertThat(result.getMessage()).isEqualTo(CatalogueText.text("zh", "login_no_game_password"));
        }

        @Test
        @DisplayName("Test 10: /register still refuses a name that has a web-created account")
        void registerRefusesWebAccount() {
            registerWith("", "");

            assertThat(service.register(player, "secret123")).isFalse();
            verify(dataOperator, never()).insert(any(AccountData.class));
        }

        @Test
        @DisplayName("Test 9: setInitialPassword sets a password on an empty hash, after which /login works")
        void setInitialPasswordThenLogin() {
            AccountData account = registerWith("", "");
            assertThat(service.hasNoGamePassword(playerUuid)).isTrue();

            assertThat(service.setInitialPassword(playerUuid, "secret123")).isTrue();

            assertThat(account.getPasswordHash()).isNotEmpty();
            assertThat(account.getSalt()).isNotEmpty();
            assertThat(service.hasNoGamePassword(playerUuid)).isFalse();
            assertThat(service.login(player, "secret123").isSuccess()).as("the new password works").isTrue();
        }

        @Test
        @DisplayName("Test 9: setInitialPassword refuses an account that already has a password and leaves it unchanged")
        void setInitialPasswordRefusesExistingPassword() {
            AccountData account = registerWith("hash", "salt");
            assertThat(service.hasNoGamePassword(playerUuid)).isFalse();

            assertThat(service.setInitialPassword(playerUuid, "secret123")).isFalse();

            assertThat(account.getPasswordHash()).isEqualTo("hash");
            assertThat(account.getSalt()).isEqualTo("salt");
            verify(dataOperator, never()).updateCounted(any(AccountData.class));
        }

        @Test
        @DisplayName("Test 9: setInitialPassword refuses a player without an account")
        void setInitialPasswordRefusesNoAccount() {
            setRegistered(false);

            assertThat(service.hasNoGamePassword(playerUuid)).isFalse();
            assertThat(service.setInitialPassword(playerUuid, "secret123")).isFalse();
        }

        @Test
        @DisplayName("F3: in GUI mode an account without a game password is told to use /panel, not prompted for the number pad")
        void guiModeWebAccountIsPointedAtPanel() {
            when(config.isGuiModeEnabled()).thenReturn(true);
            registerWith("", "");

            service.onPlayerJoin(player);

            verify(player, never()).sendMessage(ChatColor.translateAlternateColorCodes('&', config.getLoginPromptGui()));
            assertMessage("login_no_game_password", 1);
        }

        @Test
        @DisplayName("control: in GUI mode an account with a game password still gets the number-pad prompt")
        void guiModeAccountWithPasswordGetsPadPrompt() {
            when(config.isGuiModeEnabled()).thenReturn(true);
            registerWith("hash", "salt");

            service.onPlayerJoin(player);

            verify(player).sendMessage(ChatColor.translateAlternateColorCodes('&', config.getLoginPromptGui()));
            assertMessage("login_no_game_password", 0);
        }
    }

    // ==================== catalogue parity ====================

    private static final List<String> PANEL_KEYS = Arrays.asList(
            "panel_outcome_expired",
            "panel_timeout_paused",
            "panel_timeout_resumed",
            "panel_link_error",
            "panel_create_rate_limited",
            "panel_create_not_permitted",
            "panel_outcome_refused",
            "panel_link_cancelled",
            "panel_signed_in",
            "panel_web_signin_complete",
            "panel_account_create_failed",
            "panel_late_completion",
            "log_panel_link_handler_unavailable",
            "log_panel_completion_failed",
            "log_web_account_create_failed",
            "panel_web_registration_hint",
            "panel_not_registered_in_game",
            "login_no_game_password",
            "command_setpassword_description",
            "help_setpassword",
            "setpassword_success",
            "setpassword_already_set",
            "setpassword_failed");

    private static final List<String> REMOVED_KEYS = Arrays.asList(
            "panel_credential_refused",
            "log_panel_credential_refused",
            "log_auth_poll_error",
            "panel_auth_success_owner",
            "panel_auth_success_player",
            "panel_outcome_refused_identity_bound_elsewhere",
            "panel_outcome_refused_game_login_required",
            "panel_outcome_refused_link_not_permitted",
            "panel_outcome_refused_too_many_attempts",
            "panel_outcome_cancelled");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z_]+}");

    private static TreeSet<String> placeholders(String text) {
        TreeSet<String> tokens = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    @Test
    @DisplayName("every /panel key exists in en and zh with the same placeholders, and the retired keys are gone from both")
    void panelKeysExistInBothLanguages() {
        Map<String, String> en = CatalogueText.entries("en");
        Map<String, String> zh = CatalogueText.entries("zh");
        for (String key : PANEL_KEYS) {
            assertThat(en).as("lang/en.json").containsKey(key);
            assertThat(zh).as("lang/zh.json").containsKey(key);
            assertThat(placeholders(zh.get(key))).as("placeholders of %s", key).isEqualTo(placeholders(en.get(key)));
        }
        for (String key : REMOVED_KEYS) {
            assertThat(en).as("lang/en.json").doesNotContainKey(key);
            assertThat(zh).as("lang/zh.json").doesNotContainKey(key);
        }
    }
}
