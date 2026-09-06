package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.service.LoginService;

import net.md_5.bungee.api.chat.BaseComponent;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.UUID;

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
        Server mockServer = mock(Server.class);
        org.bukkit.plugin.PluginManager mockPm = mock(org.bukkit.plugin.PluginManager.class);
        mockScheduler = mock(BukkitScheduler.class);
        when(mockServer.getPluginManager()).thenReturn(mockPm);
        when(mockPm.getPlugin("UltiTools")).thenReturn(mock(org.bukkit.plugin.Plugin.class));
        when(mockServer.getScheduler()).thenReturn(mockScheduler);
        serverField.set(null, mockServer);

        loginService = mock(LoginService.class);
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
         * ecosystem's anonymous BukkitRunnable scheduler callbacks (09-PATTERNS.md).
         */
        private Runnable captureResultDeliveryTask(LoginService.PanelLinkResult result) {
            when(loginService.isPanelEnabled()).thenReturn(true);
            // Round 4 (Codex PR #18 thread 3945030000): openPanel() now captures the player's
            // invalidation generation via getInvalidationGeneration() before scheduling the
            // async worker, and passes it into the two-argument requestPanelLink() overload so
            // the worker can refuse to publish a request that went stale in the gap. The
            // captured value itself does not matter to this fixture (loginService is a plain
            // mock, so getInvalidationGeneration() already returns 0 by default) -- only that
            // the two-argument overload is the one stubbed and invoked.
            when(loginService.requestPanelLink(eq(player), anyLong())).thenReturn(result);

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
                    new LoginService.PanelLinkResult(true, "https://panel.example/link", null));
            when(player.isOnline()).thenReturn(false);

            task.run();

            verify(player, never()).spigot();
            verify(loginService, never()).startAuthPolling(anyString(), any());
        }

        @Test
        @DisplayName("Should send a clickable panel link and start auth polling when the link request succeeds")
        void sendsClickableLinkAndStartsPollingOnSuccess() {
            Runnable task = captureResultDeliveryTask(
                    new LoginService.PanelLinkResult(true, "https://panel.example/link", null));

            Player.Spigot spigot = mock(Player.Spigot.class);
            when(player.spigot()).thenReturn(spigot);

            task.run();

            verify(spigot).sendMessage(any(BaseComponent.class));
            verify(loginService).startAuthPolling(playerUuid.toString(), player);
        }

        @Test
        @DisplayName("Should send an error message when the link request fails")
        void sendsErrorMessageOnFailure() {
            Runnable task = captureResultDeliveryTask(
                    new LoginService.PanelLinkResult(false, null, "API returned status 500"));

            task.run();

            verify(player, times(2)).sendMessage(anyString());
            verify(player, never()).spigot();
            verify(loginService, never()).startAuthPolling(anyString(), any());
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
