package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.service.PlayerLinkPort.CompletionResult;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkCompletion;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkCreation;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkEventHandler;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkExpiry;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
    private final LinkedBlockingQueue<Runnable> mainThread = new LinkedBlockingQueue<>();
    private BukkitScheduler scheduler;

    /** A fake player-link API: answers every create with a link and keeps the handler the service installs. */
    private final class FakePort implements PlayerLinkPort {
        private LinkEventHandler handler;
        private int registrations;
        private int unregistrations;
        private int next = 1;
        private Runnable duringCreate = () -> { };
        private Supplier<CompletableFuture<LinkCreation>> answer;

        @Override
        public CompletableFuture<LinkCreation> create(UUID uuid, String name, boolean authenticated,
                                                      boolean accountExists, Set<LinkProof> offeredProofs) {
            duringCreate.run();
            if (answer != null) {
                return answer.get();
            }
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

        scheduler = spy(server.getScheduler());
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
        Runnable task;
        while ((task = mainThread.poll()) != null) {
            task.run();
        }
    }

    /** {@code /panel} as its worker runs it: publish the link and return its id. */
    private String openLink() {
        CompletableFuture<LoginService.PanelLinkResult> answer = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                answer.complete(service.requestPanelLink(player));
            } catch (Throwable error) {
                answer.completeExceptionally(error);
            } finally {
                mainThread.add(() -> { }); // Wake the queue consumer even for a precheck failure.
            }
        }, "panel-open-link");
        worker.start();
        try {
            do {
                Runnable task = mainThread.poll(10, TimeUnit.SECONDS);
                assertThat(task).as("publication or worker completion arrived").isNotNull();
                task.run();
            } while (!answer.isDone());
            worker.join(10_000L);
            runMainThread();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        LoginService.PanelLinkResult result = answer.join();
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

    /** One other account registered from the player's address (127.0.0.1); the player's own lookup is unchanged. */
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

    private void advance(int seconds) {
        now[0] += seconds * 1000L;
    }

    /**
     * The player joined 55 s into a 60 s timeout before the link was opened; after the link ends the countdown must
     * continue with the 5 s that were left: no kick 4 s later, a kick 2 s after that.
     */
    private void assertCountdownResumedWithTheTimeLeft() {
        service.checkTimeouts();
        advance(4);
        service.checkTimeouts();
        verify(player, never()).kickPlayer(anyString());
        advance(2);
        service.checkTimeouts();
        verify(player, times(1)).kickPlayer(anyString());
    }

    /** Join, wait 55 s of the 60 s timeout, open a link and let 100 s pass while it is pending. */
    private String joinAndOpenLinkLate() {
        service.onPlayerJoin(player);
        advance(55);
        String link = openLink();
        advance(100);
        return link;
    }

    private AccountData inserted() {
        ArgumentCaptor<AccountData> row = ArgumentCaptor.forClass(AccountData.class);
        verify(dataOperator, times(1)).insert(row.capture());
        return row.getValue();
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

    /** Complete the create while its waiting worker cannot enter the publication lock. */
    @SuppressWarnings("unchecked")
    private void completeBeforeWorkerCanRekey(Runnable event) throws Exception {
        CompletableFuture<LinkCreation> creation = new CompletableFuture<>();
        CountDownLatch entered = new CountDownLatch(1);
        port.answer = () -> creation;
        port.duringCreate = entered::countDown;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                service.requestPanelLink(player);
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "panel-publication-regression");
        worker.start();
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).as("worker reached create").isTrue();
            AtomicLong cell = ((Map<UUID, AtomicLong>) field("invalidationGenerations")).get(playerUuid);
            synchronized (cell) {
                creation.complete(LinkCreation.created("RACE", LINK_URL + "RACE",
                        Instant.ofEpochMilli(now[0] + 300_000L), EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT)));
                event.run();
                runMainThread();
            }
        } finally {
            creation.complete(LinkCreation.failed(PlayerLinkPort.LinkFailure.OTHER, "test cleanup"));
            worker.join(10_000L);
        }
        assertThat(worker.isAlive()).as("request worker finished").isFalse();
        assertThat(failure.get()).as("request worker did not throw").isNull();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(LinkExpiry.Reason.class)
    void expiryAfterCreateCompletionBeforeWorkerRekeyEndsTheLink(LinkExpiry.Reason reason) throws Exception {
        service.onPlayerJoin(player);
        advance(55);
        completeBeforeWorkerCanRekey(() -> port.handler.onExpired(new LinkExpiry("RACE", playerUuid, reason)));

        assertThat(service.isPanelRequestCurrent(playerUuid, "RACE", service.getInvalidationGeneration(playerUuid)))
                .as("an already-ended link must not be published as live").isFalse();
        assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        assertThat(paused()).isFalse();
        assertThat(service.isLoggedIn(playerUuid)).isFalse();
        assertCountdownResumedWithTheTimeLeft();
    }

    @Test
    void completionAfterCreateBeforeWorkerRekeyLogsInTheCurrentConnection() throws Exception {
        service.onPlayerJoin(player);
        AtomicReference<CompletableFuture<CompletionResult>> answer = new AtomicReference<>();
        completeBeforeWorkerCanRekey(() -> answer.set(port.handler.onCompleted(
                completion("RACE-C", "RACE", LinkProof.LINKED_WEB_ACCOUNT, false))));

        assertThat(answer.get()).isCompletedWithValue(CompletionResult.NOT_REQUESTED);
        assertThat(service.isLoggedIn(playerUuid)).as("a current completion is not late").isTrue();
        assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        assertMessage("panel_late_completion", 0);
    }

    @Test
    void accountLookupFailureReturnsGenericPanelFailure() {
        when(mockQuery.list()).thenThrow(new IllegalStateException("account database unavailable"));
        assertPrecheckFailure();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ipRegistrationLookupFailureReturnsGenericPanelFailure() {
        setAccount(null, null);
        when(config.getMaxRegisterPerIp()).thenReturn(1);
        Query<AccountData> byIp = mock(Query.class);
        when(mockQuery.where("register_ip")).thenReturn(byIp);
        when(byIp.eq(any())).thenReturn(byIp);
        when(byIp.list()).thenThrow(new IllegalStateException("address database unavailable"));
        assertPrecheckFailure();
    }

    private void assertPrecheckFailure() {
        AtomicReference<LoginService.PanelLinkResult> result = new AtomicReference<>();
        org.assertj.core.api.Assertions.assertThatCode(() -> result.set(service.requestPanelLink(player)))
                .as("a database precheck failure must return a result, not kill the command worker")
                .doesNotThrowAnyException();
        assertThat(result.get().isSuccess()).isFalse();
        assertThat(result.get().getFailure()).isEqualTo(LoginService.PanelLinkResult.Failure.GENERIC);
        assertThat(result.get().getLinkId()).isNull();
        assertThat(result.get().getUrl()).isNull();
        assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        assertThat(port.next).as("no create was sent").isEqualTo(1);
    }

    @Test
    void aQueuedPublicationCannotResurrectATimedOutRequest() throws Exception {
        service.onPlayerJoin(player);
        service.setPanelCreateCap(1);
        LoginService.PanelLinkResult result = service.requestPanelLink(player);
        assertThat(result.isSuccess()).isFalse();
        assertThat(mainThread).hasSize(1);
        runMainThread();
        assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
        assertThat(paused()).isFalse();
    }

    @Test
    void interruptionEndsTheRequestAndPreservesTheInterrupt() throws Exception {
        Thread.currentThread().interrupt();
        try {
            assertThat(service.requestPanelLink(player).isSuccess()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        runMainThread();
        assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
    }

    @Test
    void unschedulablePublicationEndsTheRequest() {
        doThrow(new IllegalStateException("disabled")).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        assertThat(service.requestPanelLink(player).isSuccess()).isFalse();
        assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
    }

    @Test
    void synchronousCreateFailureCleansItsOwnRequest() {
        port.answer = () -> { throw new IllegalStateException("create refused"); };
        assertThat(service.requestPanelLink(player).getFailure()).isEqualTo(LoginService.PanelLinkResult.Failure.GENERIC);
        assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
    }

    @Test
    void createAlreadyCompletedBeforeReturningStillPublishesBeforeItsExpiry() throws Exception {
        AtomicReference<Thread> eventThread = new AtomicReference<>();
        port.answer = () -> {
            CompletableFuture<LinkCreation> answer = CompletableFuture.completedFuture(LinkCreation.created(
                    "INLINE", LINK_URL + "INLINE", Instant.ofEpochMilli(now[0] + 300_000L),
                    EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT)));
            Thread delivery = new Thread(() -> port.handler.onExpired(
                    new LinkExpiry("INLINE", playerUuid, LinkExpiry.Reason.CANCELLED)), "panel-inline-event");
            eventThread.set(delivery);
            delivery.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (delivery.getState() != Thread.State.BLOCKED && delivery.isAlive()
                    && System.nanoTime() < deadline) {
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            assertThat(delivery.getState()).as("event must block on the generation cell before create returns")
                    .isEqualTo(Thread.State.BLOCKED);
            return answer;
        };
        service.onPlayerJoin(player);
        CompletableFuture<LoginService.PanelLinkResult> result = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                result.complete(service.requestPanelLink(player));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, "panel-inline-create");
        worker.start();
        try {
            Runnable publication = mainThread.poll(10, TimeUnit.SECONDS);
            assertThat(publication).as("first queued task is publication").isNotNull();
            publication.run();
            assertThat(service.isPanelRequestCurrent(playerUuid, "INLINE", service.getInvalidationGeneration(playerUuid)))
                    .as("publication ran before the end event").isTrue();
            Runnable end = mainThread.poll(10, TimeUnit.SECONDS);
            assertThat(end).as("second queued task is the end event").isNotNull();
            end.run();
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("end event ran after publication").isFalse();
            assertThat(result.get(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(paused()).isFalse();
        } finally {
            worker.join(10_000L);
            if (eventThread.get() != null) { eventThread.get().join(10_000L); }
        }
        assertThat(worker.isAlive()).isFalse();
        assertThat(eventThread.get().isAlive()).isFalse();
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

    // ==================== an account requested (web registration, or a retried provision) ====================

    @Nested
    @DisplayName("completion that requests a game account")
    class Provisioning {

        @Test
        @DisplayName("web registration, no account, limit not reached -> one row with empty hash and salt and this address, logged in, created")
        void webRegistrationCreatesTheAccount() throws Exception {
            setAccount(null, null);
            service.onPlayerJoin(player);
            String link = openLink();

            assertThat(deliver(completion("C1", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.CREATED);

            AccountData row = inserted();
            assertThat(row.getPlayerUuid()).isEqualTo(playerUuid.toString());
            assertThat(row.getPlayerName()).isEqualTo("TestPlayer");
            assertThat(row.getPasswordHash()).as("no game password yet").isEmpty();
            assertThat(row.getSalt()).isEmpty();
            assertThat(row.getRegisterIp()).isEqualTo("127.0.0.1");
            assertThat(row.getLastIp()).isEqualTo("127.0.0.1");
            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            assertThat(paused()).isFalse();
            assertMessage("panel_signed_in", 1);
        }

        @Test
        @DisplayName("an account made meanwhile with /register is never changed: its credentials stay, the connection is logged in, account_exists")
        void existingAccountIsNeverOverwritten() throws Exception {
            setAccount(null, null);
            service.onPlayerJoin(player);
            String link = openLink();
            AccountData existing = setAccount("hash", "salt");
            existing.setRegisterIp("10.0.0.9");

            assertThat(deliver(completion("C1", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.ACCOUNT_EXISTS);

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(existing.getPasswordHash()).isEqualTo("hash");
            assertThat(existing.getSalt()).isEqualTo("salt");
            assertThat(existing.getRegisterIp()).isEqualTo("10.0.0.9");
            assertThat(existing.getPlayerUuid()).isEqualTo(playerUuid.toString());
            assertThat(service.isLoggedIn(playerUuid)).as("DECISIONS item 11").isTrue();
        }

        @Test
        @DisplayName("max-register-per-ip reached by completion time -> nothing created, nobody logged in, the limit line, countdown resumes, registration_rejected")
        void limitReachedAtCompletion() throws Exception {
            setAccount(null, null);
            String link = joinAndOpenLinkLate();
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();

            assertThat(deliver(completion("C1", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.REGISTRATION_REJECTED);

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            verify(player).sendMessage(ChatColor.RED + CatalogueText.text("zh", "ip_limit_reached"));
            assertCountdownResumedWithTheTimeLeft();
        }

        @Test
        @DisplayName("the insert throws -> nobody logged in, the create-failed line, countdown resumes, auth_plugin_unavailable")
        void failedInsertResumesTheCountdown() throws Exception {
            setAccount(null, null);
            String link = joinAndOpenLinkLate();
            doThrow(new RuntimeException("database unavailable")).when(dataOperator).insert(any(AccountData.class));

            assertThat(deliver(completion("C1", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.AUTH_PLUGIN_UNAVAILABLE);

            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertMessage("panel_account_create_failed", 1);
            verify(UltiLoginTestHelper.getMockLogger()).error(
                    CatalogueText.text("zh", "log_web_account_create_failed")
                            .replace("{PLAYER}", "TestPlayer").replace("{ERROR}", "database unavailable"));
            assertCountdownResumedWithTheTimeLeft();
        }

        @Test
        @DisplayName("ulticloud.enabled turned off before the completion -> nothing created, nobody logged in, auth_plugin_unavailable")
        void disabledIntegrationCreatesNothing() throws Exception {
            setAccount(null, null);
            String link = joinAndOpenLinkLate();
            when(config.isUlticloudEnabled()).thenReturn(false);

            assertThat(deliver(completion("C1", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.AUTH_PLUGIN_UNAVAILABLE);

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertCountdownResumedWithTheTimeLeft();
        }

        @Test
        @DisplayName("no account and none requested -> the not-registered line, nobody logged in, countdown resumes, not_requested")
        void noAccountAndNoneRequested() throws Exception {
            setAccount(null, null);
            String link = joinAndOpenLinkLate();

            assertThat(deliver(completion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, false)))
                    .isEqualTo(CompletionResult.NOT_REQUESTED);

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertMessage("panel_not_registered_in_game", 1);
            assertCountdownResumedWithTheTimeLeft();
        }

        @Test
        @DisplayName("a failed last-login write still logs the player in and is logged with its reason")
        void failedLastLoginWriteStillLogsIn() throws Exception {
            when(dataOperator.updateCounted(any(AccountData.class))).thenReturn(0);
            service.onPlayerJoin(player);
            String link = openLink();

            assertThat(deliver(completion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, false)))
                    .isEqualTo(CompletionResult.NOT_REQUESTED);

            assertThat(service.isLoggedIn(playerUuid)).isTrue();
            verify(UltiLoginTestHelper.getMockLogger()).error(CatalogueText.text("zh",
                    "log_account_update_after_panel_failed") + ": " + CatalogueText.text("zh", "log_account_row_gone"));
        }
    }

    // ==================== at-least-once delivery ====================

    @Nested
    @DisplayName("redelivered completions (DECISIONS item 9)")
    class Deduplication {

        @Test
        @DisplayName("the same completion id twice -> the second creates nothing, logs nobody in, says nothing and answers the first result")
        void duplicateIsInert() throws Exception {
            setAccount(null, null);
            service.onPlayerJoin(player);
            String link = openLink();
            LinkCompletion c1 = completion("C1", link, LinkProof.WEB_REGISTRATION, true);
            assertThat(deliver(c1)).isEqualTo(CompletionResult.CREATED);
            service.onPlayerQuit(player);
            clearInvocations(player);

            assertThat(deliver(c1)).isEqualTo(CompletionResult.CREATED);

            verify(dataOperator, times(1)).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("an error result is recorded too: a redelivery after a refused registration answers it again and creates nothing")
        void errorResultIsRecorded() throws Exception {
            setAccount(null, null);
            String link = joinAndOpenLinkLate();
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();
            LinkCompletion c1 = completion("C1", link, LinkProof.WEB_REGISTRATION, true);
            assertThat(deliver(c1)).isEqualTo(CompletionResult.REGISTRATION_REJECTED);
            when(config.getMaxRegisterPerIp()).thenReturn(0);

            assertThat(deliver(c1)).isEqualTo(CompletionResult.REGISTRATION_REJECTED);

            verify(dataOperator, never()).insert(any(AccountData.class));
        }

        @Test
        @DisplayName("300 distinct completions keep the newest 256 ids, which still de-duplicate; an evicted id is handled again")
        @SuppressWarnings("unchecked")
        void boundedToTheNewest256() throws Exception {
            for (int i = 0; i < 300; i++) {
                deliver(completion("C" + i, "gone-" + i, LinkProof.LINKED_WEB_ACCOUNT, false));
            }
            Map<String, CompletionResult> answered = (Map<String, CompletionResult>) field("answeredCompletions");
            assertThat(answered).hasSize(LoginService.ANSWERED_COMPLETIONS_KEPT).hasSize(256)
                    .containsKeys("C44", "C299").doesNotContainKeys("C0", "C43");
            setAccount(null, null);

            assertThat(deliver(completion("C44", "gone-44", LinkProof.LINKED_WEB_ACCOUNT, true)))
                    .as("a remembered id is answered from the record, even with different content")
                    .isEqualTo(CompletionResult.NOT_REQUESTED);
            verify(dataOperator, never()).insert(any(AccountData.class));

            assertThat(deliver(completion("C0", "gone-0", LinkProof.LINKED_WEB_ACCOUNT, true)))
                    .isEqualTo(CompletionResult.CREATED);
            verify(dataOperator, times(1)).insert(any(AccountData.class));
        }
    }

    // ==================== late completions (DECISIONS item 10) ====================

    @Nested
    @DisplayName("late completions: the link is no longer pending")
    class LateCompletions {

        @Test
        @DisplayName("F1: quit and rejoin retains the requesting address, creates the account but logs nobody in")
        void quitAndRejoin() throws Exception {
            setAccount(null, null);
            String link = openLink();
            service.onPlayerQuit(player);
            service.onPlayerJoin(player);

            assertThat(deliver(completion("C1", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.CREATED);

            AccountData row = inserted();
            assertThat(row.getPasswordHash()).isEmpty();
            assertThat(row.getRegisterIp()).as("quit does not lose the requesting address").isEqualTo("127.0.0.1");
            assertThat(row.getPlayerName()).isEqualTo("TestPlayer");
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertMessage("panel_late_completion", 1);
        }

        @Test
        @DisplayName("an unknown address after restart still skips the cap and stores empty IPs")
        void lateProvisionWithUnknownAddressKeepsRestartBehaviour() throws Exception {
            setAccount(null, null);
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();
            doReturn(null).when(server).getPlayer(playerUuid);

            assertThat(deliver(completion("C1", "from-before-restart", LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.CREATED);
            assertThat(inserted().getRegisterIp()).isEmpty();
            assertThat(inserted().getLastIp()).isEmpty();
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        void quitRetainsTheCapAndRedeliveryRepeatsTheRejection() throws Exception {
            setAccount(null, null);
            String link = openLink();
            service.onPlayerQuit(player);
            doReturn(null).when(server).getPlayer(playerUuid);
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();
            LinkCompletion event = completion("LATE-CAP", link, LinkProof.WEB_REGISTRATION, true);

            assertThat(deliver(event)).as("late provisioning must enforce the requesting IP's cap")
                    .isEqualTo(CompletionResult.REGISTRATION_REJECTED);
            when(config.getMaxRegisterPerIp()).thenReturn(0);
            assertThat(deliver(event)).as("redelivery must not recompute the recorded rejection")
                    .isEqualTo(CompletionResult.REGISTRATION_REJECTED);
            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
        }

        @Test
        @SuppressWarnings("unchecked")
        void lateCreatedAccountCountsAgainstTheAddressAndRedeliveryIsInert() throws Exception {
            setAccount(null, null);
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            List<AccountData> rows = new ArrayList<>();
            Query<AccountData> byIp = mock(Query.class);
            when(mockQuery.where("register_ip")).thenReturn(byIp);
            when(byIp.eq(any())).thenAnswer(invocation -> {
                String ip = invocation.getArgument(0);
                when(byIp.list()).thenAnswer(query -> {
                    List<AccountData> matching = new ArrayList<>();
                    for (AccountData row : rows) {
                        if (ip.equals(row.getRegisterIp())) { matching.add(row); }
                    }
                    return matching;
                });
                return byIp;
            });
            doAnswer(invocation -> { rows.add(invocation.getArgument(0)); return null; })
                    .when(dataOperator).insert(any(AccountData.class));
            String link = openLink();
            service.onPlayerQuit(player);
            LinkCompletion event = completion("LATE-CREATED", link, LinkProof.WEB_REGISTRATION, true);

            assertThat(deliver(event)).isEqualTo(CompletionResult.CREATED);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getRegisterIp()).isEqualTo("127.0.0.1");
            assertThat(rows.get(0).getLastIp()).isEqualTo("127.0.0.1");
            assertThat(deliver(event)).as("recorded success stays success even though the cap is now reached")
                    .isEqualTo(CompletionResult.CREATED);
            assertThat(service.requestPanelLink(player).getFailure()).as("the saved address now counts")
                    .isEqualTo(LoginService.PanelLinkResult.Failure.IP_LIMIT);
            verify(dataOperator, times(1)).insert(any(AccountData.class));
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(LinkExpiry.Reason.class)
        void gatewayEndRetainsTheAddressLimit(LinkExpiry.Reason reason) throws Exception {
            setAccount(null, null);
            String link = openLink();
            port.handler.onExpired(new LinkExpiry(link, playerUuid, reason));
            runMainThread();
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();

            assertThat(deliver(completion("ENDED", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.REGISTRATION_REJECTED);
            verify(dataOperator, never()).insert(any(AccountData.class));
        }

        @Test
        void localDeadlineAndSupersedingLinkDoNotLoseTheAddress() throws Exception {
            setAccount(null, null);
            String first = openLink();
            openLink();
            advance(301);
            service.cleanupExpiredPanelRequests();
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();

            assertThat(deliver(completion("LOCAL-END", first, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.REGISTRATION_REJECTED);
            verify(dataOperator, never()).insert(any(AccountData.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void addressRetentionIsDeadlinePlus24HoursAndCleanupRemovesIt() throws Exception {
            setAccount(null, null);
            String first = openLink();
            advance(100);
            String second = openLink();
            service.onPlayerQuit(player);
            Map<String, ?> origins = (Map<String, ?>) field("panelLinkOrigins");
            assertThat(origins).hasSize(2);

            advance(86_600); // first deadline + 24h exactly; second still has 100 seconds left.
            service.cleanupExpiredPanelRequests();
            assertThat(origins).containsKeys(first, second);
            advance(1);
            service.cleanupExpiredPanelRequests();
            assertThat(origins).doesNotContainKey(first).containsKey(second);
            advance(100);
            service.cleanupExpiredPanelRequests();
            assertThat(origins).isEmpty();
        }

        @Test
        void createReceiptRetainsAddressEvenWhenQuitMadePublicationStale() throws Exception {
            setAccount(null, null);
            CompletableFuture<LinkCreation> creation = new CompletableFuture<>();
            CountDownLatch entered = new CountDownLatch(1);
            port.answer = () -> creation;
            port.duringCreate = entered::countDown;
            CompletableFuture<LoginService.PanelLinkResult> result = new CompletableFuture<>();
            Thread worker = new Thread(() -> result.complete(service.requestPanelLink(player)), "stale-origin-create");
            worker.start();
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                service.onPlayerQuit(player);
                creation.complete(LinkCreation.created("STALE", LINK_URL + "STALE",
                        Instant.ofEpochMilli(now[0] + 300_000L), EnumSet.of(LinkProof.WEB_REGISTRATION)));
                Runnable publication = mainThread.poll(10, TimeUnit.SECONDS);
                assertThat(publication).isNotNull();
                publication.run();
                assertThat(result.get(10, TimeUnit.SECONDS).isSuccess()).isFalse();
                when(config.getMaxRegisterPerIp()).thenReturn(1);
                oneAccountAlreadyFromThisAddress();

                assertThat(deliver(completion("STALE-CAP", "STALE", LinkProof.WEB_REGISTRATION, true)))
                        .isEqualTo(CompletionResult.REGISTRATION_REJECTED);
            } finally {
                creation.complete(LinkCreation.failed(PlayerLinkPort.LinkFailure.OTHER, "test cleanup"));
                runMainThread();
                worker.join(10_000L);
            }
            assertThat(worker.isAlive()).isFalse();
        }

        @Test
        void createReceiptRetainsAddressEvenWhenPublicationTimesOut() throws Exception {
            setAccount(null, null);
            service.setPanelCreateCap(1);
            assertThat(service.requestPanelLink(player).isSuccess()).isFalse();
            runMainThread();
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();

            assertThat(deliver(completion("TIMEOUT-CAP", "L1", LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.REGISTRATION_REJECTED);
        }

        @Test
        @SuppressWarnings("unchecked")
        void nextCreateReceiptPrunesExpiredOriginsButKeepsItsOwn() throws Exception {
            String first = openLink();
            advance(86_701);
            String second = openLink();
            assertThat((Map<String, ?>) field("panelLinkOrigins")).containsKey(second).doesNotContainKey(first);
        }

        @Test
        void expiredOriginIsNotUsedEvenBeforePeriodicCleanup() throws Exception {
            setAccount(null, null);
            String link = openLink();
            service.onPlayerQuit(player);
            advance(86_701);
            when(config.getMaxRegisterPerIp()).thenReturn(1);
            oneAccountAlreadyFromThisAddress();

            assertThat(deliver(completion("BEYOND-WINDOW", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.CREATED);
            assertThat(inserted().getRegisterIp()).isEmpty();
            assertThat((Map<?, ?>) field("panelLinkOrigins")).isEmpty();
        }

        @Test
        void addressRetentionIsClearedOnShutdown() throws Exception {
            openLink();
            service.shutdown();
            assertThat((Map<?, ?>) field("panelLinkOrigins")).isEmpty();
        }

        @Test
        @DisplayName("a restart left no pending link: an account holder is not logged in, not_requested, the late line")
        void afterRestart() throws Exception {
            service.onPlayerJoin(player);

            assertThat(deliver(completion("C1", "from-before-restart", LinkProof.LINKED_WEB_ACCOUNT, false)))
                    .isEqualTo(CompletionResult.NOT_REQUESTED);

            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertMessage("panel_late_completion", 1);
            assertMessage("panel_signed_in", 0);
        }

        @Test
        @DisplayName("F4: an admin password reset dropped the link -> nobody logged in; a requested account that exists answers account_exists")
        void droppedByAdminReset() throws Exception {
            service.onPlayerJoin(player);
            String link = openLink();
            service.invalidateSession(playerUuid, false);

            assertThat(deliver(completion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, true)))
                    .isEqualTo(CompletionResult.ACCOUNT_EXISTS);

            verify(dataOperator, never()).insert(any(AccountData.class));
            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertMessage("panel_late_completion", 1);
        }

        @Test
        @DisplayName("an offline player: created silently, nobody logged in")
        void offlinePlayer() throws Exception {
            setAccount(null, null);
            String link = openLink();
            service.onPlayerQuit(player);
            doReturn(null).when(server).getPlayer(playerUuid);

            assertThat(deliver(completion("C1", link, LinkProof.WEB_REGISTRATION, true)))
                    .isEqualTo(CompletionResult.CREATED);
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("a player already logged in on a new connection is not told anything")
        void loggedInPlayerIsNotTold() throws Exception {
            markLoggedIn();

            assertThat(deliver(completion("C1", "gone", LinkProof.LINKED_WEB_ACCOUNT, false)))
                    .isEqualTo(CompletionResult.NOT_REQUESTED);
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("a completion naming another player than its link's owner logs nobody in and leaves the link pending")
        void otherPlayersCompletion() throws Exception {
            service.onPlayerJoin(player);
            String link = openLink();

            deliver(new LinkCompletion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, UUID.randomUUID(), "Other", false, null));

            assertThat(service.isLoggedIn(playerUuid)).isFalse();
            assertThat(service.hasPendingPanelRequest(playerUuid)).isTrue();
            assertThat(paused()).isTrue();
        }
    }

    // ==================== expiry events ====================

    @Nested
    @DisplayName("expiry events")
    class Expiry {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "EXPIRED, panel_outcome_expired",
                "CANCELLED, panel_link_cancelled",
                "DENIED, panel_outcome_refused",
                "OTHER, panel_outcome_expired"
        })
        @DisplayName("each reason ends the pending link with its line on the main thread and resumes the countdown with the time left")
        void reasonEndsTheLink(LinkExpiry.Reason reason, String key) {
            String link = joinAndOpenLinkLate();

            port.handler.onExpired(new LinkExpiry(link, playerUuid, reason));
            assertThat(service.hasPendingPanelRequest(playerUuid)).as("ended on the main thread, not on the caller's").isTrue();
            runMainThread();

            assertThat(service.hasPendingPanelRequest(playerUuid)).isFalse();
            assertMessage(key, 1);
            assertCountdownResumedWithTheTimeLeft();
        }

        @Test
        @DisplayName("an earlier link the newer /panel cancelled ends silently and the newer one keeps the pause")
        void earlierLinkEndsSilently() throws Exception {
            service.onPlayerJoin(player);
            String first = openLink();
            String second = openLink();

            port.handler.onExpired(new LinkExpiry(first, playerUuid, LinkExpiry.Reason.CANCELLED));
            runMainThread();

            assertMessage("panel_link_cancelled", 0);
            assertThat(service.isPanelRequestCurrent(playerUuid, second, service.getInvalidationGeneration(playerUuid)))
                    .isTrue();
            assertThat(paused()).isTrue();
        }

        @Test
        @DisplayName("an expiry for a link that is not pending, or for another player's link, does nothing")
        void unknownLinkDoesNothing() throws Exception {
            service.onPlayerJoin(player);
            String link = openLink();

            port.handler.onExpired(new LinkExpiry("not-pending", playerUuid, LinkExpiry.Reason.EXPIRED));
            port.handler.onExpired(new LinkExpiry(link, UUID.randomUUID(), LinkExpiry.Reason.EXPIRED));
            runMainThread();

            assertThat(service.hasPendingPanelRequest(playerUuid)).isTrue();
            assertMessage("panel_outcome_expired", 0);
            assertThat(paused()).isTrue();
        }
    }

    @Test
    @DisplayName("a completion that cannot be scheduled (the plugin is disabling) fails its future, so no result is sent")
    void unschedulableCompletionFails() {
        doThrow(new IllegalStateException("disabled")).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));

        assertThat(port.handler.onCompleted(completion("C1", "L1", LinkProof.LINKED_WEB_ACCOUNT, false)))
                .isCompletedExceptionally();
        port.handler.onExpired(new LinkExpiry("L1", playerUuid, LinkExpiry.Reason.EXPIRED));
    }

    @Test
    @DisplayName("an unexpected failure resumes the countdown and fails the future, so the completion is asked again")
    void unexpectedFailureFailsTheFuture() throws Exception {
        service.onPlayerJoin(player);
        String link = openLink();
        when(mockQuery.list()).thenThrow(new IllegalStateException("database gone"));

        CompletableFuture<CompletionResult> answer =
                port.handler.onCompleted(completion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, false));
        runMainThread();

        assertThat(answer).isCompletedExceptionally();
        assertThat(paused()).isFalse();
        assertThat(service.isLoggedIn(playerUuid)).isFalse();
    }

    // ==================== the backup-password reminder (maintainer's answer to DECISIONS question 1: A) ====================

    @Nested
    @DisplayName("backup-password reminder after a sign-in")
    class BackupPasswordHint {

        private void assertSignedInThenHint() {
            InOrder order = inOrder(player);
            order.verify(player).sendMessage(
                    ChatColor.translateAlternateColorCodes('&', CatalogueText.text("zh", "panel_signed_in")));
            order.verify(player).sendMessage(
                    ChatColor.translateAlternateColorCodes('&', CatalogueText.text("zh", "panel_backup_password_hint")));
        }

        @Test
        @DisplayName("a web registration: signed-in line, then the backup-password hint")
        void afterWebRegistration() throws Exception {
            setAccount(null, null);
            service.onPlayerJoin(player);
            String link = openLink();

            deliver(completion("C1", link, LinkProof.WEB_REGISTRATION, true));

            assertSignedInThenHint();
            assertMessage("panel_backup_password_hint", 1);
        }

        @Test
        @DisplayName("every later /panel login of an account still without a game password gets the hint again")
        void afterEveryLaterLogin() throws Exception {
            when(config.isSessionEnabled()).thenReturn(false);
            setAccount("", "");
            service.onPlayerJoin(player);
            String first = openLink();
            deliver(completion("C1", first, LinkProof.LINKED_WEB_ACCOUNT, false));
            service.onPlayerQuit(player);
            service.onPlayerJoin(player);
            String second = openLink();

            deliver(completion("C2", second, LinkProof.LINKED_WEB_ACCOUNT, false));

            assertMessage("panel_signed_in", 2);
            assertMessage("panel_backup_password_hint", 2);
        }

        @Test
        @DisplayName("an account with a game password never gets the hint")
        void accountWithPasswordGetsNoHint() throws Exception {
            service.onPlayerJoin(player);
            String link = openLink();

            deliver(completion("C1", link, LinkProof.LINKED_WEB_ACCOUNT, false));

            assertMessage("panel_signed_in", 1);
            assertMessage("panel_backup_password_hint", 0);
        }
    }
}
