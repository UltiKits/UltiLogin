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

    @Override
    public void unregisterSelf() {
        getLogger().info(i18n("UltiLogin 已禁用！"));
    }

    @Override
    public void reloadSelf() {
        // UltiLogin#13: this override used to log a reload message without ever reloading
        // anything -- ConfigManager.reloadConfigs(this), the only thing that re-reads
        // login.yml into a running LoginConfig, was never reached. That left allowedCommands
        // (and every other @ConfigEntry field on LoginConfig) frozen at whatever it was when
        // the plugin loaded, for the life of the server process, no matter how many times
        // /ul reload UltiLogin ran or what the file said afterward. super.reloadSelf() is what
        // actually reloads the config (plus the language file and @ConditionalOnConfig drift
        // reporting); this module's own log line stays after it as user-facing confirmation.
        super.reloadSelf();
        getLogger().info(i18n("UltiLogin 配置已重载！"));
    }
}
