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

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
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

    /** Bodies the create requests carried, in order. */
    private final List<JsonObject> createdBodies = new ArrayList<>();
    /** Poll responses still to be served, in order; an empty script answers {@code pending}. */
    private final Deque<String> pollScript = new ArrayDeque<>();
    /** One captured poll runnable per {@code startAuthPolling} call, in order. */
    private final List<Runnable> pollRunnables = new ArrayList<>();
    /** The task handed back for each captured poll, in the same order. */
    private final List<BukkitTask> pollTasks = new ArrayList<>();

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

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);
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
    }

    @AfterEach
    void tearDown() throws Exception {
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

    // ==================== Test 5: catalogue parity ====================

    private static final List<String> NEW_KEYS = Arrays.asList(
            "panel_outcome_cancelled",
            "panel_outcome_expired",
            "panel_outcome_refused",
            "panel_outcome_refused_identity_bound_elsewhere",
            "panel_outcome_refused_game_login_required",
            "panel_outcome_refused_link_not_permitted",
            "panel_outcome_refused_too_many_attempts");

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
