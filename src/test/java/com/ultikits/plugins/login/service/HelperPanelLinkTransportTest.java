package com.ultikits.plugins.login.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.commands.PanelCommand;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.utils.CommonUtils;
import com.ultikits.ultitools.utils.SimpleHttpClient;
import com.ultikits.ultitools.utils.UltiCloudRequests;

import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
 * {@code /panel} through the framework helper {@link UltiCloudRequests} (Phase 18 magic-link contract,
 * sections 2, 7, 12 and 13; plan 21-18): the server's own credential on create and poll is the only path.
 * A server not logged in to UltiCloud, a refused credential and a failed helper call each end the request
 * visibly, and none is retried without the credential.
 * <p>
 * The helper is statically mocked, so no token exists anywhere in this test. The framework's
 * {@link SimpleHttpClient} is statically mocked too, only to prove it is never called. The poll task is captured, not scheduled;
 * asynchronous and main-thread callbacks run inline.
 */
@DisplayName("/panel through the framework helper")
class HelperPanelLinkTransportTest {

    private static final String LINK_URL = "http://panel.test/auth/magic-link?code=ABCDEFGHJKLM";
    private static final String CREATE_OK = "{\"code\":\"200\",\"msg\":\"Success\",\"data\":{\"url\":\""
            + LINK_URL + "\",\"credentialed\":true}}";
    private static final String COMPLETED = "{\"code\":\"200\",\"msg\":\"Success\",\"data\":{\"status\":\"completed\","
            + "\"proof\":\"bound_account\",\"is_server_owner\":false}}";

    private LoginService service;
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

    /** Helper results still to be served for create, in order. */
    private final Deque<UltiCloudRequests.Result> createScript = new ArrayDeque<>();
    /** Helper results still to be served for the poll, in order; empty answers {@code pending}. */
    private final Deque<UltiCloudRequests.Result> pollScript = new ArrayDeque<>();
    /** Bodies the helper's create carried. */
    private final List<JsonObject> helperBodies = new ArrayList<>();
    /** Query maps the helper's poll carried. */
    private final List<Map<String, String>> helperPollQueries = new ArrayList<>();
    /** Bodies a plain (uncredentialed) HTTP create would have carried; must stay empty. */
    private final List<JsonObject> uncredentialedBodies = new ArrayList<>();
    /** Plain HTTP poll answers; only there so a regression fails an assertion, not the network. */
    private final Deque<String> plainPollScript = new ArrayDeque<>();
    /** One captured poll runnable per poll started, in order. */
    private final List<Runnable> pollRunnables = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = UltiLoginTestHelper.bootstrapLiveServer();
        UltiLoginTestHelper.setUp();

        LoginConfig config = UltiLoginTestHelper.createDefaultConfig();
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
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        when(player.spigot()).thenReturn(mock(Player.Spigot.class));
        doReturn(player).when(server).getPlayer(playerUuid);
        AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt");
        when(mockQuery.list()).thenReturn(Collections.singletonList(account));

        BukkitScheduler scheduler = spy(server.getScheduler());
        doAnswer(invocation -> {
            pollRunnables.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        }).when(scheduler).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTaskAsynchronously(any(Plugin.class), any(Runnable.class));
        doReturn(scheduler).when(server).getScheduler();

        YamlConfiguration env = mock(YamlConfiguration.class);
        when(env.getString("api-url")).thenReturn("http://ulticloud.test");
        ultiTools = mockStatic(UltiTools.class);
        ultiTools.when(UltiTools::getEnv).thenReturn(env);
        commonUtils = mockStatic(CommonUtils.class);
        commonUtils.when(CommonUtils::getUltiToolsUUID).thenReturn("server-uuid");

