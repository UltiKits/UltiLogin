package com.ultikits.plugins.login.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.utils.CommonUtils;
import com.ultikits.ultitools.utils.SimpleHttpClient;
import com.ultikits.ultitools.utils.UltiCloudRequests;

import com.ultikits.plugins.login.listener.LoginProtectionListener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /panel}: the facts the create request carries, the in-game report of every web outcome, and
 * the login-timeout pause while a link is pending (Phase 18 magic-link contract, sections 2, 7 and 13).
 * <p>
 * The poll task is captured rather than scheduled, so each test runs one poll at a time by hand;
 * main-thread callbacks run inline. The HTTP layer is the framework's {@link SimpleHttpClient},
 * statically mocked, so the anonymous transport is the one under test unless a test injects its own.
 */
@DisplayName("LoginService /panel facts, outcomes and timeout pause")
class LoginServicePanelTest {

    private static final String LINK_URL = "http://panel.test/auth/magic-link?code=ABCDEFGHJKLM";
    private static final String CREATE_OK = "{\"code\":\"200\",\"msg\":\"Success\",\"data\":{\"url\":\""
            + LINK_URL + "\"}}";

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
    private MockedStatic<CommonUtils> commonUtils;
    private MockedStatic<SimpleHttpClient> http;
    private MockedStatic<UltiCloudRequests> cloud;

    /** Bodies the create requests carried, in order. */
    private final List<JsonObject> createdBodies = new ArrayList<>();
    /** Poll responses still to be served, in order; an empty script answers {@code pending}. */
    private final Deque<String> pollScript = new ArrayDeque<>();
    /** One captured poll runnable per {@code startAuthPolling} call, in order. */
    private final List<Runnable> pollRunnables = new ArrayList<>();
    /** The task handed back for each captured poll, in the same order. */
    private final List<BukkitTask> pollTasks = new ArrayList<>();

    /** The service's clock, in milliseconds; tests move it with {@link #advance(int)}. */
    private final long[] now = {1_000_000_000L};

