package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.service.PlayerLinkPort.CompletionResult;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkCompletion;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkCreation;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkEventHandler;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkProof;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two link events the framework delivers to UltiLogin's handler: {@code completed}, answered with a result, and
 * {@code expired}. The handler is driven exactly as the framework drives it: it is installed through
 * {@link PlayerLinkPort#registerHandler} when the service initialises, called off the main thread, and its work runs
 * in a main-thread task, which this test queues and runs explicitly ({@link #runMainThread()}).
 */
@DisplayName("LoginService player-link completion and expiry events")
class PlayerLinkEventsTest {

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
    private final long[] now = {1_000_000_000L};
    private FakePort port;
    private final List<Runnable> mainThread = new ArrayList<>();

    /** A fake player-link API: answers every create with a link and keeps the handler the service installs. */
    private final class FakePort implements PlayerLinkPort {
        private LinkEventHandler handler;
        private int registrations;
        private int unregistrations;
        private int next = 1;

        @Override
        public CompletableFuture<LinkCreation> create(UUID uuid, String name, boolean authenticated,
                                                      boolean accountExists, Set<LinkProof> offeredProofs) {
            String id = "L" + next++;
            return CompletableFuture.completedFuture(LinkCreation.created(id, LINK_URL + id,
                    Instant.ofEpochMilli(now[0] + 300_000L), EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT)));
        }

        @Override
        public boolean registerHandler(LinkEventHandler eventHandler) {
            registrations++;
            this.handler = eventHandler;
            return true;
        }

        @Override
        public void unregisterHandler() {
            unregistrations++;
            this.handler = null;
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
        service.registerPlayerLinkHandler();

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        doReturn(player).when(server).getPlayer(playerUuid);
        setAccount("hash", "salt");

        BukkitScheduler scheduler = spy(server.getScheduler());
        doAnswer(invocation -> {
            mainThread.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        doReturn(scheduler).when(server).getScheduler();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    // ==================== fixture helpers ====================

    /** The player's account with this hash and salt, or none for {@code null}. */
    private AccountData setAccount(String hash, String salt) {
        if (hash == null) {
            when(mockQuery.list()).thenReturn(Collections.<AccountData>emptyList());
            return null;
        }
        AccountData account = UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", hash, salt);
        when(mockQuery.list()).thenReturn(Collections.singletonList(account));
        return account;
    }

    /** Run every queued main-thread task, including any a task queues itself. */
    private void runMainThread() {
        while (!mainThread.isEmpty()) {
            mainThread.remove(0).run();
        }
    }

    /** {@code /panel} as its worker runs it: publish the link and return its id. */
    private String openLink() {
        LoginService.PanelLinkResult result = service.requestPanelLink(player);
        assertThat(result.isSuccess()).as("the link was published: %s", result.getError()).isTrue();
        service.announcePanelPause(player);
        return result.getLinkId();
    }

    private LinkCompletion completion(String completionId, String linkId, LinkProof proof, boolean provision) {
        return new LinkCompletion(completionId, linkId, proof, playerUuid, "TestPlayer", provision,
                proof == LinkProof.WEB_REGISTRATION ? "webuser" : null);
    }

    /** Deliver a completion as the framework does and run the main-thread work; returns the answer. */
    private CompletionResult deliver(LinkCompletion completion) throws Exception {
        CompletableFuture<CompletionResult> answer = port.handler.onCompleted(completion);
        runMainThread();
        assertThat(answer).as("the answer is complete once the main-thread work ran").isDone();
        return answer.get();
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

    @SuppressWarnings("unchecked")
    private boolean paused() throws Exception {
        return ((Map<UUID, Long>) field("panelPausedAt")).containsKey(playerUuid);
    }

    /** The player saw {@code key}'s text exactly {@code count} times. */
    private void assertMessage(String key, int count) {
        String text = ChatColor.translateAlternateColorCodes('&', CatalogueText.text("zh", key));
        verify(player, times(count)).sendMessage(text);
    }

    // ==================== registration ====================

    @Test
    @DisplayName("initialising installs exactly one handler and shutdown removes it")
    void handlerLifecycle() {
        assertThat(port.registrations).isEqualTo(1);
        assertThat(port.handler).isNotNull();

        service.shutdown();

        assertThat(port.unregistrations).isEqualTo(1);
        assertThat(port.handler).isNull();
    }

    @Test
    @DisplayName("a handler the framework refuses is reported once in the console")
    void refusedRegistrationIsLogged() {
        PlayerLinkPort refusing = mock(PlayerLinkPort.class);
        when(refusing.registerHandler(any(LinkEventHandler.class))).thenReturn(false);
        service.setPlayerLinkPort(refusing);

        service.registerPlayerLinkHandler();

        verify(UltiLoginTestHelper.getMockLogger()).warn(CatalogueText.text("zh", "log_panel_link_handler_unavailable"));
    }

    // ==================== tracer: a linked web account logs the waiting player in ====================

    @Nested
    @DisplayName("completion without an account request")
    class NotRequested {

        @Test
        @DisplayName("tracer: linked web account completes L1 -> the waiting player is logged in, L1 ends, the pause clears, answer not_requested")
        void linkedWebAccountLogsTheWaitingPlayerIn() throws Exception {
            service.onPlayerJoin(player);
            String link = openLink();
            assertThat(paused()).as("the countdown waits for the link").isTrue();

            CompletableFuture<CompletionResult> answer =
                    port.handler.onCompleted(completion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, false));

            assertThat(answer).as("the handler returns without doing the work itself").isNotDone();
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertThat(mainThread).as("the work is one main-thread task").hasSize(1);

            runMainThread();

            assertThat(answer.get()).isEqualTo(CompletionResult.NOT_REQUESTED);
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("L1 is no longer pending").isFalse();
            assertThat(paused()).as("the pause is cleared").isFalse();
            assertMessage("panel_signed_in", 1);
            verify(dataOperator).updateCounted(any(AccountData.class));
            verify(dataOperator, never()).insert(any(AccountData.class));
        }

        @Test
        @DisplayName("control: a player already logged in, proof in game session -> sign-in complete line, no second login")
        void alreadyLoggedInPlayerIsNotLoggedInAgain() throws Exception {
            markLoggedIn();
            String link = openLink();

            assertThat(deliver(completion("C1", link, LinkProof.IN_GAME_SESSION, false)))
                    .isEqualTo(CompletionResult.NOT_REQUESTED);

            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertMessage("panel_web_signin_complete", 1);
            assertMessage("panel_signed_in", 0);
            verify(dataOperator, never()).updateCounted(any(AccountData.class));
        }

        @Test
        @DisplayName("an in-game-session proof never logs in a connection that is not logged in")
        void inGameSessionProofNeverLogsIn() throws Exception {
            service.onPlayerJoin(player);
            String link = openLink();

            assertThat(deliver(completion("C1", link, LinkProof.IN_GAME_SESSION, false)))
                    .isEqualTo(CompletionResult.NOT_REQUESTED);

            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertThat(paused()).as("the countdown resumes").isFalse();
            assertMessage("panel_signed_in", 0);
        }

        @Test
        @DisplayName("a session is remembered after the sign-in when sessions are enabled")
        void signInRemembersTheSession() throws Exception {
            when(config.isSessionEnabled()).thenReturn(true);
            service.onPlayerJoin(player);
            String link = openLink();

            deliver(completion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, false));

            assertThat(service.hasValidSession(player)).isTrue();
        }
    }
}
