package com.ultikits.plugins.compat.login;

import com.ultikits.plugins.login.listener.LoginProtectionListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;

/**
 * Modern mount hook, outside the module's component-scan root and loaded only after its event probe.
 * The injected core is the existing bean, not another listener with an independent join-tick set.
 * Its package must not start with any declared scan root: older framework JAR scanners match raw
 * prefixes, so {@code com.ultikits.plugins.compat.login} avoids the {@code com.ultikits.plugins.login}
 * prefix that {@code com.ultikits.plugins.logincompat} accidentally matched.
 */
public class MountProtectionListener implements Listener {
    private final LoginProtectionListener protection;

    public MountProtectionListener(LoginProtectionListener protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityMount(EntityMountEvent event) {
        protection.protectMount(event.getEntity(), event);
    }
}
