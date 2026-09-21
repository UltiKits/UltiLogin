package com.ultikits.plugins.login.listener;

import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.annotations.EventListener;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Login protection for the interaction events that live in Paper's own event namespaces
 * ({@code io.papermc.paper.event.*}, {@code com.destroystokyo.paper.event.*}), kept deliberately
 * separate from {@link LoginProtectionListener}.
 *
 * <h2>Why these are not in {@code LoginProtectionListener}</h2>
 *
 * <strong>So that a server missing one of these classes loses only these four handlers instead of all
 * twenty-one core ones.</strong> This is measured, not precautionary. Bukkit's
 * {@code JavaPluginLoader#createRegisteredListeners} wraps its {@code getMethods()} /
 * {@code getDeclaredMethods()} calls in a {@code catch (NoClassDefFoundError)} that logs one
 * {@code severe} line and then <em>returns early</em> — verified in the bytecode of the resolved
 * {@code paper-api} 1.21.11 ({@code javap -c}: the exception table covers offsets 36–159 with handler
 * 162, which reaches {@code Logger.severe} and {@code areturn} at 202). The listener's whole handler
 * map comes back empty.
 *
 * <p>So a single handler whose parameter type is absent at runtime disables every other handler in the
 * same class. These four events are Paper-only and comparatively recent, while this module declares
 * Bukkit {@code api-version} compatibility well below the version it compiles against, so that is a
 * live risk rather than a hypothetical one — and its blast radius is exactly the catastrophic outcome
 * gate 1's WR-02 describes, reached by a version difference instead of a refactor. The framework
 * registers listener beans one at a time ({@code ListenerManager#registerAll} calls
 * {@code registerEvents} per bean, `ListenerManager.java:62`), so putting them here bounds the damage
 * to this class.
 *
 * <p>This is a deliberate divergence from gate 1 WR-03's suggested fix direction ("add
 * {@code onPlayerPickItem(PlayerPickItemEvent)}" to the existing listener), taken because of the
 * measurement above. The coverage is the same; the failure mode is not.
 *
 * <h2>What these cover</h2>
 *
 * Each is a distinct client intent with its own {@code HandlerList} that descends from no event
 * {@link LoginProtectionListener} handles, so nothing there receives it:
 * <ul>
 *   <li>{@link PlayerPickItemEvent} — middle-click "pick item". Its javadoc: after the event "the
 *   contents of the source and the target slot will be swapped, and the currently selected hotbar slot
 *   of the player will be set to the target slot". It is {@code abstract} and its two concrete
 *   subclasses ({@code PlayerPickBlockEvent}, {@code PlayerPickEntityEvent}) declare no
 *   {@code HandlerList} of their own, so this one handler receives both.</li>
 *   <li>{@link PlayerSwapWithEquipmentSlotEvent} — swapping the held item with an equipment slot.</li>
 *   <li>{@link PlayerRecipeBookClickEvent} — a recipe-book click, which moves items into the crafting
 *   grid of an already-open inventory.</li>
 *   <li>{@link PlayerOpenSignEvent} — refuses the sign editor at the point it would open, rather than
 *   catching the write afterwards in {@link LoginProtectionListener#onSignChange}. Both are kept: this
 *   one prevents the editor, that one is the backstop if a path reaches the write without this event.</li>
 * </ul>
 *
 * Like every handler in {@link LoginProtectionListener}, these run at {@link EventPriority#LOWEST}
 * without {@code ignoreCancelled}, which means a plugin listening later can still call
 * {@code setCancelled(false)} and undo them — the class-wide property recorded in that class's javadoc.
 *
 * @author wisdomme
 */
@EventListener
public class LoginProtectionPaperListener implements Listener {

    private final LoginService loginService;

    public LoginProtectionPaperListener(LoginService loginService) {
        this.loginService = loginService;
    }

    /**
     * Cancel a middle-click item pick for an unauthenticated player — it swaps two of their own
     * inventory slots and changes the selected hotbar slot, and in creative it creates the stack.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickItem(PlayerPickItemEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }

    /**
     * Cancel swapping the held item with an equipment slot for an unauthenticated player.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSwapWithEquipmentSlot(PlayerSwapWithEquipmentSlotEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }

    /**
     * Cancel a recipe-book click for an unauthenticated player — it moves items into the crafting grid.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRecipeBookClick(PlayerRecipeBookClickEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }

    /**
     * Refuse to open a sign editor for an unauthenticated player.
     * <p>
     * This is the front half of the sign protection; {@link LoginProtectionListener#onSignChange} is
     * the back half. Neither is redundant: the {@code PLUGIN} cause means another plugin can open a
     * sign editor without any player interaction, so the write has to be refused too.
     * <p>
     * <strong>Known limitation, deliberate — do not "fix" it by adding a second handler.</strong> The
     * API also carries {@code org.bukkit.event.player.PlayerSignOpenEvent}, an <em>unrelated sibling</em>
     * of this event: both extend {@code PlayerEvent} directly and each declares its own
     * {@code HandlerList}, so a handler for one is never delivered the other. Which of the two a real
     * server dispatches on a plugin-initiated open is <em>not established</em>, and the {@code org.bukkit}
     * one is {@code @Deprecated(forRemoval = true)} — so a handler for it would be a standing dependency
     * on a class whose removal empties this whole listener's handler map (see this class's own javadoc for
     * that measurement), possibly for an event that never fires. A declared protection that does not
     * execute is the defect class this work exists to remove. If a server does dispatch that type, the
     * editor opens for an unauthenticated player and {@link LoginProtectionListener#onSignChange} still
     * refuses the write, so no sign text is written either way. Deferred pending the measurement in
     * {@code UltiKits/UltiLogin#36}.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerOpenSign(PlayerOpenSignEvent event) {
        cancelIfNotLoggedIn(event.getPlayer(), event);
    }

    /**
     * Cancel {@code event} unless {@code player} is authenticated.
     * <p>
     * A {@code null} player cancels, rather than throwing. None of these four events documents a
     * nullable player, but an unchecked exception thrown out of a handler is logged and swallowed by
     * Bukkit and leaves the event <em>uncancelled</em> — so the only failure mode worth having here is
     * the closed one.
     */
    private void cancelIfNotLoggedIn(Player player, Cancellable event) {
        if (player == null || !loginService.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
