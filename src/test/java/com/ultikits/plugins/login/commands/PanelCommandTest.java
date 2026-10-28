package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.i18n.LoginSeams;
import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.websocket.CloudPlayerLinks;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PanelCommand Tests")
class PanelCommandTest {

    private PanelCommand command;
    private LoginService loginService;
    private Player player;
    private UUID playerUuid;
    private BukkitScheduler mockScheduler;
    private Server mockServer;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();

        // Always install a fresh Bukkit.server mock with its own BukkitScheduler stub -- this
        // class needs to capture the exact Runnables PanelCommand schedules (the outer async
        // HTTP-request task, then the inner sync result-delivery task), which requires a
        // scheduler this test controls rather than whatever a previously run test class left
        // installed on the shared static field.
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        mockServer = mock(Server.class);
        org.bukkit.plugin.PluginManager mockPm = mock(org.bukkit.plugin.PluginManager.class);
        mockScheduler = mock(BukkitScheduler.class);
        when(mockServer.getPluginManager()).thenReturn(mockPm);
        when(mockPm.getPlugin("UltiTools")).thenReturn(mock(org.bukkit.plugin.Plugin.class));
        when(mockServer.getScheduler()).thenReturn(mockScheduler);
        serverField.set(null, mockServer);

        loginService = mock(LoginService.class);

