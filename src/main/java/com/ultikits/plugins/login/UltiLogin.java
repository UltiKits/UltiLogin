package com.ultikits.plugins.login;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

/**
 * UltiLogin - Player login and registration system.
 * <p>
 * Features:
 * - Player registration with password
 * - Login authentication
 * - Session persistence by IP
 * - Movement/action restriction before login
 * - Auto-kick on login timeout
 * </p>
 * <p>
 * Reload is performed by the framework's final {@code reloadSelf()}, which re-reads
 * {@code login.yml} into {@code LoginConfig} (UltiKits/UltiLogin#13, #29).
 * </p>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@UltiToolsModule(scanBasePackages = {"com.ultikits.plugins.login"})
public class UltiLogin extends UltiToolsPlugin {

    @Override
    public boolean registerSelf() {
        getLogger().info(i18n("UltiLogin 已启用！"));
        return true;
    }
}