        http = mockStatic(SimpleHttpClient.class);
        http.when(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString())).thenAnswer(invocation -> {
            uncredentialedBodies.add(JsonParser.parseString(invocation.<String>getArgument(2)).getAsJsonObject());
            return new SimpleHttpClient.Response(200, CREATE_OK);
        });
        http.when(() -> SimpleHttpClient.get(anyString())).thenAnswer(invocation -> {
            String next = plainPollScript.poll();
            return new SimpleHttpClient.Response(200, next != null ? next : status("pending"));
        });

        cloud = mockStatic(UltiCloudRequests.class);
        cloud.when(() -> UltiCloudRequests.post(anyString(), anyString())).thenAnswer(invocation -> {
            helperBodies.add(JsonParser.parseString(invocation.<String>getArgument(1)).getAsJsonObject());
            UltiCloudRequests.Result next = createScript.poll();
            return next != null ? next : result(UltiCloudRequests.Outcome.OK, 200, CREATE_OK);
        });
        cloud.when(() -> UltiCloudRequests.get(anyString(), anyMap())).thenAnswer(invocation -> {
            helperPollQueries.add(invocation.getArgument(1));
            UltiCloudRequests.Result next = pollScript.poll();
            return next != null ? next : result(UltiCloudRequests.Outcome.OK, 200, status("pending"));
        });
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

    private static UltiCloudRequests.Result result(UltiCloudRequests.Outcome outcome, int status, String body) {
        UltiCloudRequests.Result result = mock(UltiCloudRequests.Result.class);
        when(result.getOutcome()).thenReturn(outcome);
        when(result.getStatusCode()).thenReturn(status);
        when(result.getBody()).thenReturn(body);
        return result;
    }

    private static UltiCloudRequests.Result noExchange(UltiCloudRequests.Outcome outcome) {
        return result(outcome, -1, null);
    }

    private static String status(String status) {
        return "{\"code\":\"200\",\"msg\":\"Success\",\"data\":{\"status\":\"" + status + "\"}}";
    }

    /** {@code /panel} as the command runs it: publish the link, then start its poll. */
    private String openLink() {
        LoginService.PanelLinkResult published = service.requestPanelLink(player);
        assertThat(published.isSuccess()).as("the link was published: %s", published.getError()).isTrue();
        service.startAuthPolling(playerUuid.toString(), player, published.getLinkId());
        return published.getLinkId();
    }

    private void runLastPoll() {
        assertThat(pollRunnables).as("a poll task was scheduled for the link").isNotEmpty();
        pollRunnables.get(pollRunnables.size() - 1).run();
    }

    private static String text(String key) {
        return ChatColor.translateAlternateColorCodes('&', CatalogueText.entries("zh").getOrDefault(key, key));
    }

    // ==================== Test 1: credentialed create and poll ====================

    @Nested
    @DisplayName("server logged in to UltiCloud")
    class Credentialed {

        @Test
        @DisplayName("create goes through UltiCloudRequests.post and the poll through get(requestId); nothing without the credential")
        void createAndPollThroughHelper() {
            String requestId = openLink();
            pollScript.add(result(UltiCloudRequests.Outcome.OK, 200, COMPLETED));
            runLastPoll();

            cloud.verify(() -> UltiCloudRequests.post(eq("/auth/magic-link"), anyString()), times(1));
            assertThat(helperBodies).hasSize(1);
            assertThat(helperBodies.get(0).get("requestId").getAsString()).isEqualTo(requestId);
            assertThat(helperBodies.get(0).get("playerUuid").getAsString()).isEqualTo(playerUuid.toString());
            cloud.verify(() -> UltiCloudRequests.get("/auth/magic-link/poll",
                    Collections.singletonMap("requestId", requestId)), times(1));
            assertThat(service.isLoggedIn(playerUuid)).as("the credentialed completion logged the player in").isTrue();
            http.verify(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString()), never());
            http.verify(() -> SimpleHttpClient.get(anyString()), never());
        }

        @Test
        @DisplayName("NOT_CONNECTED in the middle of a credentialed poll keeps polling silently, never the legacy poll")
        void notConnectedMidPollKeepsPolling() {
            openLink();
            pollScript.add(noExchange(UltiCloudRequests.Outcome.NOT_CONNECTED));
            runLastPoll();

            assertThat(service.hasPendingPanelRequest(playerUuid)).as("the link is still pending").isTrue();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();

            pollScript.add(result(UltiCloudRequests.Outcome.OK, 200, COMPLETED));
            runLastPoll();

            assertThat(service.isLoggedIn(playerUuid)).as("a later completion still logs in").isTrue();
            cloud.verify(() -> UltiCloudRequests.get(anyString(), anyMap()), times(2));
            http.verify(() -> SimpleHttpClient.get(anyString()), never());
            http.verify(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString()), never());
        }
    }

    // ==================== Test 2: not connected -> visible link error, no request (plan 21-18) ====================

    @Nested
    @DisplayName("server not logged in to UltiCloud")
    class NotConnected {

        @Test
        @DisplayName("NOT_CONNECTED on create says 'link error' on the main thread, cancels the request and sends nothing without the credential")
        void notConnectedFailsVisiblyWithoutAnyRequest() {
            createScript.add(noExchange(UltiCloudRequests.Outcome.NOT_CONNECTED));

            new PanelCommand(UltiLoginTestHelper.getMockPlugin(), service).openPanel(player);

            verify(player).sendMessage(text("panel_link_error"));
            verify(player, never()).sendMessage(text("panel_error"));
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("the request was cancelled").isFalse();
            assertThat(pollRunnables).as("no poll was started").isEmpty();
            cloud.verify(() -> UltiCloudRequests.post(anyString(), anyString()), times(1));
            cloud.verify(() -> UltiCloudRequests.get(anyString(), anyMap()), never());
            assertThat(uncredentialedBodies).as("requests sent without the server's credential").isEmpty();
            http.verify(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString()), never());
            http.verify(() -> SimpleHttpClient.get(anyString()), never());
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }
    }

    // ==================== Tests 3 and 4: refused or failed -> no retry without the credential ====================

    @Nested
    @DisplayName("credential refused or helper failure")
    class NoDowngrade {

        private void runPanelCommand() {
            new PanelCommand(UltiLoginTestHelper.getMockPlugin(), service).openPanel(player);
        }

        @ParameterizedTest(name = "HTTP {0}")
        @ValueSource(ints = {401, 403})
        @DisplayName("a 401 or 403 from create says 'server credential refused', cancels the request, never retries without the credential")
        void refusedCredentialIsNeverDowngraded(int statusCode) {
            String errorCode = statusCode == 401 ? "invalid_credential" : "not_server_owner";
            createScript.add(result(UltiCloudRequests.Outcome.OK, statusCode,
                    "{\"code\":\"" + statusCode + "\",\"msg\":\"refused\",\"error_code\":\"" + errorCode + "\"}"));

            runPanelCommand();

            verify(player).sendMessage(text("panel_credential_refused"));
            verify(player, never()).sendMessage(text("panel_error"));
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("the request was cancelled").isFalse();
            assertThat(pollRunnables).as("no poll was started").isEmpty();
            http.verify(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString()), never());
            http.verify(() -> SimpleHttpClient.get(anyString()), never());
        }

        @ParameterizedTest(name = "HTTP {0}")
        @ValueSource(ints = {429, 500, 503})
        @DisplayName("429 or 5xx from create is a plain failure and is not retried without the credential")
        void rateLimitOrServerErrorIsNotRetried(int statusCode) {
            createScript.add(result(UltiCloudRequests.Outcome.OK, statusCode,
                    "{\"code\":\"" + statusCode + "\",\"msg\":\"busy\"}"));

            runPanelCommand();

            verify(player).sendMessage(text("panel_error"));
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("the request was cancelled").isFalse();
            assertThat(pollRunnables).as("no poll was started").isEmpty();
            http.verify(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString()), never());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = UltiCloudRequests.Outcome.class, names = {"IO_ERROR", "PATH_NOT_ALLOWED"})
        @DisplayName("IO_ERROR or PATH_NOT_ALLOWED on create says 'link error', cancels the request, never retries without the credential")
        void helperFailureIsNotRetried(UltiCloudRequests.Outcome outcome) {
            createScript.add(noExchange(outcome));

            runPanelCommand();

            verify(player).sendMessage(text("panel_link_error"));
            verify(player, never()).sendMessage(text("panel_error"));
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("the request was cancelled").isFalse();
            assertThat(pollRunnables).as("no poll was started").isEmpty();
            http.verify(() -> SimpleHttpClient.post(anyString(), anyMap(), anyString()), never());
        }
    }

    // ==================== plan 21-18: one credentialed path ====================

    @Nested
    @DisplayName("one credentialed path (plan 21-18)")
    class SingleCredentialedPath {

        @Test
        @DisplayName("credentialed() has no default: every transport states whether it carries the server credential")
        void credentialedIsNotDefaulted() throws Exception {
            assertThat(new HelperPanelLinkTransport().credentialed()).as("control: the helper vouches").isTrue();
            assertThat(PanelLinkTransport.class.getMethod("credentialed").isDefault())
                    .as("a default would let a transport be read as uncredentialed (or credentialed) by omission")
                    .isFalse();
        }

        @Test
        @DisplayName("the transport seam declares only the credentialed path's outcomes")
        void seamDeclaresOnlyCredentialedOutcomes() {
            List<String> nested = new ArrayList<>();
            for (Class<?> type : PanelLinkTransport.class.getDeclaredClasses()) {
                nested.add(type.getSimpleName());
            }
            assertThat(nested).as("types nested in PanelLinkTransport")
                    .containsExactlyInAnyOrder("NotConnectedException", "CredentialRefusedException",
                            "LinkFailedException");
        }

        @Test
        @DisplayName("no main source sends a plain HTTP request or reads the API address itself")
        void noRequestWithoutTheCredentialInMainSources() throws IOException {
            // Control first: the same scan finds the helper calls the module is known to make.
            assertThat(scanModuleSources("\\bUltiCloudRequests\\.(post|get)\\("))
                    .as("control: the scan sees the helper calls").hasSize(2);
            assertThat(scanModuleSources(
                    "SimpleHttpClient\\.(post|get|put|delete|request)\\s*\\(|magic-link/poll\\?playerUuid|\"api-url\""))
                    .as("plain HTTP requests or API-address reads in the module").isEmpty();
        }
    }

    // ==================== Test 5: the module never touches the credential ====================

    @Test
    @DisplayName("no class in com.ultikits.plugins.login references CredentialStore, CloudSession or TokenEntity")
    void moduleNeverReadsTheCredential() throws IOException {
        // Control first: the same scan finds a type the module is known to use, so an empty result
        // below means "absent", not "the scan read nothing".
        assertThat(scanModuleSources("\\bSimpleHttpClient\\b")).as("control: the scan sees the module's sources").isNotEmpty();
        assertThat(scanModuleSources("\\b(CredentialStore|CloudSession|TokenEntity)\\b"))
                .as("references to the framework's credential types").isEmpty();
    }

    private static List<String> scanModuleSources(String regex) throws IOException {
        Path root = Paths.get("src", "main", "java", "com", "ultikits", "plugins", "login");
        assertThat(root).as("the module's source tree is where the scan looks").isDirectory();
        Pattern pattern = Pattern.compile(regex);
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(root)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        List<String> hits = new ArrayList<>();
        for (Path source : sources) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    hits.add(source + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        }
        return hits;
    }
}
