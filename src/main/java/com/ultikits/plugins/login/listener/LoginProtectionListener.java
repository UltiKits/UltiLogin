package com.ultikits.plugins.login.listener;

import com.ultikits.plugins.login.gui.LoginGUIPage;
import com.ultikits.plugins.login.gui.RegisterGUIPage;
import com.ultikits.plugins.login.config.LoginConfig;
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
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

/**
 * Listener for login protection.
 *
 * <h2>Why each blocked interaction needs its own handler</h2>
 *
 * Bukkit registers and delivers an event on the {@code HandlerList} of its <em>registration
 * class</em> — the nearest class, starting at the event's own class and walking up, that
 * <em>declares</em> a static {@code getHandlerList()}. A subclass declaring its own
 * {@code HandlerList} therefore has a separate dispatch list, and a handler registered for the
 * superclass never receives it.
 * <p>
 * That is what UltiKits/UltiLogin#24 reported: an unauthenticated player could still equip an item
 * onto an armor stand even though {@link PlayerInteractEntityEvent} was handled. Measured in the
 * {@code paper-api} this module compiles against (1.21.11-R0.1-SNAPSHOT, {@code javap -p}):
 * {@link PlayerInteractEntityEvent}, {@link PlayerInteractAtEntityEvent} and
 * {@link PlayerArmorStandManipulateEvent} each declare their own {@code HANDLER_LIST}, and the
 * latter two extend {@link PlayerInteractEntityEvent} <em>directly</em> — they are siblings, not a
 * chain. So covering only the interact-at event (which #24's suggested fix named) would still not
 * have covered the armor-stand event itself.
 * <p>
 * Because that gap cannot be seen by reading this class, the set of events an unauthenticated player
 * must not act through is declared as data in
 * {@code LoginProtectionEventCoverageTest}, which asserts this listener against it and fails when a
 * future {@code paper-api} adds a new diverted subclass of anything handled here. Add a handler
 * below and the entry there together.
 *
 * <h2>Sibling listener</h2>
 *
 * {@link LoginProtectionPaperListener} carries the handlers for events in Paper's own namespaces, kept
 * in a separate class on purpose. That class's javadoc has the measurement: one handler whose parameter
 * type is missing at runtime empties the <em>whole</em> listener's handler map, so isolating the
 * Paper-only events bounds that failure to them. Both classes are in the coverage contract's
 * {@code PROTECTION_LISTENERS}, so it does not matter to the tests which file a handler lives in.
 *
 * <h2>One event covered defensively</h2>
 *
 * {@link #onInventoryDrag} is here even though no reachable bypass was demonstrated for it: an
 * unreachable handler in a security net costs nothing, while a wrong premise costs a bypass.
 * <strong>Its presence is not evidence that the bypass existed.</strong> Its javadoc records what
 * was and was not measured.
 * <p>
 * {@link #onSignChange} and {@link #onPlayerEditBook} were both in this group until their premises
 * were measured and disproved — {@code onSignChange} by the {@code PLUGIN} sign-open finding,
 * {@code onPlayerEditBook} by a real-server run of {@code
 * ultilogin.protection.world-interaction-block}. Both are load-bearing, each is the only thing
 * refusing its write, and neither may be deleted as unreachable. See their own javadoc.
 *
 * <h2>Priority, and who gets the last word</h2>
 *
 * Every handler here runs at {@link EventPriority#LOWEST} with no {@code ignoreCancelled}, which is
 * this class's long-standing convention and is deliberate: it lets another plugin see these events
 * after this listener and make its own decision. The consequence, recorded so it is not a surprise, is
 * that a plugin listening at {@code HIGH}/{@code HIGHEST} can call {@code setCancelled(false)} and
 * undo any protection here. Raising the priority would stop that, but would also start overriding
 * other plugins' deliberate allowances, so it is not changed unilaterally.
 *
 * <h2>Events deliberately left uncovered</h2>
 *
 * Three subclasses of events handled here declare their own {@code HandlerList} and are left
 * uncovered on purpose. The same three, with the same reasons, are listed in that test's
 * {@code DELIBERATELY_UNCOVERED} map:
 * <ul>
 *   <li>{@link PlayerTeleportEvent} — this module teleports unauthenticated players itself
 *   ({@code LoginService#applyNoSessionProtections} sends them to the configured spawn while still
 *   unauthenticated, recording the original location to restore on login). Cancelling teleports for
 *   unauthenticated players would break this module's own spawn protection, and would stop an
 *   operator rescuing a stuck player with {@code /tp}. A teleport is not player-initiated world
 *   mutation, and {@link #onPlayerMove} already freezes walking.</li>
 *   <li>{@link PlayerPortalEvent} — a subclass of {@link PlayerTeleportEvent}, excluded for that
 *   reason plus one of its own: with movement frozen, the only way to reach it is having quit inside
 *   a portal block, where cancelling it would trap the player until they log in from inside the
 *   portal.</li>
 *   <li>{@code AsyncPlayerChatPreviewEvent} — a preview renders text and mutates no world or
 *   inventory state. Chat itself is already cancelled by {@link #onPlayerChat}, and the class is
 *   {@code @Deprecated} in {@code paper-api} 1.21.11.</li>
 * </ul>
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
                if (!isCredentialGuiTitle(title)) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    /**
     * Cancel an inventory drag for an unauthenticated player.
     * <p>
     * Found by the same sweep as UltiKits/UltiLogin#24, and justified on the same structural ground:
     * {@link InventoryDragEvent} is a sibling of {@link InventoryClickEvent} — both extend
     * {@code InventoryInteractEvent} and both declare their own {@code HandlerList} — so
     * {@link #onInventoryClick} never receives a drag, and a drag moves items.
     * <p>
     * <strong>Covered defensively: no reachable drag bypass was demonstrated.</strong> An earlier
     * revision of this javadoc, and of the changelog entry, implied one was live. A review measured
     * otherwise, and the measurement points the other way on both branches. A meaningful drag needs
     * a non-empty cursor, and the only way to load the cursor is a pick-up click: with {@code
     * gui-mode.enabled: false} (the shipped default) {@link #onInventoryClick} refuses every click,
     * so the cursor can never be loaded; with it {@code true} the click is allowed inside the
     * credential GUI, but obliviate-invs cancels <em>every</em> drag while one of its GUIs is open
     * ({@code InvListener#onDrag} is {@code setCancelled(!gui.onDrag(event))} and the default
     * {@code onDrag} returns {@code false}). So it is refused explicitly rather than left resting on
     * the expectation that the click guard and a third-party library make it unreachable. It is now
     * the only handler here in that register: {@link #onPlayerEditBook} was its peer until a
     * real-server run measured that premise and disproved it.
     * <p>
     * Unlike {@link #onInventoryClick} this deliberately does <em>not</em> mirror the credential-GUI
     * title allowance. A drag cannot enter a digit into {@code LoginGUIPage}/{@code RegisterGUIPage},
     * so allowing it buys no functionality; and because the credential GUI's view includes the
     * player's own inventory rows, allowing drags there would let an unauthenticated player
     * rearrange their items and push stacks toward the GUI's container slots. Cancelling
     * unconditionally is both simpler and strictly safer.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            cancelIfNotLoggedIn((Player) event.getWhoClicked(), event);
        }
    }

    /**
     * Whether {@code title} is one of the titles the credential GUI is opened with: the login title,
     * the register title, or the confirm title {@code RegisterGUIPage} switches to. Compared with the
     * resolved titles, by their words, so the allowance holds in every language and for an operator's
     * own titles (UltiKits/UltiLogin#20). It used to test whether
     * the title contained 密码, 登录 or 注册: that refused every keypad click under an English title,
     * and let an unauthenticated player click in any other inventory whose title held one of those
     * words.
     */
    private boolean isCredentialGuiTitle(String title) {
        if (title == null) {
            return false;
        }
        LoginConfig config = loginService.getConfig();
        String shown = ChatColor.stripColor(title);
        return shown.equals(words(config.getGuiLoginTitle()))
                || shown.equals(words(config.getGuiRegisterTitle()))
                || shown.equals(words(config.getGuiConfirmTitle()));
    }

    /**
     * A title as it reads: colour codes applied, then stripped. Compared without its colour codes,
     * because a server may echo a title's codes rewritten.
     */
    private static String words(String text) {
        return text == null ? null : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            // Allow opening login/register GUI
            if (!loginService.isLoggedIn(player.getUniqueId())) {
                String title = event.getView().getTitle();
                if (!isCredentialGuiTitle(title)) {
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

    /**
     * Cancel a precise-position entity interaction ("interact at") for an unauthenticated player.
     * <p>
     * {@link PlayerInteractAtEntityEvent} extends {@link PlayerInteractEntityEvent} but declares its
     * own {@code HandlerList}, so {@link #onPlayerInteractEntity} never receives it
     * (UltiKits/UltiLogin#24 — see this class's javadoc for the dispatch rule).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }

    /**
     * Cancel armor-stand equipping and un-equipping for an unauthenticated player — the interaction
     * UltiKits/UltiLogin#24 was reported against.
     * <p>
     * {@link PlayerArmorStandManipulateEvent} is a sibling of {@link PlayerInteractAtEntityEvent},
     * not a subclass of it: both extend {@link PlayerInteractEntityEvent} directly and both declare
     * their own {@code HandlerList}. Cancelling the interact-at event above therefore does not make
     * this handler redundant — it would only stop the equip when the server happens to fire
     * interact-at first, which is a behavioural assumption about the server rather than a structural
     * guarantee. This handler is what closes the reported hole.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }

    /**
     * Cancel an off-hand swap for an unauthenticated player.
     * <p>
     * Found by the same sweep as #24, and the same shape of hole: the swap arrives as its own client
     * intent with its own {@code HandlerList}, and is not preceded by any event this listener
     * handles — no {@link PlayerInteractEvent}, no {@link InventoryClickEvent} — so nothing else here
     * stops an unauthenticated player rearranging their hands.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }

    /**
     * Cancel writing or signing a book for an unauthenticated player.
     * <p>
     * <strong>This handler is load-bearing — do not delete it as unreachable.</strong> An earlier
     * revision of this javadoc called it defensive, on the premise that the client opens the book
     * editor only after a server packet following an uncancelled {@link PlayerInteractEvent}, so
     * that {@link #onPlayerInteract} would already have stopped everything downstream. A
     * real-server run of {@code ultilogin.protection.world-interaction-block} measured that
     * premise and it is false: an unauthenticated player right-clicking a writable book at air
     * <em>does</em> get the editor, and the edit packet then arrives at the server on its own.
     * <p>
     * Three measurements on the server that ran it (Paper 1.21.11, mojang-mapped, {@code javap -c}):
     * <ul>
     *   <li>{@code Player#openItemGui(ItemStack, InteractionHand)} — the only thing
     *   {@code WritableBookItem#use} calls to open the editor — has an empty body on the server
     *   ({@code 0: return}). The screen is opened by the client's own override during its local
     *   prediction of item use, so the server neither opens it, sends it, nor observes it, and
     *   cancelling {@link PlayerInteractEvent} cannot suppress it. There is also no book-editor-open
     *   event to cancel: {@code paper-api} 1.21.11 carries seven book events (recipe book, lectern,
     *   and this one) and none of them is an "editor opened" — unlike signs, which have
     *   {@code PlayerOpenSignEvent}, which is why {@link LoginProtectionPaperListener#onPlayerOpenSign}
     *   can prevent that editor and nothing can prevent this one.</li>
     *   <li>{@code ServerGamePacketListenerImpl#handleEditBook} reaches
     *   {@code CraftEventFactory#handleEditBookEvent} — which fires this event unconditionally —
     *   for a plain writable book in a hotbar slot. Its three earlier refusal sites were all
     *   excluded by the recorded state of the run: the book-size and rate-limit checks disconnect
     *   the player rather than refusing silently (the player stayed connected), and the
     *   {@code isHotbarSlot(slot) || slot == 40} check passed because the book was in hotbar slot 2.
     *   {@code signBook}'s own {@code has(WRITABLE_BOOK_CONTENT)} guard passes too:
     *   {@code Items.WRITABLE_BOOK} registers that component as a <em>default</em>, and
     *   {@code PatchedDataComponentMap#get} falls back to the prototype when the patch has no entry,
     *   so it is present on a book with no NBT at all.</li>
     *   <li>On cancellation {@code handleEditBookEvent} skips the write entirely and calls
     *   {@code containerMenu.forceSlot(...)} to resync the slot — which is exactly what the run
     *   observed: the book came back {@code writable_book}, count 1, with no text component.</li>
     * </ul>
     * So this handler is the <em>only</em> thing refusing that write. It was also the only handler
     * on this event anywhere in that deployment (all 22 installed plugin jars scanned, nested jars
     * included; {@code PlayerEditBookEvent} appears in this class and nowhere else).
     * <p>
     * The client-side editor opening is a documented limitation, not a defect — see the
     * {@code ultilogin.protection.world-interaction-block} rows in {@code FEATURES.md} and
     * {@code UAT-CHECKLIST.md}. <strong>Do not try to close it with a client-side suppression</strong>;
     * there is no server-side hook to hang one on.
     * <p>
     * {@link PlayerEditBookEvent} declares its own {@code HandlerList} and is not a subclass of
     * anything else handled here, so it is a separate client-intent entry point rather than an
     * instance of #24's diverted-subclass shape.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerEditBook(PlayerEditBookEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }

    /**
     * Cancel writing a sign for an unauthenticated player.
     * <p>
     * <strong>This handler is load-bearing — do not delete it as unreachable.</strong> An earlier
     * revision of this javadoc called it defensive, on the premise that a sign editor only ever opens
     * after an uncancelled {@link PlayerInteractEvent} or
     * {@link org.bukkit.event.block.BlockPlaceEvent}. That premise was measured, and it is false:
     * {@link org.bukkit.event.player.PlayerSignOpenEvent}'s {@code Cause} enum has four constants —
     * {@code INTERACT}, {@code PLACE}, <strong>{@code PLUGIN}</strong> and {@code UNKNOWN} — and
     * {@code HumanEntity#openSign(Sign, Side)} is public API ("Opens an editor window for the
     * specified sign"). {@code INTERACT} and {@code PLACE} are both already refused here; {@code
     * PLUGIN} is not, and cannot be, because it follows no player interaction at all. So any
     * co-installed plugin that opens a sign editor for a joining player reaches this event, and this
     * handler is the only thing refusing the write.
     * <p>
     * {@link LoginProtectionPaperListener#onPlayerOpenSign} refuses the editor at the point it opens;
     * the two are complementary rather than redundant — that one prevents, this one is the backstop.
     * <p>
     * {@code SignChangeEvent#getPlayer()} is annotated {@code @NotNull}, so the null branch below
     * should be unreachable. It is there anyway because the annotation is not enforced at runtime and
     * the wrong side of that bet is fail-open: an exception thrown out of a handler is logged and
     * swallowed by Bukkit, leaving the event <em>uncancelled</em>.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            event.setCancelled(true);
            return;
        }
        cancelIfNotLoggedIn(player, event);
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
     * command) is not guaranteed to already be on the main thread. The text branch used to send synchronously
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
                    UUID uuid = player.getUniqueId();
                    // Round 9 (Codex PR #18 thread 3946574852, P2): mark this player as
                    // mid-transition before opening the new credential GUI. In real Bukkit,
                    // opening a new inventory implicitly closes whatever the player currently has
                    // open, which runs that GUI's own onClose reopen hook synchronously, on this
                    // same call -- without this marker, that hook could schedule its own reopen
                    // of the GUI being replaced, fighting (in the unregister case, permanently)
                    // the GUI this method is deliberately opening. Cleared in the finally block
                    // once the new GUI has actually been opened, so it never leaks past this call.
                    // Round 10 (Codex PR #18 thread 3946842965): a GUI the player already closed
                    // for an unrelated reason may have a delayed reopen queued (see
                    // LoginGUIPage/RegisterGUIPage#onClose). Cancel it before opening this fresh
                    // GUI so it cannot fire afterward and stack a second credential GUI on top of
                    // this one, whose own onClose would then queue yet another reopen in turn.
                    loginService.cancelPendingCredentialGuiReopen(uuid);
                    loginService.beginCredentialGuiTransition(uuid);
                    try {
                        if (loginService.isRegistered(uuid)) {
                            LoginGUIPage.open(player, plugin, loginService);
                        } else {
                            RegisterGUIPage.open(player, plugin, loginService);
                        }
                    } finally {
                        loginService.endCredentialGuiTransition(uuid);
                    }
                }
            });
        } else {
            // Send text prompt
            dispatchOnMainThread(player, plugin, bukkitPlugin, () -> {
                // Round 12 (Codex PR #18 thread 3947572910, P3): unlike the GUI branch above,
                // this queued callback used to send unconditionally, with no re-check of the
                // player's state at execution time. AsyncPlayerChatEvent already runs off the
                // main thread, so this callback is *always* queued for a later tick here, not
                // just occasionally -- if the player was force-logged-in (or otherwise
                // authenticated) in the gap between queuing and this tick, it still sent the
                // login/register instruction to an already-authenticated player. Re-check
                // exactly what the GUI branch checks before sending anything.
                if (player.isOnline() && !loginService.isLoggedIn(player.getUniqueId())) {
                    if (loginService.isRegistered(player.getUniqueId())) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            loginService.getConfig().getLoginPrompt()));
                    } else {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            loginService.getConfig().getRegisterPrompt()));
                    }
                }
            });
        }
    }

    /**
     * Run {@code task} on the main thread, without letting {@link Bukkit#getScheduler()}'s
     * unchecked exception on a disabling plugin escape to the caller.
     * <p>
     * Both branches of {@link #presentCredentialPrompt} call {@code
     * Bukkit.getScheduler().runTask(bukkitPlugin, ...)} unconditionally. Bukkit's scheduler
     * validates {@code plugin.isEnabled()} before accepting a task and throws an unchecked
     * exception if the owning plugin is disabled at the moment {@code runTask} is called -- and
     * nothing upstream of this method (including {@code LoginService.invalidateSession}/{@code
     * forceReauthenticationIfOnline}) catches it. Three rules close that gap and its symmetric
     * restriction against needlessly hopping threads:
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
     * <p>
     * Round 8 (Codex PR #18, thread 3946414499): widened from {@code private} to {@code public}
     * so {@code LoginService}'s own {@code applyNoSessionProtections(Player)} -- the blind-effect
     * and spawn-teleport reapplication shared with {@link
     * com.ultikits.plugins.login.service.LoginService#onPlayerJoin(Player)} -- can dispatch
     * through the identical main-thread rules, since potion effects and teleports are exactly as
     * main-thread-only as the GUI/text prompt this method already guards.
     *
     * @param player the player the prompt is for, used only for the skip warning's message
     * @param plugin the UltiTools plugin instance, used to log the skip warning
     * @param bukkitPlugin the framework plugin instance the scheduler task would be registered
     *                     under
     * @param task the prompt body to run
     */
    public static void dispatchOnMainThread(Player player, UltiToolsPlugin plugin,
            Plugin bukkitPlugin, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (bukkitPlugin.isEnabled()) {
            Bukkit.getScheduler().runTask(bukkitPlugin, task);
        } else {
            plugin.getLogger().warn(plugin.i18n("log_prompt_skipped_disabling")
                .replace("{PLAYER}", player.getName()));
        }
    }
}