    @BeforeEach
    void setUp() throws Exception {
        server = UltiLoginTestHelper.bootstrapLiveServer();
        UltiLoginTestHelper.setUp();

        config = UltiLoginTestHelper.createDefaultConfig();
        when(config.isUlticloudEnabled()).thenReturn(true);

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

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        doReturn(player).when(server).getPlayer(playerUuid);
        setRegistered(true);

        BukkitScheduler scheduler = spy(server.getScheduler());
        doAnswer(invocation -> {
            pollRunnables.add(invocation.getArgument(1));
            BukkitTask task = mock(BukkitTask.class);
            pollTasks.add(task);
            return task;
        }).when(scheduler).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        doReturn(scheduler).when(server).getScheduler();

        YamlConfiguration env = mock(YamlConfiguration.class);
        when(env.getString("api-url")).thenReturn("http://ulticloud.test");
        ultiTools = mockStatic(UltiTools.class);
        ultiTools.when(UltiTools::getEnv).thenReturn(env);
        commonUtils = mockStatic(CommonUtils.class);
        commonUtils.when(CommonUtils::getUltiToolsUUID).thenReturn("server-uuid");
        http = mockStatic(SimpleHttpClient.class);
        http.when(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString())).thenAnswer(invocation -> {
            createdBodies.add(JsonParser.parseString(invocation.<String>getArgument(2)).getAsJsonObject());
            return new SimpleHttpClient.Response(200, CREATE_OK);
        });
        http.when(() -> SimpleHttpClient.get(anyString())).thenAnswer(invocation -> {
            String next = pollScript.poll();
            return new SimpleHttpClient.Response(200, next != null ? next : status("pending"));
        });
        // The server is not logged in to UltiCloud unless a test says otherwise, so /panel falls back
        // to the anonymous request above (HelperPanelLinkTransportTest covers the credentialed one).
        cloud = mockStatic(UltiCloudRequests.class);
        UltiCloudRequests.Result notConnected = mock(UltiCloudRequests.Result.class);
        when(notConnected.getOutcome()).thenReturn(UltiCloudRequests.Outcome.NOT_CONNECTED);
        when(notConnected.getStatusCode()).thenReturn(-1);
        cloud.when(() -> UltiCloudRequests.post(anyString(), anyString())).thenReturn(notConnected);
        cloud.when(() -> UltiCloudRequests.get(anyString(), anyMap())).thenReturn(notConnected);
    }

    @AfterEach
    void tearDown() throws Exception {
        cloud.close();
        http.close();
        commonUtils.close();
        ultiTools.close();
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
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

    private static String status(String status) {
        return "{\"code\":\"200\",\"msg\":\"Success\",\"data\":{\"status\":\"" + status + "\"}}";
    }

    private static String refused(String reason) {
        return "{\"code\":\"200\",\"msg\":\"Success\",\"data\":{\"status\":\"refused\",\"reason\":\"" + reason + "\"}}";
    }

    private static String completed() {
        return "{\"code\":\"200\",\"msg\":\"Success\",\"data\":{\"status\":\"completed\","
                + "\"proof\":\"bound_account\",\"is_server_owner\":false}}";
    }

    /** {@code /panel} as the command runs it: publish the link, then start its poll. */
    private String openLink() {
        LoginService.PanelLinkResult result = service.requestPanelLink(player);
        assertThat(result.isSuccess()).as("the link was published: %s", result.getError()).isTrue();
        service.startAuthPolling(playerUuid.toString(), player, result.getRequestId());
        return result.getRequestId();
    }

    /** Run the most recently started poll once, serving {@code response}. */
    private void pollOnce(String response) {
        pollScript.add(response);
        assertThat(pollRunnables).as("a poll task was scheduled for the link").isNotEmpty();
        pollRunnables.get(pollRunnables.size() - 1).run();
    }

    private void advance(int seconds) {
        now[0] += seconds * 1000L;
    }

    private BukkitTask lastPollTask() {
        return pollTasks.get(pollTasks.size() - 1);
    }

    /** The player saw {@code key}'s text exactly {@code count} times, and it was looked up that often. */
    private void assertMessage(String key, int count) {
        verify(UltiLoginTestHelper.getMockPlugin(), times(count)).i18n(key);
        String text = ChatColor.translateAlternateColorCodes('&',
                CatalogueText.entries("zh").getOrDefault(key, key));
        verify(player, times(count)).sendMessage(text);
    }

    // ==================== Test 1: facts on create, through the transport seam ====================

    @Nested
    @DisplayName("create request facts")
    class CreateFacts {

        @Test
        @DisplayName("a registered player who has not logged in reports inGameLogin=false, registeredInUltiLogin=true")
        void registeredNotLoggedIn() {
            setRegistered(true);

            LoginService.PanelLinkResult result = service.requestPanelLink(player);

            assertThat(createdBodies).hasSize(1);
            JsonObject body = createdBodies.get(0);
            assertThat(body.get("requestId").getAsString()).isEqualTo(result.getRequestId());
            assertThat(body.get("playerUuid").getAsString()).isEqualTo(playerUuid.toString());
            assertThat(body.get("playerName").getAsString()).isEqualTo("TestPlayer");
            assertThat(body.get("serverUuid").getAsString()).isEqualTo("server-uuid");
            assertThat(body.get("inGameLogin")).as("inGameLogin is sent").isNotNull();
            assertThat(body.get("inGameLogin").getAsBoolean()).isFalse();
            assertThat(body.get("registeredInUltiLogin")).as("registeredInUltiLogin is sent").isNotNull();
            assertThat(body.get("registeredInUltiLogin").getAsBoolean()).isTrue();
        }

        @Test
        @DisplayName("a logged-in player without an UltiLogin account reports inGameLogin=true, registeredInUltiLogin=false")
        void loggedInNotRegistered() {
            service.completeLogin(player);
            setRegistered(false);

            service.requestPanelLink(player);

            assertThat(createdBodies).hasSize(1);
            JsonObject body = createdBodies.get(0);
            assertThat(body.get("inGameLogin")).as("inGameLogin is sent").isNotNull();
            assertThat(body.get("inGameLogin").getAsBoolean()).isTrue();
            assertThat(body.get("registeredInUltiLogin")).as("registeredInUltiLogin is sent").isNotNull();
            assertThat(body.get("registeredInUltiLogin").getAsBoolean()).isFalse();
        }

        @Test
        @DisplayName("create and poll go through the injected transport, not straight to HTTP")
        void transportSeamCarriesCreateAndPoll() {
            List<JsonObject> seamBodies = new ArrayList<>();
            List<String> seamPolls = new ArrayList<>();
            service.setPanelLinkTransport(new PanelLinkTransport() {
                @Override
                public SimpleHttpClient.Response create(JsonObject body) {
                    seamBodies.add(body);
                    return new SimpleHttpClient.Response(200, CREATE_OK);
                }

                @Override
                public SimpleHttpClient.Response poll(String requestId, UUID uuid) {
                    seamPolls.add(requestId + "|" + uuid);
                    return new SimpleHttpClient.Response(200, status("pending"));
                }
            });

            String requestId = openLink();
            pollRunnables.get(0).run();

            assertThat(seamBodies).as("the create body reached the transport").hasSize(1);
            assertThat(seamBodies.get(0).get("requestId").getAsString()).isEqualTo(requestId);
            assertThat(seamPolls).containsExactly(requestId + "|" + playerUuid);
            http.verify(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString()), never());
            http.verify(() -> SimpleHttpClient.get(anyString()), never());
        }

        @Test
        @DisplayName("the anonymous transport keeps the legacy create URL and the playerUuid poll")
        void anonymousTransportKeepsLegacyEndpoints() {
            openLink();
            pollOnce(status("pending"));

            http.verify(() -> SimpleHttpClient.post(
                    org.mockito.ArgumentMatchers.eq("http://ulticloud.test/auth/magic-link"), anyMap(), anyString()));
            http.verify(() -> SimpleHttpClient.get(
                    "http://ulticloud.test/auth/magic-link/poll?playerUuid=" + playerUuid));
        }
    }

    // ==================== Tests 2-4: every web outcome in game ====================

    @Nested
    @DisplayName("web outcomes")
    class Outcomes {

        @Test
        @DisplayName("cancelled is reported once, keeps the request pending and polling, and a later completed still logs in")
        void cancelledReportedOnceThenCompletes() {
            openLink();

            pollOnce(status("cancelled"));
            pollOnce(status("cancelled"));
            pollOnce(status("cancelled"));

            assertMessage("panel_outcome_cancelled", 1);
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("a cancelled link stays completable").isTrue();
            verify(lastPollTask(), never()).cancel();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();

            pollOnce(completed());

            assertThat(service.isLoggedIn(playerUuid)).as("completed after cancelled logs in").isTrue();
        }

        @ParameterizedTest(name = "refused / {0}")
        @ValueSource(strings = {"identity_bound_elsewhere", "game_login_required", "link_not_permitted",
                "too_many_attempts"})
        @DisplayName("refused shows its reason, ends the request, stops the poll and never logs in")
        void refusedShowsReasonAndStops(String reason) {
            openLink();

            pollOnce(refused(reason));

            assertMessage("panel_outcome_refused_" + reason, 1);
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            verify(lastPollTask()).cancel();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();

            // A completed observation that was already in flight cannot log the player in either.
            pollOnce(completed());
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("refused with a reason this version does not know still reports a refusal")
        void refusedUnknownReason() {
            openLink();

            pollOnce(refused("some_future_reason"));

            assertMessage("panel_outcome_refused", 1);
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("expired is reported, ends the request, stops the poll and never logs in")
        void expiredReportedAndStops() {
            openLink();

            pollOnce(status("expired"));

            assertMessage("panel_outcome_expired", 1);
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            verify(lastPollTask()).cancel();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @DisplayName("an unknown status keeps polling silently; only completed logs in")
        void unknownStatusKeepsPolling() {
            openLink();

            pollOnce(status("on_hold"));
            pollOnce(status("pending"));
            pollOnce("{\"code\":\"200\",\"msg\":\"Success\",\"data\":{}}");

            verify(UltiLoginTestHelper.getMockPlugin(), never()).i18n(startsWith("panel_outcome_"));
            assertThat(service.hasPendingPanelRequest(playerUuid)).isTrue();
            verify(lastPollTask(), never()).cancel();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();

            pollOnce(completed());

            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            assertMessage("panel_auth_success_player", 1);
        }
    }

    // ==================== Tests 6-10: the login timeout while a link is pending ====================

    /** With login-timeout 60 s: joined 55 s before the link, so 5 s remain when the countdown pauses. */
    @Nested
    @DisplayName("login timeout while a link is pending")
    class TimeoutPause {

        private void joinThenOpenLinkAt55() {
            service.onPlayerJoin(player);
            advance(55);
            openLink();
        }

        private void assertNotKicked() {
            verify(player, never()).kickPlayer(anyString());
        }

        private void assertKickedOnce() {
            verify(player, times(1)).kickPlayer(anyString());
        }

        @Test
        @DisplayName("a pending link holds the countdown: not kicked at 70 s or 300 s; a player without one is kicked at 70 s")
        void pendingLinkIsNeverKicked() {
            UUID otherUuid = UUID.randomUUID();
            Player other = UltiLoginTestHelper.createMockPlayer("Other", otherUuid);
            when(other.getLocation()).thenReturn(new Location(null, 0, 64, 0));
            doReturn(other).when(server).getPlayer(otherUuid);
            service.onPlayerJoin(other);

            joinThenOpenLinkAt55();
            advance(15);
            service.checkTimeouts();

            verify(other, times(1)).kickPlayer(anyString());
            assertNotKicked();

            advance(230);
            service.checkTimeouts();

            assertNotKicked();
            assertMessage("panel_timeout_paused", 1);
        }

        @Test
        @DisplayName("cancelled on the web 120 s into the pause: kicked after the 5 s that remained, not at once and not after a fresh 60 s")
        void cancelResumesWithRemainingTime() {
            joinThenOpenLinkAt55();
            advance(120);
            pollOnce(status("cancelled"));

            service.checkTimeouts();
            advance(4);
            service.checkTimeouts();
            assertNotKicked();

            advance(2);
            service.checkTimeouts();

            assertKickedOnce();
            verify(UltiLoginTestHelper.getMockPlugin(), times(1)).i18n("panel_timeout_resumed");
            verify(lastPollTask(), never()).cancel();
        }

        @Test
        @DisplayName("the link expiring (5-minute cleanup) resumes with the remaining time and says so once")
        void expiryResumesWithRemainingTime() {
            joinThenOpenLinkAt55();
            advance(301);
            service.checkTimeouts();

            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertNotKicked();

            advance(4);
            service.checkTimeouts();
            assertNotKicked();

            advance(2);
            service.checkTimeouts();

            assertKickedOnce();
            assertMessage("panel_timeout_resumed", 1);
            assertMessage("panel_outcome_expired", 1);
        }

        @Test
        @DisplayName("two links in sequence: paused while either holds, resumed only when neither does")
        void pausedWhileAnyLinkHolds() {
            joinThenOpenLinkAt55();          // link A, created at 55 s
            advance(10);
            openLink();                      // link B, created at 65 s; supersedes A's poll, A stays pending
            advance(100);
            pollOnce(refused("too_many_attempts"));   // B ends; A still holds

            service.checkTimeouts();
            advance(150);                    // 315 s after joining
            service.checkTimeouts();
            assertNotKicked();
            verify(UltiLoginTestHelper.getMockPlugin(), never()).i18n("panel_timeout_resumed");

            advance(41);                     // 356 s: A, created at 55 s, is past five minutes and expires
            service.checkTimeouts();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertNotKicked();

            advance(6);
            service.checkTimeouts();

            assertKickedOnce();
            verify(UltiLoginTestHelper.getMockPlugin(), times(1)).i18n("panel_timeout_resumed");
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
            joinThenOpenLinkAt55();
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

    // ==================== Plan 18-17 Tests 6-10: web registration and the empty game password ====================

    /** The server is logged in to UltiCloud: create and poll go through the (mocked) framework helper. */
    private void connectServer() {
        cloud.when(() -> UltiCloudRequests.post(anyString(), anyString()))
                .thenAnswer(invocation -> cloudResult(200, CREATE_OK));
        cloud.when(() -> UltiCloudRequests.get(anyString(), anyMap())).thenAnswer(invocation -> {
            String next = pollScript.poll();
            return cloudResult(200, next != null ? next : status("pending"));
        });
    }

    private static UltiCloudRequests.Result cloudResult(int statusCode, String body) {
        UltiCloudRequests.Result result = mock(UltiCloudRequests.Result.class);
        when(result.getOutcome()).thenReturn(UltiCloudRequests.Outcome.OK);
        when(result.getStatusCode()).thenReturn(statusCode);
        when(result.getBody()).thenReturn(body);
        return result;
    }

    private static String completedWith(String proof) {
        return "{\"code\":\"200\",\"msg\":\"Success\",\"data\":{\"status\":\"completed\","
                + "\"proof\":\"" + proof + "\",\"is_server_owner\":false}}";
    }

    private AccountData registerWith(String passwordHash, String salt) {
        AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", passwordHash, salt);
        when(mockQuery.list()).thenReturn(Collections.singletonList(account));
        return account;
    }

    @Nested
    @DisplayName("web registration (proof 3)")
    class WebRegistration {

        @Test
        @DisplayName("Test 6: completed + web_registration for an unregistered player creates one row with an empty hash, logs in, hints once")
        void createsAccountWithEmptyHash() {
            connectServer();
            setRegistered(false);
            openLink();

            pollOnce(completedWith("web_registration"));

            ArgumentCaptor<AccountData> inserted = ArgumentCaptor.forClass(AccountData.class);
            verify(dataOperator, times(1)).insert(inserted.capture());
            AccountData row = inserted.getValue();
            assertThat(row.getPlayerUuid()).isEqualTo(playerUuid.toString());
            assertThat(row.getPlayerName()).isEqualTo("TestPlayer");
            assertThat(row.getPasswordHash()).as("no game password yet").isEmpty();
            assertThat(row.getRegisterIp()).isEqualTo("127.0.0.1");
            assertThat(service.isLoggedIn(playerUuid)).as("the web registration logged the player in").isTrue();
            assertMessage("panel_auth_success_player", 1);
            assertMessage("panel_web_registration_hint", 1);
        }

        @Test
        @DisplayName("Test 7: the same outcome for a player who registered in game meanwhile leaves the row untouched and logs in, no hint")
        void existingAccountIsNeverOverwritten() {
            connectServer();
            setRegistered(false);
            openLink();
            AccountData existing = registerWith("hash", "salt");

            pollOnce(completedWith("web_registration"));

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(existing.getPasswordHash()).as("the game password is untouched").isEqualTo("hash");
            assertThat(existing.getSalt()).isEqualTo("salt");
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            assertMessage("panel_web_registration_hint", 0);
        }

        @Test
        @DisplayName("a web_registration proof on an anonymous (uncredentialed) link creates nothing and logs nobody in")
        void proofOnAnonymousLinkIsIgnored() {
            setRegistered(false);
            openLink();

            pollOnce(completedWith("web_registration"));

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertMessage("panel_not_registered_in_game", 1);
        }

        @Test
        @DisplayName("completed + bound_account for a player with no game account is refused with 'not registered in game'")
        void boundAccountWithoutGameAccountIsRefused() {
            connectServer();
            setRegistered(false);
            openLink();

            pollOnce(completedWith("bound_account"));

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertMessage("panel_not_registered_in_game", 1);
            assertMessage("panel_auth_success_player", 0);
        }

        @Test
        @DisplayName("web registration respects max-register-per-ip: over the limit nothing is created and nobody logged in")
        void webRegistrationRespectsIpLimit() {
            connectServer();
            setRegistered(false);
            openLink();
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            AccountData other = UltiLoginTestHelper.createSampleAccount(UUID.randomUUID(), "Other", "h", "s");
            when(mockQuery.list()).thenAnswer(invocation -> Collections.emptyList());
            // isRegistered(player) reads player_uuid; the IP count reads register_ip.
            when(mockQuery.where("register_ip")).thenAnswer(invocation -> {
                Query<AccountData> byIp = mock(Query.class);
                when(byIp.eq(any())).thenReturn(byIp);
                when(byIp.list()).thenReturn(Collections.singletonList(other));
                return byIp;
            });

            pollOnce(completedWith("web_registration"));

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            verify(player).sendMessage(ChatColor.RED + CatalogueText.text("zh", "ip_limit_reached"));
        }
    }

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
    }

    // ==================== gate-1 fix round (plan 18-20): quit, invalidation, GUI mode ====================

    @Nested
    @DisplayName("quit, invalidation and GUI mode (gate-1 review of UltiKits/UltiLogin#54)")
    class QuitInvalidationAndGuiMode {

        @Test
        @DisplayName("F1: a quit drops the player's pending links, so a completion still in flight logs nobody in")
        void quitDropsPendingLinks() {
            openLink();

            service.onPlayerQuit(player);
            // The poll call that was already in flight when the player left now returns 'completed'.
            pollOnce(completed());

            assertThat(service.hasPendingPanelRequest(playerUuid)).as("pending links after the quit").isFalse();
            assertThat(service.isLoggedIn(playerUuid)).as("logged in by a link of a player who left").isFalse();
            assertMessage("panel_auth_success_player", 0);
        }

        @Test
        @DisplayName("control: without a quit the same in-flight completion logs the player in")
        void completionWithoutQuitLogsIn() {
            openLink();

            pollOnce(completed());

            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            assertMessage("panel_auth_success_player", 1);
        }

        @Test
        @DisplayName("F4: invalidating a player's credentials also forgets which transport created their pending link")
        @SuppressWarnings("unchecked")
        void invalidationForgetsTheTransport() throws Exception {
            connectServer();
            openLink();
            java.lang.reflect.Field field = LoginService.class.getDeclaredField("panelRequestTransports");
            field.setAccessible(true);
            Map<String, ?> transports = (Map<String, ?>) field.get(service);
            assertThat(transports).as("control: the link's transport is recorded").hasSize(1);

            service.invalidateSession(playerUuid, false);

            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertThat(transports).as("transport entries left behind by the invalidation").isEmpty();
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

    // ==================== Test 5: catalogue parity ====================

    private static final List<String> NEW_KEYS = Arrays.asList(
            "panel_outcome_cancelled",
            "panel_outcome_expired",
            "panel_outcome_refused",
            "panel_outcome_refused_identity_bound_elsewhere",
            "panel_outcome_refused_game_login_required",
            "panel_outcome_refused_link_not_permitted",
            "panel_outcome_refused_too_many_attempts",
            "panel_timeout_paused",
            "panel_timeout_resumed",
            // plan 18-17
            "panel_credential_refused",
            "panel_link_error",
            "log_panel_credential_refused",
            "panel_web_registration_hint",
            "panel_not_registered_in_game",
            "login_no_game_password",
            "command_setpassword_description",
            "help_setpassword",
            "setpassword_success",
            "setpassword_already_set",
            "setpassword_failed");

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
    @DisplayName("every new /panel key exists in en and zh with the same placeholders")
    void newKeysExistInBothLanguages() {
        Map<String, String> en = CatalogueText.entries("en");
        Map<String, String> zh = CatalogueText.entries("zh");
        for (String key : NEW_KEYS) {
            assertThat(en).as("lang/en.json").containsKey(key);
            assertThat(zh).as("lang/zh.json").containsKey(key);
            assertThat(placeholders(zh.get(key))).as("placeholders of %s", key).isEqualTo(placeholders(en.get(key)));
        }
    }
}
