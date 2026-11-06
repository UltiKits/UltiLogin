package com.ultikits.plugins.logincompat;

import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.plugins.login.service.LoginService;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Optional item-pick hook, outside the component-scan root and constructed only after its event probe.
 * Its subclasses use this event's HandlerList, so the one handler covers block and entity picks.
 * No older equivalent covers the pick-slot path on the measured Paper 1.19.2/1.19.4 builds.
 */
public class PickItemProtectionListener implements Listener {
    private final LoginService loginService;

    public PickItemProtectionListener(LoginService loginService) {
        this.loginService = loginService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickItem(PlayerPickItemEvent event) {
        Player player = event.getPlayer();
        if (player == null
                || (!LoginProtectionListener.isPluginNpc(player) && !loginService.isLoggedIn(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }
}
