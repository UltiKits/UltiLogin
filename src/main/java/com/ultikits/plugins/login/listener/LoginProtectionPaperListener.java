package com.ultikits.plugins.login.listener;

import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.annotations.EventListener;
import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The stable Paper recipe-book protection, available throughout the measured event matrix.
 *
 * <p>A missing parameter type makes Bukkit's listener-method reflection fail for the entire class
 * (measured in JavaPluginLoader#createRegisteredListeners, Paper API 1.21.11). The framework also
 * reflects methods before filtering component annotations. Item-pick, sign-open and equipment-swap
 * therefore have independent listeners outside the component-scan root, constructed only after
 * UltiLogin probes their event classes. A missing hook cannot cost this recipe-book protection.
 *
 * <p>All protections run at LOWEST without ignoreCancelled, preserving the existing convention:
 * another plugin running later can deliberately undo the cancellation.
 */
@EventListener
public class LoginProtectionPaperListener implements Listener {
    private final LoginService loginService;

    public LoginProtectionPaperListener(LoginService loginService) {
        this.loginService = loginService;
    }

    /** Cancel a recipe-book click, which moves items into the crafting grid. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRecipeBookClick(PlayerRecipeBookClickEvent event) {
        Player player = event.getPlayer();
        if (player == null
                || (!LoginProtectionListener.isPluginNpc(player) && !loginService.isLoggedIn(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }
}