        LoginSeams.speak(loginService, "zh");
        command = new PanelCommand(UltiLoginTestHelper.getMockPlugin(), loginService);

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
        // Root-cause fix (PR #17 review): this class installs its own Bukkit.server mock directly
        // (see setUp() above) rather than going through UltiLoginTestHelper.bootstrapLiveServer(),
        // so it must clear the field itself instead of leaving it for the next Surefire-fork class
        // to inherit.
        UltiLoginTestHelper.clearBukkitServer();
    }

    @Nested
    @DisplayName("openPanel")
    class OpenPanel {

        @Test
        @DisplayName("Should send panel not enabled message when disabled")
        void panelNotEnabled() {
            when(loginService.isPanelEnabled()).thenReturn(false);

            command.openPanel(player);

            verify(player).sendMessage(anyString());
            verify(loginService, never()).requestPanelLink(any(), anyLong());
        }

        @Test
        @DisplayName("Should send a generating message and schedule an async panel-link request when enabled")
        void schedulesAsyncLinkRequestWhenEnabled() {
            when(loginService.isPanelEnabled()).thenReturn(true);

            command.openPanel(player);

            verify(player).sendMessage(anyString());
            verify(mockScheduler).runTaskAsynchronously(any(), any(Runnable.class));
        }

        /**
         * Drives openPanel through to the point where the sync result-delivery task has been
         * captured (without running it), given a stubbed requestPanelLink outcome. Two scheduler
         * hops are involved -- runTaskAsynchronously (the HTTP call) then runTask (delivering the
         * result back on the main thread) -- so both Runnables are captured and the outer one is
         * run first to produce the inner one, per the capture-and-invoke idiom for this
         * ecosystem's anonymous BukkitRunnable scheduler callbacks.
         */
        private Runnable captureResultDeliveryTask(LoginService.PanelLinkResult result) {
            when(loginService.isPanelEnabled()).thenReturn(true);
            // openPanel() now captures the player's invalidation generation via
            // getInvalidationGeneration() before scheduling the async worker, and passes it
            // into the two-argument requestPanelLink() overload so the worker can refuse to
            // publish a request that went stale in the gap. The captured value itself does not
            // matter to this fixture (loginService is a plain mock, so
            // getInvalidationGeneration() already returns 0 by default) -- only that the
            // two-argument overload is the one stubbed and invoked.
            when(loginService.requestPanelLink(eq(player), anyLong())).thenReturn(result);
            // The result-delivery task now revalidates via isPanelRequestCurrent() before acting
            // on a successful result -- default this to "still current" so the pre-existing
            // success-path tests below keep exercising the link-sent/poll-started behaviour unchanged.
            // The dedicated staleness test overrides this per-test.
            lenient().when(loginService.isPanelRequestCurrent(any(), any(), anyLong())).thenReturn(true);

            command.openPanel(player);

            ArgumentCaptor<Runnable> asyncCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTaskAsynchronously(any(), asyncCaptor.capture());
            asyncCaptor.getValue().run();

            ArgumentCaptor<Runnable> syncCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTask(any(), syncCaptor.capture());
            return syncCaptor.getValue();
        }

        @Test
        @DisplayName("Should not deliver the panel-link result if the player went offline before the async request completed")
        void skipsDeliveryWhenOffline() {
            Runnable task = captureResultDeliveryTask(
                    new LoginService.PanelLinkResult(true, "https://panel.example/link", null, "req-1"));
            when(player.isOnline()).thenReturn(false);

            task.run();

            verify(player, never()).spigot();
            verify(loginService, never()).announcePanelPause(any());
        }

        @Test
        @DisplayName("Should send a clickable panel link and announce the countdown pause, checked against the result's link id, when the link request succeeds")
        void sendsClickableLinkAndAnnouncesThePauseOnSuccess() {
            // The main-thread re-check is keyed on the exact link id requestPanelLink() published, not
            // re-derived from "whatever is pending now"; no poll is started (the outcome arrives as an event).
            Runnable task = captureResultDeliveryTask(
                    new LoginService.PanelLinkResult(true, "https://panel.example/link", null, "link-1"));

            Player.Spigot spigot = mock(Player.Spigot.class);
            when(player.spigot()).thenReturn(spigot);

            task.run();

            verify(loginService).isPanelRequestCurrent(eq(playerUuid), eq("link-1"), anyLong());
            verify(spigot).sendMessage(any(BaseComponent.class));
            verify(loginService).announcePanelPause(player);
        }

        @Test
        @DisplayName("Should discard a successful result and send the plain failure message when"
                + " the request was invalidated between requestPanelLink()'s return and this"
                + " callback actually running on the main thread")
        void discardsResultWhenInvalidatedBeforeCallbackRuns() {
            // requestPanelLink()'s own post-POST re-check only closes the race up to the moment
            // that method returns. This callback is itself scheduled via runTask(...) after that
            // method already returned a success result -- an invalidateSession(...) landing in
            // that final gap must still be caught here, or the revoked player would receive the
            // link anyway.
            Runnable task = captureResultDeliveryTask(
                    new LoginService.PanelLinkResult(true, "https://panel.example/link", null, "req-1"));
            when(loginService.isPanelRequestCurrent(eq(playerUuid), eq("req-1"), anyLong()))
                    .thenReturn(false);

            Player.Spigot spigot = mock(Player.Spigot.class);
            when(player.spigot()).thenReturn(spigot);

            task.run();

            verify(spigot, never()).sendMessage(any(BaseComponent.class));
            verify(loginService, never()).announcePanelPause(any());
            // Once for openPanel()'s own "generating" message, once for the discarded result's
            // failure message -- same count as the pre-existing sendsErrorMessageOnFailure case.
            verify(player, times(2)).sendMessage(anyString());
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "{0} -> {1}")
        @org.junit.jupiter.params.provider.CsvSource({
                "LINK_ERROR,    panel_link_error",
                "RATE_LIMITED,  panel_create_rate_limited",
                "NOT_PERMITTED, panel_create_not_permitted",
                "GENERIC,       panel_error"
        })
        @DisplayName("each failure is answered with its own message and delivers nothing")
        void failureMessages(LoginService.PanelLinkResult.Failure failure, String key) {
            Runnable task = captureResultDeliveryTask(
                    new LoginService.PanelLinkResult(false, null, "failed", null, failure));

            task.run();

            verify(player).sendMessage(ChatColor.translateAlternateColorCodes('&', CatalogueText.text("zh", key)));
            verify(player, never()).spigot();
            verify(loginService, never()).announcePanelPause(any());
        }

        @Test
        @DisplayName("an address at max-register-per-ip is answered in red with the text /register uses")
        void ipLimitMessage() {
            Runnable task = captureResultDeliveryTask(new LoginService.PanelLinkResult(false, null, "limit", null,
                    LoginService.PanelLinkResult.Failure.IP_LIMIT));

            task.run();

            verify(player).sendMessage(ChatColor.RED + CatalogueText.text("zh", "ip_limit_reached"));
            verify(player, never()).spigot();
        }

        @Test
        @DisplayName("Should send an error message when the link request fails")
        void sendsErrorMessageOnFailure() {
            Runnable task = captureResultDeliveryTask(
                    new LoginService.PanelLinkResult(false, null, "API returned status 500"));

            task.run();

            verify(player, times(2)).sendMessage(anyString());
            verify(player, never()).spigot();
            verify(loginService, never()).announcePanelPause(any());
        }
    }

    /**
     * Tracer for plan 01-02: {@code /panel} for a registered, logged-in player goes through the real
     * {@link LoginService} and its production player-link adapter to the framework's {@link CloudPlayerLinks}
     * (mocked; Mockito 5 mocks the final class), and the link the framework answers reaches the player as one
     * clickable line. The asynchronous task runs on its own thread, so "not on the main thread" is observed,
     * not assumed.
     */
    @Nested
    @DisplayName("end to end through the framework's player-link API")
    class EndToEnd {

        private CloudPlayerLinks links;
        private final List<Boolean> createdOnPrimaryThread = new ArrayList<>();
        private Thread mainThread;
        private Query<AccountData> accountQuery;
        private final java.util.concurrent.LinkedBlockingQueue<Runnable> mainTasks =
                new java.util.concurrent.LinkedBlockingQueue<>();

        // The framework is installed in UltiTools' real static field, not through mockStatic: a static
        // mock is visible only on the thread that created it, and the create runs on the worker thread.
        @BeforeEach
        void setUpFramework() throws Exception {
            mainThread = Thread.currentThread();
            when(mockServer.isPrimaryThread()).thenAnswer(invocation -> Thread.currentThread() == mainThread);
            doAnswer(invocation -> {
                mainTasks.add(invocation.getArgument(1));
                return mock(org.bukkit.scheduler.BukkitTask.class);
            }).when(mockScheduler).runTask(any(), any(Runnable.class));

            links = mock(CloudPlayerLinks.class);
            UltiTools framework = mock(UltiTools.class);
            when(framework.getCloudPlayerLinks()).thenReturn(links);
            UltiLoginTestHelper.setStaticField(UltiTools.class, "ultiTools", framework);
        }

        @AfterEach
        void tearDownFramework() throws Exception {
            UltiLoginTestHelper.setStaticField(UltiTools.class, "ultiTools", null);
        }

        @SuppressWarnings("unchecked")
        private LoginService realServiceWithAccount() throws Exception {
            LoginConfig config = UltiLoginTestHelper.createDefaultConfig();
            when(config.isUlticloudEnabled()).thenReturn(true);
            DataOperator<AccountData> dataOperator = mock(DataOperator.class);
            Query<AccountData> query = mock(Query.class);
            accountQuery = query;
            when(UltiLoginTestHelper.getMockPlugin().getDataOperator(AccountData.class)).thenReturn(dataOperator);
            when(dataOperator.query()).thenReturn(query);
            when(query.where(anyString())).thenReturn(query);
            when(query.eq(any())).thenReturn(query);
            when(query.list()).thenReturn(Collections.singletonList(
                    UltiLoginTestHelper.createSampleAccount(playerUuid, "TestPlayer", "hash", "salt")));
            LoginService service = new LoginService(UltiLoginTestHelper.getMockPlugin(), config);
            Field loggedIn = LoginService.class.getDeclaredField("loggedInPlayers");
            loggedIn.setAccessible(true);
            ((Map<UUID, Boolean>) loggedIn.get(service)).put(playerUuid, true);
            return service;
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
        @SuppressWarnings("unchecked")
        void databasePrecheckFailureStillDeliversTheMainThreadError(boolean ipLookup) throws Exception {
            LoginService service = realServiceWithAccount();
            if (ipLookup) {
                when(accountQuery.list()).thenReturn(Collections.emptyList());
                when(service.getConfig().getMaxRegisterPerIp()).thenReturn(1);
                Query<AccountData> byIp = mock(Query.class);
                when(accountQuery.where("register_ip")).thenReturn(byIp);
                when(byIp.eq(any())).thenReturn(byIp);
                when(byIp.list()).thenThrow(new IllegalStateException("IP query unavailable"));
            } else {
                when(accountQuery.list()).thenThrow(new IllegalStateException("account query unavailable"));
            }
            PanelCommand panel = new PanelCommand(UltiLoginTestHelper.getMockPlugin(), service);
            panel.openPanel(player);
            ArgumentCaptor<Runnable> async = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTaskAsynchronously(any(), async.capture());
            assertThatCode(() -> async.getValue().run()).doesNotThrowAnyException();
            assertThat(mainTasks).as("a DB failure still schedules the player's result").hasSize(1);
            mainTasks.remove().run();

            InOrder messages = inOrder(player);
            messages.verify(player).sendMessage(ChatColor.translateAlternateColorCodes('&',
                    CatalogueText.text("zh", "panel_generating")));
            messages.verify(player).sendMessage(ChatColor.translateAlternateColorCodes('&',
                    CatalogueText.text("zh", "panel_error")));
            verify(player, never()).spigot();
            verify(links, never()).create(any(), any(), anyString(), any(), any());
        }

        @Test
        @DisplayName("a logged-in player's /panel creates one link off the main thread and receives it as a clickable line")
        void loggedInPlayerGetsAClickableLink() throws Exception {
            String url = "https://panel.example/link/L1";
            when(links.create(any(), any(), anyString(), any(), any())).thenAnswer(invocation -> {
                createdOnPrimaryThread.add(Bukkit.isPrimaryThread());
                return CompletableFuture.completedFuture(CloudPlayerLinks.LinkCreation.created("L1", url,
                        Instant.now().plusSeconds(300),
                        Collections.singletonList(CloudPlayerLinks.LinkProof.IN_GAME_SESSION)));
            });
            LoginService service = realServiceWithAccount();
            PanelCommand panel = new PanelCommand(UltiLoginTestHelper.getMockPlugin(), service);
            Player.Spigot spigot = mock(Player.Spigot.class);
            when(player.spigot()).thenReturn(spigot);

            panel.openPanel(player);

            ArgumentCaptor<Runnable> asyncCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTaskAsynchronously(any(), asyncCaptor.capture());
            Thread worker = new Thread(asyncCaptor.getValue(), "panel-test-async");
            worker.start();
            Runnable publication = mainTasks.poll(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(publication).as("main-thread publication").isNotNull();
            publication.run();
            worker.join(10_000L);
            assertThat(worker.isAlive()).as("the async /panel task finished within 10 s").isFalse();

            Runnable delivery = mainTasks.poll(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(delivery).as("main-thread command result").isNotNull();
            delivery.run();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<CloudPlayerLinks.LinkProof>> proofs = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<CloudPlayerLinks.GameAuth> auth = ArgumentCaptor.forClass(CloudPlayerLinks.GameAuth.class);
            verify(links, times(1)).create(eq(UltiLoginTestHelper.getMockPlugin()), eq(playerUuid), eq("TestPlayer"),
                    auth.capture(), proofs.capture());
            assertThat(auth.getValue().isAuthPlugin()).isTrue();
            assertThat(auth.getValue().authenticated()).as("authenticated").isTrue();
            assertThat(auth.getValue().accountExists()).as("account exists").isTrue();
            assertThat(proofs.getValue()).containsExactlyInAnyOrder(CloudPlayerLinks.LinkProof.IN_GAME_SESSION,
                    CloudPlayerLinks.LinkProof.LINKED_WEB_ACCOUNT);
            assertThat(createdOnPrimaryThread).as("create ran off the main thread").containsExactly(false);

            ArgumentCaptor<BaseComponent> line = ArgumentCaptor.forClass(BaseComponent.class);
            InOrder order = inOrder(player, spigot);
            order.verify(player).sendMessage(
                    ChatColor.translateAlternateColorCodes('&', CatalogueText.text("zh", "panel_generating")));
            order.verify(spigot).sendMessage(line.capture());
            TextComponent sent = (TextComponent) line.getValue();
            assertThat(sent.getText()).isEqualTo(ChatColor.translateAlternateColorCodes('&',
                    CatalogueText.text("zh", "panel_link_sent").replace("{URL}", url)));
            assertThat(sent.getClickEvent().getAction()).isEqualTo(ClickEvent.Action.OPEN_URL);
            assertThat(sent.getClickEvent().getValue()).isEqualTo(url);
        }
    }

    @Nested
    @DisplayName("handleHelp")
    class HandleHelp {

        @Test
        @DisplayName("Should send usage message")
        void sendUsage() {
            CommandSender sender = mock(CommandSender.class);

            command.handleHelp(sender);

            verify(sender).sendMessage(contains("/panel"));
        }
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should create command successfully")
        void createCommand() {
            assertThat(command).isNotNull();
        }
    }
}
