package com.ultikits.plugins.login.listener;

import com.ultikits.plugins.login.i18n.LoginSeams;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.gui.LoginGUIPage;
import com.ultikits.plugins.login.gui.RegisterGUIPage;
import com.ultikits.plugins.login.service.LoginService;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LoginProtectionListener Tests")
class LoginProtectionListenerTest {

    private LoginProtectionListener listener;
    private LoginService loginService;
    private LoginConfig config;
    private Player player;
    private UUID playerUuid;
    private BukkitScheduler mockScheduler;
    private org.bukkit.plugin.Plugin mockBukkitPlugin;
    private Server mockServer;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
        loginService = mock(LoginService.class);
        LoginSeams.speak(loginService, "zh");

        // Mock config on loginService
        config = UltiLoginTestHelper.createDefaultConfig();
        lenient().when(loginService.getConfig()).thenReturn(config);

        // Always install a fresh Bukkit.server mock before the LoginProtectionListener
        // constructor runs (it calls Bukkit.getPluginManager()). Unlike the older
        // conditional-install pattern elsewhere in this suite, this class always needs
        // its own BukkitScheduler stub so the GUI-mode scheduler-callback tests below can
        // capture the exact Runnable that was scheduled, regardless of what a previously
        // run test class already installed into the shared static field.
        java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        mockServer = mock(Server.class);
        org.bukkit.plugin.PluginManager mockPm = mock(org.bukkit.plugin.PluginManager.class);
        mockScheduler = mock(BukkitScheduler.class);
        // presentCredentialPrompt's new dispatchOnMainThread helper only schedules via runTask(...)
        // when bukkitPlugin.isEnabled() is true (off the primary thread, which this mock Server
        // always reports since isPrimaryThread() is never stubbed). Default to enabled so every
        // pre-existing scheduler-capturing test below keeps exercising the runTask path;
        // DisabledPlugin below overrides it back to false for its own case, and PrimaryThread
        // stubs isPrimaryThread() true instead.
        mockBukkitPlugin = mock(org.bukkit.plugin.Plugin.class);
        lenient().when(mockBukkitPlugin.isEnabled()).thenReturn(true);
        lenient().when(mockServer.getPluginManager()).thenReturn(mockPm);
        lenient().when(mockPm.getPlugin("UltiTools")).thenReturn(mockBukkitPlugin);
        lenient().when(mockServer.getScheduler()).thenReturn(mockScheduler);
        serverField.set(null, mockServer);

