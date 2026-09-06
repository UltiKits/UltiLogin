package com.ultikits.plugins.login.listener;

import com.ultikits.plugins.login.gui.LoginGUIPage;
import com.ultikits.plugins.login.gui.RegisterGUIPage;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.EventListener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

/**
 * Listener for login protection.
 *
 * @author wisdomme
 * @version 1.1.0
 */
@EventListener
public class LoginProtectionListener implements Listener {

    private final UltiToolsPlugin plugin;
    private final LoginService loginService;
    private final Plugin bukkitPlugin;

    public LoginProtectionListener(UltiToolsPlugin plugin, LoginService loginService) {
        this.plugin = plugin;
        this.loginService = loginService;
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loginService.onPlayerJoin(player);
        
        // Open GUI if enabled (with delay for proper loading)
        if (loginService.getConfig().isGuiModeEnabled()) {
            Bukkit.getScheduler().runTaskLater(bukkitPlugin, () -> {
                if (player.isOnline() && !loginService.isLoggedIn(player.getUniqueId())) {
                    // Check if already has valid session (handled in onPlayerJoin)
                    if (loginService.hasValidSession(player)) {
                        return;
                    }
                    
                    if (loginService.isRegistered(player.getUniqueId())) {
                        LoginGUIPage.open(player, plugin, loginService);
                    } else {
                        RegisterGUIPage.open(player, plugin, loginService);
                    }
                }
            }, 20L); // 1 second delay for compatibility with skin plugins
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        loginService.onPlayerQuit(event.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only cancel if player should be blocked AND actually moved (not just looked around)
        if (shouldCancel(event.getPlayer()) &&
                (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                 event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                 event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
            event.setTo(event.getFrom());
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (shouldCancel(event.getPlayer())) {
            event.setCancelled(true);
            sendLoginPrompt(event.getPlayer());
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!loginService.isLoggedIn(player.getUniqueId())) {
            if (!loginService.isCommandAllowed(event.getMessage())) {
                event.setCancelled(true);
                sendLoginPrompt(player);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            // Allow GUI interactions for login/register GUI
            if (!loginService.isLoggedIn(player.getUniqueId())) {
                String title = event.getView().getTitle();
                // Allow clicking in login/register GUI
                if (!title.contains("密码") && !title.contains("登录") && !title.contains("注册")) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            // Allow opening login/register GUI
            if (!loginService.isLoggedIn(player.getUniqueId())) {
                String title = event.getView().getTitle();
                if (!title.contains("密码") && !title.contains("登录") && !title.contains("注册")) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            cancelIfNotLoggedIn((Player) event.getEntity(), event);
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (shouldCancel(player)) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamageEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            cancelIfNotLoggedIn((Player) event.getDamager(), event);
        }
    }
    
    /**
     * Check if player actions should be cancelled.
     */
    private boolean shouldCancel(Player player) {
        return !loginService.isLoggedIn(player.getUniqueId());
    }
    
    /**
     * Cancel event if player not logged in.
     */
    private void cancelIfNotLoggedIn(Player player, Cancellable event) {
        if (shouldCancel(player)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Send login prompt to player.
     */
    private void sendLoginPrompt(Player player) {
        presentCredentialPrompt(player, plugin, loginService, bukkitPlugin);
    }

    /**
     * Present the login or register credential prompt -- the GUI page or a plain chat message,
     * chosen by {@code loginService}'s config and the player's current registration state.
     * <p>
     * {@code static} and package-visible via a full parameter list (rather than an instance
     * method reached through a bean reference) so {@link LoginService#presentCredentialPrompt
     * (Player)} can call this exact same branching after an administrative credential change
     * (Codex PR #18 thread 3945030004, round 4) without duplicating it a second time, and
     * without introducing a circular bean dependency between the listener and the service --
     * {@code LoginService} already has {@code plugin} and {@code bukkitPlugin} as constructor-
     * injected fields, so it can call this like any other static utility. This class's own
     * mock-based unit tests are unaffected: {@link #sendLoginPrompt(Player)} still exercises the
     * identical branching with the identical field values, just via this extracted method.
     * <p>
     * Both branches are dispatched onto the main thread via {@link #dispatchOnMainThread}:
     * inventory APIs are not thread-safe, and a caller revoking a session (e.g. an admin
     * command) is not guaranteed to already be on the main thread. Round 5 (13-REVIEW-UltiLogin
     * .md, own deep review of bcadfb5, Info finding): the text branch used to send synchronously
     * on whatever thread the caller was on, safe only because every current caller of {@code
     * LoginService.invalidateSession(UUID)} happens to be a synchronous command body. Dispatching
     * it the same way as the GUI branch removes that latent assumption, so a future async caller
     * (e.g. a WebSocket-driven remote admin action) cannot call a Bukkit player API off-thread
     * through this path.
     *
     * @param player the player to prompt; must be online
     * @param plugin the UltiTools plugin instance, passed through to the GUI pages
     * @param loginService the login service to read registration/login state and config from
     * @param bukkitPlugin the framework plugin instance the scheduler task is registered under
     */
    public static void presentCredentialPrompt(Player player, UltiToolsPlugin plugin,
            LoginService loginService, Plugin bukkitPlugin) {
        if (loginService.getConfig().isGuiModeEnabled()) {
            // Reopen GUI
            dispatchOnMainThread(player, plugin, bukkitPlugin, () -> {
                if (player.isOnline() && !loginService.isLoggedIn(player.getUniqueId())) {
                    if (loginService.isRegistered(player.getUniqueId())) {
                        LoginGUIPage.open(player, plugin, loginService);
                    } else {
                        RegisterGUIPage.open(player, plugin, loginService);
                    }
                }
            });
        } else {
            // Send text prompt
            dispatchOnMainThread(player, plugin, bukkitPlugin, () -> {
                if (loginService.isRegistered(player.getUniqueId())) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        loginService.getConfig().getLoginPrompt()));
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        loginService.getConfig().getRegisterPrompt()));
                }
            });
        }
    }

    /**
     * Run {@code task} on the main thread, without letting {@link Bukkit#getScheduler()}'s
     * unchecked exception on a disabling plugin escape to the caller.
     * <p>
     * Round 6 (13-REVIEW-UltiLogin.md, own review of 29ba589, Warning finding): both branches of
     * {@link #presentCredentialPrompt} call {@code Bukkit.getScheduler().runTask(bukkitPlugin,
     * ...)} unconditionally. Bukkit's scheduler validates {@code plugin.isEnabled()} before
     * accepting a task and throws an unchecked exception if the owning plugin is disabled at the
     * moment {@code runTask} is called -- and nothing upstream of this method (including {@code
     * LoginService.invalidateSession}/{@code forceReauthenticationIfOnline}) catches it. Three
     * rules close that gap and its symmetric restriction against needlessly hopping threads:
     * <ol>
     *   <li>Already on the main thread ({@link Bukkit#isPrimaryThread()}) -- run {@code task}
     *   inline. Scheduling a task from the main thread to run on the main thread only adds a tick
     *   of latency for no safety benefit, and this is also what keeps a caller that is already the
     *   main thread from having to depend on the scheduler validating {@code bukkitPlugin} at
     *   all.</li>
     *   <li>Off the main thread and {@code bukkitPlugin} is still enabled -- schedule via {@link
     *   Bukkit#getScheduler()}{@code .runTask(...)}, exactly as before.</li>
     *   <li>Off the main thread and {@code bukkitPlugin} is disabled -- the plugin is disabling
     *   (or already disabled) and cannot usefully prompt a player through its own scheduler
     *   anyway; skip and log a warning instead of letting the scheduler's unchecked exception
     *   propagate out of the credential-invalidation call chain that triggered this prompt.</li>
     * </ol>
     *
     * @param player the player the prompt is for, used only for the skip warning's message
     * @param plugin the UltiTools plugin instance, used to log the skip warning
     * @param bukkitPlugin the framework plugin instance the scheduler task would be registered
     *                     under
     * @param task the prompt body to run
     */
    private static void dispatchOnMainThread(Player player, UltiToolsPlugin plugin,
            Plugin bukkitPlugin, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (bukkitPlugin.isEnabled()) {
            Bukkit.getScheduler().runTask(bukkitPlugin, task);
        } else {
            plugin.getLogger().warn("Skipped presenting the credential prompt to "
                + player.getName() + " because the plugin is disabling");
        }
    }
}
