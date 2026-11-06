package com.ultikits.plugins.logincompat;

import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.plugins.login.service.LoginService;
import io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The optional dedicated equipment-slot-swap protection (UltiKits/UltiLogin#83).
 *
 * <p>This class must stay outside {@code com.ultikits.plugins.login}, the module's component-scan
 * root. The scanner reflects methods even on classes without a component annotation. Only after
 * probing the event's presence does UltiLogin construct and register this plain listener; it also
 * unregisters it explicitly on unload. Ordinary right-click equipment use remains protected by
 * the core PlayerInteractEvent handler on servers without this dedicated event.
 */
public class EquipmentSwapProtectionListener implements Listener {
    private final LoginService loginService;

    public EquipmentSwapProtectionListener(LoginService loginService) {
        this.loginService = loginService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSwapWithEquipmentSlot(PlayerSwapWithEquipmentSlotEvent event) {
        Player player = event.getPlayer();
        if (player == null
                || (!LoginProtectionListener.isPluginNpc(player)
                && !loginService.isLoggedIn(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }
}