        listener = new LoginProtectionListener(UltiLoginTestHelper.getMockPlugin(), loginService);

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);

        // Mock player.getServer() for PlayerCommandPreprocessEvent
        Server playerServer = mock(Server.class);
        lenient().when(player.getServer()).thenReturn(playerServer);
        lenient().when(playerServer.getOnlinePlayers()).thenReturn(java.util.Collections.emptyList());
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
    @DisplayName("onPlayerJoin")
    class OnPlayerJoin {

        @Test
        @DisplayName("Should call loginService.onPlayerJoin")
        void callsOnPlayerJoin() {
            PlayerJoinEvent event = new PlayerJoinEvent(player, "join message");

            listener.onPlayerJoin(event);

            verify(loginService).onPlayerJoin(player);
        }
    }

    @Nested
    @DisplayName("presentCredentialPrompt dispatch")
    class PresentCredentialPromptDispatch {

        @Test
        @DisplayName("Should run the prompt inline, without touching the scheduler, when already on the main thread")
        void presentsInlineOnPrimaryThread() {
            when(mockServer.isPrimaryThread()).thenReturn(true);
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(loginService.isRegistered(playerUuid)).thenReturn(true);

            LoginProtectionListener.presentCredentialPrompt(
                    player, UltiLoginTestHelper.getMockPlugin(), loginService, mockBukkitPlugin);

            verify(player).sendMessage(anyString());
            verify(mockScheduler, never()).runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class));
        }

        @Test
        @DisplayName("Should skip the prompt and log a warning, without throwing, when the plugin is disabling")
        void skipsWhenPluginDisabling() {
            when(mockServer.isPrimaryThread()).thenReturn(false);
            when(mockBukkitPlugin.isEnabled()).thenReturn(false);
            when(config.isGuiModeEnabled()).thenReturn(false);

            assertThatCode(() -> LoginProtectionListener.presentCredentialPrompt(
                    player, UltiLoginTestHelper.getMockPlugin(), loginService, mockBukkitPlugin))
                    .doesNotThrowAnyException();

            verify(mockScheduler, never()).runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class));
            verify(player, never()).sendMessage(anyString());
            // The console line follows the language setting (UltiKits/UltiLogin#20)
            String expected = com.ultikits.plugins.login.i18n.CatalogueText.entries("zh")
                    .getOrDefault("log_prompt_skipped_disabling", "<lang/zh has no log_prompt_skipped_disabling>")
                    .replace("{PLAYER}", "TestPlayer");
            verify(UltiLoginTestHelper.getMockLogger()).warn(expected);
        }
    }

    @Nested
    @DisplayName("onPlayerQuit")
    class OnPlayerQuit {

        @Test
        @DisplayName("Should call loginService.onPlayerQuit")
        void callsOnPlayerQuit() {
            PlayerQuitEvent event = new PlayerQuitEvent(player, "quit message");

            listener.onPlayerQuit(event);

            verify(loginService).onPlayerQuit(player);
        }
    }

    @Nested
    @DisplayName("onPlayerMove")
    class OnPlayerMove {

        private org.bukkit.World mockWorld;

        @BeforeEach
        void setUpWorld() {
            mockWorld = mock(org.bukkit.World.class);
        }

        private org.bukkit.Location createMockLocation(int x, int y, int z) {
            org.bukkit.Location loc = mock(org.bukkit.Location.class);
            when(loc.getBlockX()).thenReturn(x);
            when(loc.getBlockY()).thenReturn(y);
            when(loc.getBlockZ()).thenReturn(z);
            when(loc.getWorld()).thenReturn(mockWorld);
            // Paper 1.21's PlayerMoveEvent#setTo/#setFrom now call Location#clone() on the
            // argument before storing it (spigot-api 1.20 stored the reference directly). An
            // unstubbed mock's clone() returns null by default, which made every assertion that
            // reads the location back after the listener resets it see null instead of the mock.
            when(loc.clone()).thenReturn(loc);
            return loc;
        }

        @Test
        @DisplayName("Should allow movement when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            org.bukkit.Location from = createMockLocation(0, 64, 0);
            org.bukkit.Location to = createMockLocation(1, 64, 0);

            PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

            listener.onPlayerMove(event);

            // Event should not cancel location change
            assertThat(event.getTo()).isEqualTo(to);
        }

        @Test
        @DisplayName("Should block movement when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.Location from = createMockLocation(0, 64, 0);
            org.bukkit.Location to = createMockLocation(1, 64, 0);

            PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

            listener.onPlayerMove(event);

            // Event should reset location to from
            assertThat(event.getTo()).isEqualTo(from);
        }

        /**
         * UltiKits/UltiLogin#33: a player steering a vehicle is moved through the same
         * {@link PlayerMoveEvent} (Paper 1.21.11 fires it from
         * {@code ServerGamePacketListenerImpl#handleMoveVehicle} for the controlling rider, measured
         * with {@code javap -c}), and the handler's {@code setTo(from)} makes the server teleport the
         * rider back, which dismounts them. This pins that the handler reverts a move whatever the
         * player is riding: a vehicle exemption added to it would make this fail.
         */
        @Test
        @DisplayName("Should block movement of a player riding a vehicle when not logged in (UltiKits/UltiLogin#33)")
        void blockWhenRidingAVehicle() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            org.bukkit.entity.Boat boat = mock(org.bukkit.entity.Boat.class);
            lenient().when(player.isInsideVehicle()).thenReturn(true);
            lenient().when(player.getVehicle()).thenReturn(boat);
            lenient().when(boat.getPassengers()).thenReturn(java.util.Collections.singletonList(player));

            org.bukkit.Location from = createMockLocation(0, 62, 0);
            org.bukkit.Location to = createMockLocation(3, 62, 1);

            PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

            listener.onPlayerMove(event);

            assertThat(event.getTo()).isEqualTo(from);
        }

        @Test
        @DisplayName("Should allow looking around (no block change)")
        void allowLooking() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.Location from = createMockLocation(0, 64, 0);
            org.bukkit.Location to = createMockLocation(0, 64, 0);

            PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

            listener.onPlayerMove(event);

            // Should not reset location
            assertThat(event.getTo()).isEqualTo(to);
        }
    }

    @Nested
    @DisplayName("onPlayerChat")
    class OnPlayerChat {

        @Test
        @DisplayName("Should allow chat when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "message", java.util.Collections.emptySet());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("Should block chat when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "message", java.util.Collections.emptySet());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
        }
    }

    @Nested
    @DisplayName("onPlayerCommand")
    class OnPlayerCommand {

        @Test
        @DisplayName("Should allow all commands when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/help");

            listener.onPlayerCommand(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("Should allow login commands when not logged in")
        void allowLoginCommands() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.isCommandAllowed("/login password")).thenReturn(true);

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/login password");

            listener.onPlayerCommand(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("Should block other commands when not logged in")
        void blockOtherCommands() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.isCommandAllowed("/help")).thenReturn(false);

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/help");

            listener.onPlayerCommand(event);

            assertThat(event.isCancelled()).isTrue();
        }
    }

    @Nested
    @DisplayName("onBlockBreak")
    class OnBlockBreak {

        @Test
        @DisplayName("Should allow when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            BlockBreakEvent event = mock(BlockBreakEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onBlockBreak(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should block when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            BlockBreakEvent event = mock(BlockBreakEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onBlockBreak(event);

            verify(event).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onBlockPlace")
    class OnBlockPlace {

        @Test
        @DisplayName("Should allow when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            BlockPlaceEvent event = mock(BlockPlaceEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onBlockPlace(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should block when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            BlockPlaceEvent event = mock(BlockPlaceEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onBlockPlace(event);

            verify(event).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerInteract")
    class OnPlayerInteract {

        @Test
        @DisplayName("Should block when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerInteract(event);

            verify(event).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerDropItem")
    class OnPlayerDropItem {

        @Test
        @DisplayName("Should block when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerDropItem(event);

            verify(event).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerDamage")
    class OnPlayerDamage {

        @Test
        @DisplayName("Should block damage when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            EntityDamageEvent event = mock(EntityDamageEvent.class);
            when(event.getEntity()).thenReturn(player);

            listener.onPlayerDamage(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should allow damage when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            EntityDamageEvent event = mock(EntityDamageEvent.class);
            when(event.getEntity()).thenReturn(player);

            listener.onPlayerDamage(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onInventoryClick")
    class OnInventoryClick {

        @Test
        @DisplayName("Should block other inventory interaction")
        void blockOtherInventory() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.event.inventory.InventoryClickEvent event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
            when(event.getWhoClicked()).thenReturn(player);
            when(event.getView()).thenReturn(view);
            when(view.getTitle()).thenReturn("Chest");

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
        }
    }

    /**
     * A title alone never opens anything to an unauthenticated player. The allowance used to be
     * "the title contains 密码, 登录 or 注册", then (UltiKits/UltiLogin#20) "the title equals one of the
     * three configured titles"; since UltiKits/UltiLogin#35 it is decided by identity, and the
     * allowed cases are pinned against the real GUI library in {@code CredentialGuiIdentityTest}.
     */
    @Nested
    @DisplayName("a title alone is not the credential GUI (UltiKits/UltiLogin#20, #35)")
    class CredentialGuiTitles {

        private org.bukkit.event.inventory.InventoryClickEvent clickIn(String title) {
            org.bukkit.event.inventory.InventoryClickEvent event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
            when(event.getWhoClicked()).thenReturn(player);
            when(event.getView()).thenReturn(view);
            when(view.getTitle()).thenReturn(title);
            return event;
        }

        private org.bukkit.event.inventory.InventoryOpenEvent openOf(String title) {
            org.bukkit.event.inventory.InventoryOpenEvent event = mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
            org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getView()).thenReturn(view);
            when(view.getTitle()).thenReturn(title);
            return event;
        }

        @BeforeEach
        void englishTitles() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            lenient().when(config.getGuiLoginTitle()).thenReturn("&6Enter Password");
            lenient().when(config.getGuiRegisterTitle()).thenReturn("&6Set Password");
            lenient().when(config.getGuiConfirmTitle()).thenReturn("&6Confirm Password");
        }

        @Test
        @DisplayName("another inventory whose title merely contains 登录 is refused")
        void foreignTitleRefused() {
            org.bukkit.event.inventory.InventoryClickEvent click = clickIn("\u00a76登录奖励");
            org.bukkit.event.inventory.InventoryOpenEvent open = openOf("\u00a76每日注册礼包");

            listener.onInventoryClick(click);
            listener.onInventoryOpen(open);

            verify(click).setCancelled(true);
            verify(open).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onInventoryOpen")
    class OnInventoryOpen {

        @Test
        @DisplayName("Should block other inventory opening")
        void blockOtherInventory() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.event.inventory.InventoryOpenEvent event = mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
            org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getView()).thenReturn(view);
            when(view.getTitle()).thenReturn("Chest");

            listener.onInventoryOpen(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should allow inventory when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            org.bukkit.event.inventory.InventoryOpenEvent event = mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
            org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getView()).thenReturn(view);
            when(view.getTitle()).thenReturn("Chest");

            listener.onInventoryOpen(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel for non-Player entity")
        void nonPlayerEntity() {
            org.bukkit.event.inventory.InventoryOpenEvent event = mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
            org.bukkit.entity.Entity nonPlayer = mock(org.bukkit.entity.Entity.class);
            when(event.getPlayer()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));

            // HumanEntity that is not a Player
            org.bukkit.entity.HumanEntity humanEntity = mock(org.bukkit.entity.HumanEntity.class);
            when(event.getPlayer()).thenReturn(humanEntity);

            listener.onInventoryOpen(event);

            // Should not interact since the cast (Player) won't apply
            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerInteractEntity")
    class OnPlayerInteractEntity {

        @Test
        @DisplayName("Should block interact entity when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.event.player.PlayerInteractEntityEvent event = mock(org.bukkit.event.player.PlayerInteractEntityEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerInteractEntity(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should allow interact entity when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            org.bukkit.event.player.PlayerInteractEntityEvent event = mock(org.bukkit.event.player.PlayerInteractEntityEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerInteractEntity(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerPickupItem")
    class OnPlayerPickupItem {

        @Test
        @DisplayName("Should block pickup when player not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.event.entity.EntityPickupItemEvent event = mock(org.bukkit.event.entity.EntityPickupItemEvent.class);
            when(event.getEntity()).thenReturn(player);

            listener.onPlayerPickupItem(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should allow pickup when player logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            org.bukkit.event.entity.EntityPickupItemEvent event = mock(org.bukkit.event.entity.EntityPickupItemEvent.class);
            when(event.getEntity()).thenReturn(player);

            listener.onPlayerPickupItem(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should ignore non-player entity pickup")
        void nonPlayerPickup() {
            org.bukkit.event.entity.EntityPickupItemEvent event = mock(org.bukkit.event.entity.EntityPickupItemEvent.class);
            org.bukkit.entity.LivingEntity nonPlayer = mock(org.bukkit.entity.LivingEntity.class);
            when(event.getEntity()).thenReturn(nonPlayer);

            listener.onPlayerPickupItem(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerDamageEntity")
    class OnPlayerDamageEntity {

        @Test
        @DisplayName("Should block damage by player when not logged in")
        void blockWhenNotLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.event.entity.EntityDamageByEntityEvent event = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
            when(event.getDamager()).thenReturn(player);

            listener.onPlayerDamageEntity(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should allow damage by player when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            org.bukkit.event.entity.EntityDamageByEntityEvent event = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
            when(event.getDamager()).thenReturn(player);

            listener.onPlayerDamageEntity(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should ignore damage by non-player entity")
        void nonPlayerDamager() {
            org.bukkit.event.entity.EntityDamageByEntityEvent event = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
            org.bukkit.entity.Entity nonPlayer = mock(org.bukkit.entity.Entity.class);
            when(event.getDamager()).thenReturn(nonPlayer);

            listener.onPlayerDamageEntity(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerDamage (extended)")
    class OnPlayerDamageExtended {

        @Test
        @DisplayName("Should ignore damage to non-player entity")
        void nonPlayerEntity() {
            org.bukkit.event.entity.EntityDamageEvent event = mock(org.bukkit.event.entity.EntityDamageEvent.class);
            org.bukkit.entity.Entity nonPlayer = mock(org.bukkit.entity.Entity.class);
            when(event.getEntity()).thenReturn(nonPlayer);

            listener.onPlayerDamage(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onInventoryClick (extended)")
    class OnInventoryClickExtended {

        @Test
        @DisplayName("Should allow inventory click when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            org.bukkit.event.inventory.InventoryClickEvent event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
            when(event.getWhoClicked()).thenReturn(player);
            when(event.getView()).thenReturn(view);
            when(view.getTitle()).thenReturn("Chest");

            listener.onInventoryClick(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should ignore inventory click by non-player")
        void nonPlayerClick() {
            org.bukkit.event.inventory.InventoryClickEvent event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            org.bukkit.entity.HumanEntity humanEntity = mock(org.bukkit.entity.HumanEntity.class);
            when(event.getWhoClicked()).thenReturn(humanEntity);

            listener.onInventoryClick(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerChat (extended)")
    class OnPlayerChatExtended {

        @Test
        @DisplayName("Should send prompt when chat blocked (non-GUI mode, registered)")
        void sendPromptRegistered() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(loginService.isRegistered(playerUuid)).thenReturn(true);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "message", java.util.Collections.emptySet());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            // The text-prompt branch is now dispatched via Bukkit.getScheduler().runTask(...),
            // the same as the GUI branch already was, so this must capture and run that task
            // rather than expect player.sendMessage(...) synchronously.
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTask(any(), captor.capture());
            captor.getValue().run();
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send register prompt when chat blocked (non-GUI mode, not registered)")
        void sendPromptNotRegistered() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(loginService.isRegistered(playerUuid)).thenReturn(false);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "message", java.util.Collections.emptySet());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            // See sendPromptRegistered() above -- the text-prompt branch now schedules onto the
            // main thread the same way the GUI branch already did.
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTask(any(), captor.capture());
            captor.getValue().run();
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("onPlayerInteract (extended)")
    class OnPlayerInteractExtended {

        @Test
        @DisplayName("Should allow interact when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerDropItem (extended)")
    class OnPlayerDropItemExtended {

        @Test
        @DisplayName("Should allow drop when logged in")
        void allowWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerDropItem(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onPlayerMove (extended)")
    class OnPlayerMoveExtended {

        private org.bukkit.World mockWorld;

        @BeforeEach
        void setUpWorld() {
            mockWorld = mock(org.bukkit.World.class);
        }

        private org.bukkit.Location createMockLocation(int x, int y, int z) {
            org.bukkit.Location loc = mock(org.bukkit.Location.class);
            when(loc.getBlockX()).thenReturn(x);
            when(loc.getBlockY()).thenReturn(y);
            when(loc.getBlockZ()).thenReturn(z);
            when(loc.getWorld()).thenReturn(mockWorld);
            // Paper 1.21's PlayerMoveEvent#setTo/#setFrom now call Location#clone() on the
            // argument before storing it (spigot-api 1.20 stored the reference directly). An
            // unstubbed mock's clone() returns null by default, which made every assertion that
            // reads the location back after the listener resets it see null instead of the mock.
            when(loc.clone()).thenReturn(loc);
            return loc;
        }

        @Test
        @DisplayName("Should block Y movement when not logged in")
        void blockYMovement() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.Location from = createMockLocation(0, 64, 0);
            org.bukkit.Location to = createMockLocation(0, 65, 0);

            PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

            listener.onPlayerMove(event);

            assertThat(event.getTo()).isEqualTo(from);
        }

        @Test
        @DisplayName("Should block Z movement when not logged in")
        void blockZMovement() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            org.bukkit.Location from = createMockLocation(0, 64, 0);
            org.bukkit.Location to = createMockLocation(0, 64, 1);

            PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

            listener.onPlayerMove(event);

            assertThat(event.getTo()).isEqualTo(from);
        }
    }

    @Nested
    @DisplayName("onPlayerCommand (extended)")
    class OnPlayerCommandExtended {

        @Test
        @DisplayName("Should send login prompt when command blocked (non-GUI mode, registered)")
        void sendLoginPromptRegistered() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.isCommandAllowed("/help")).thenReturn(false);
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(loginService.isRegistered(playerUuid)).thenReturn(true);

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/help");

            listener.onPlayerCommand(event);

            assertThat(event.isCancelled()).isTrue();
            // See OnPlayerChatExtended#sendPromptRegistered() above -- the text-prompt branch
            // now schedules onto the main thread the same way the GUI branch already did.
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTask(any(), captor.capture());
            captor.getValue().run();
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("sendLoginPrompt text-mode dispatch race")
    class SendLoginPromptTextModeCallback {

        /**
         * Blocks a chat message while GUI mode is disabled (routing through the text-prompt
         * branch of {@link LoginProtectionListener#presentCredentialPrompt}) and captures the
         * scheduled Runnable without invoking it, so each test below can choose what state
         * (online/offline, logged-in/not) the queued callback observes when it finally runs on
         * the main thread -- mirroring {@link SendLoginPromptGuiModeCallback#captureReopenTask}
         * for the GUI branch.
         */
        private Runnable captureTextPromptTask() {
            when(config.isGuiModeEnabled()).thenReturn(false);
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.isRegistered(playerUuid)).thenReturn(true);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "message", java.util.Collections.emptySet());
            listener.onPlayerChat(event);

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTask(any(), captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("Should not send the prompt if the player went offline before the queued task ran")
        void skipsWhenOffline() {
            Runnable task = captureTextPromptTask();
            when(player.isOnline()).thenReturn(false);

            task.run();

            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should not send the prompt if the player became logged in (e.g. force-logged-in) before the queued task ran")
        void skipsWhenAlreadyLoggedIn() {
            Runnable task = captureTextPromptTask();
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            task.run();

            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should still send the prompt in the normal case where nothing changed before the queued task ran")
        void sendsNormally() {
            Runnable task = captureTextPromptTask();

            task.run();

            verify(player).sendMessage(anyString());
        }
    }

    /**
     * UltiKits/UltiLogin#41: an unauthenticated player who rides without steering is carried by the
     * vehicle, and {@link PlayerMoveEvent} is not fired for that motion (Paper fires it only for the
     * controlling rider), measured on a real server. An unauthenticated player therefore rides
     * nothing: a mount is refused, a vehicle restored after the join is left one tick later, and a
     * moving vehicle drops an unauthenticated passenger.
     */
    @Nested
    @DisplayName("an unauthenticated player rides nothing (UltiKits/UltiLogin#41)")
    class UnauthenticatedPassenger {

        private org.bukkit.entity.Minecart cart;

        @BeforeEach
        void setUpCart() {
            cart = mock(org.bukkit.entity.Minecart.class);
        }

        @Test
        @DisplayName("mounting a vehicle is refused while not logged in")
        void mountRefused() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            org.bukkit.event.entity.EntityMountEvent event = new org.bukkit.event.entity.EntityMountEvent(player, cart);

            listener.onEntityMount(event);

            assertThat(event.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("mounting is allowed once logged in")
        void mountAllowedWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            org.bukkit.event.entity.EntityMountEvent event = new org.bukkit.event.entity.EntityMountEvent(player, cart);

            listener.onEntityMount(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("a mob mounting is none of this module's business")
        void nonPlayerMountIgnored() {
            org.bukkit.entity.Zombie zombie = mock(org.bukkit.entity.Zombie.class);
            org.bukkit.event.entity.EntityMountEvent event = new org.bukkit.event.entity.EntityMountEvent(zombie, cart);

            listener.onEntityMount(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("a moving vehicle drops its unauthenticated passenger and keeps a logged-in one")
        void movingVehicleDropsUnauthenticatedPassenger() {
            Player loggedIn = UltiLoginTestHelper.createMockPlayer("Driver", UUID.randomUUID());
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.isLoggedIn(loggedIn.getUniqueId())).thenReturn(true);
            when(cart.getPassengers()).thenReturn(java.util.Arrays.<org.bukkit.entity.Entity>asList(loggedIn, player));
            org.bukkit.Location from = mock(org.bukkit.Location.class);
            org.bukkit.Location to = mock(org.bukkit.Location.class);

            listener.onVehicleMove(new org.bukkit.event.vehicle.VehicleMoveEvent(cart, from, to));

            verify(cart).removePassenger(player);
            verify(cart, never()).removePassenger(loggedIn);
        }

        @Test
        @DisplayName("one tick after joining, a player still riding and not logged in is dismounted")
        void joinDismountsTheRestoredVehicle() {
            when(config.isGuiModeEnabled()).thenReturn(false);
            listener.onPlayerJoin(new PlayerJoinEvent(player, "join message"));
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTaskLater(any(), captor.capture(), eq(1L));
            when(player.isOnline()).thenReturn(true);
            when(player.isInsideVehicle()).thenReturn(true);
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            captor.getValue().run();

            verify(player).leaveVehicle();
        }

        @Test
        @DisplayName("one tick after joining, a player logged in by their session keeps their seat")
        void joinKeepsTheSeatOfALoggedInPlayer() {
            when(config.isGuiModeEnabled()).thenReturn(false);
            listener.onPlayerJoin(new PlayerJoinEvent(player, "join message"));
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTaskLater(any(), captor.capture(), eq(1L));
            when(player.isOnline()).thenReturn(true);
            when(player.isInsideVehicle()).thenReturn(true);
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            captor.getValue().run();

            verify(player, never()).leaveVehicle();
        }
    }

    @Nested
    @DisplayName("onPlayerJoin GUI-mode delayed callback (refusal path: unauthenticated join popup)")
    class OnPlayerJoinGuiModeCallback {

        /**
         * Triggers onPlayerJoin with GUI mode enabled, captures the Runnable scheduled via
         * runTaskLater without invoking it, so each test below can choose which tick's state
         * (online/offline, logged-in/not, session valid/not) the delayed task observes when it
         * finally runs -- the ArgumentCaptor<Runnable> capture-and-invoke idiom used for this
         * ecosystem's anonymous BukkitRunnable scheduler callbacks.
         */
        private Runnable captureDelayedTask() {
            when(config.isGuiModeEnabled()).thenReturn(true);

            listener.onPlayerJoin(new PlayerJoinEvent(player, "join message"));

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTaskLater(any(), captor.capture(), eq(20L));
            return captor.getValue();
        }

        @Test
        @DisplayName("Should schedule the GUI-open task with a 1-second (20-tick) delay when GUI mode is enabled")
        void schedulesDelayedTaskWithOneSecondDelay() {
            Runnable task = captureDelayedTask();

            assertThat(task).isNotNull();
        }

        @Test
        @DisplayName("Should not open a GUI when the player went offline before the delayed task ran")
        void skipsWhenOffline() {
            Runnable task = captureDelayedTask();
            when(player.isOnline()).thenReturn(false);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                loginGui.verifyNoInteractions();
                registerGui.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Should not reopen a GUI when the player already logged in before the delayed task ran")
        void skipsWhenAlreadyLoggedIn() {
            Runnable task = captureDelayedTask();
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                loginGui.verifyNoInteractions();
                registerGui.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Should not open a GUI when the player already gained a valid session before the delayed task ran")
        void skipsWhenSessionValid() {
            Runnable task = captureDelayedTask();
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.hasValidSession(player)).thenReturn(true);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                loginGui.verifyNoInteractions();
                registerGui.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Should open the login GUI for a registered player once the delayed task runs")
        void opensLoginGuiForRegistered() {
            Runnable task = captureDelayedTask();
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.hasValidSession(player)).thenReturn(false);
            when(loginService.isRegistered(playerUuid)).thenReturn(true);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                loginGui.verify(() -> LoginGUIPage.open(player, UltiLoginTestHelper.getMockPlugin(), loginService));
                registerGui.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Should open the register GUI for an unregistered player once the delayed task runs")
        void opensRegisterGuiForUnregistered() {
            Runnable task = captureDelayedTask();
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            when(loginService.hasValidSession(player)).thenReturn(false);
            when(loginService.isRegistered(playerUuid)).thenReturn(false);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                registerGui.verify(() -> RegisterGUIPage.open(player, UltiLoginTestHelper.getMockPlugin(), loginService));
                loginGui.verifyNoInteractions();
            }
        }
    }

    @Nested
    @DisplayName("sendLoginPrompt GUI-mode reopen callback (refusal path: reprompt after a blocked action)")
    class SendLoginPromptGuiModeCallback {

        /**
         * Blocks a chat message while GUI mode is enabled (which routes through the private
         * sendLoginPrompt -> Bukkit.getScheduler().runTask(...) reopen path) and captures the
         * scheduled Runnable without invoking it.
         */
        private Runnable captureReopenTask() {
            when(config.isGuiModeEnabled()).thenReturn(true);
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "message", java.util.Collections.emptySet());
            listener.onPlayerChat(event);

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockScheduler).runTask(any(), captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("Should schedule an immediate GUI reopen task when chat is blocked in GUI mode")
        void schedulesReopenTask() {
            Runnable task = captureReopenTask();

            assertThat(task).isNotNull();
        }

        @Test
        @DisplayName("Should not reopen the GUI if the player went offline before the reopen task ran")
        void skipsWhenOffline() {
            Runnable task = captureReopenTask();
            when(player.isOnline()).thenReturn(false);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                loginGui.verifyNoInteractions();
                registerGui.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Should not reopen the GUI if the player became logged in before the reopen task ran")
        void skipsWhenLoggedInByRunTime() {
            Runnable task = captureReopenTask();
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                loginGui.verifyNoInteractions();
                registerGui.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Should reopen the login GUI for a registered player when the reopen task runs")
        void reopensLoginGuiForRegistered() {
            Runnable task = captureReopenTask();
            when(loginService.isRegistered(playerUuid)).thenReturn(true);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                loginGui.verify(() -> LoginGUIPage.open(player, UltiLoginTestHelper.getMockPlugin(), loginService));
                registerGui.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Should reopen the register GUI for an unregistered player when the reopen task runs")
        void reopensRegisterGuiForUnregistered() {
            Runnable task = captureReopenTask();
            when(loginService.isRegistered(playerUuid)).thenReturn(false);

            try (MockedStatic<LoginGUIPage> loginGui = mockStatic(LoginGUIPage.class);
                 MockedStatic<RegisterGUIPage> registerGui = mockStatic(RegisterGUIPage.class)) {
                task.run();

                registerGui.verify(() -> RegisterGUIPage.open(player, UltiLoginTestHelper.getMockPlugin(), loginService));
                loginGui.verifyNoInteractions();
            }
        }
    }
}
