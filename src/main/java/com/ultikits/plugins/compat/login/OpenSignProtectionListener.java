package com.ultikits.plugins.compat.login;

import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.plugins.login.service.LoginService;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Optional sign-editor-open protection, outside the component-scan root and guarded by an event probe.
 * A PLUGIN open need not follow a player interaction; SignChangeEvent remains the separate write guard
 * even on servers with no open hook. The unrelated deprecated Bukkit PlayerSignOpenEvent has its own
 * HandlerList and is not an equivalent on Paper 1.19.2/1.19.4, where neither open event exists.
 * Which event a server actually fires still needs version-specific real-server verification (#36).
 * Its package must not start with any declared scan root: older framework JAR scanners match raw
 * prefixes, so {@code com.ultikits.plugins.compat.login} avoids the {@code com.ultikits.plugins.login}
 * prefix that {@code com.ultikits.plugins.logincompat} accidentally matched.
 */
public class OpenSignProtectionListener implements Listener {
    private final LoginService loginService;

    public OpenSignProtectionListener(LoginService loginService) {
        this.loginService = loginService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerOpenSign(PlayerOpenSignEvent event) {
        Player player = event.getPlayer();
        if (player == null
                || (!LoginProtectionListener.isPluginNpc(player) && !loginService.isLoggedIn(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }
}
