package com.ultikits.plugins.login;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.config.RemovedConfigKeys;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

import java.io.File;

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
 * {@code login.yml} into {@code LoginConfig} (UltiKits/UltiLogin#13, #29). The {@link #onReload()}
 * hook adds only the removed-key warning (UltiKits/UltiLogin#23).
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
        // Deleting a key from LoginConfig does nothing to the operator's existing file, so tell
        // them about any key this version no longer reads (UltiKits/UltiLogin#23).
        warnAboutRemovedConfigKeys();
        return true;
    }

    /**
     * Runs after the framework has re-read {@code login.yml}. Repeats the removed-key warning, so an
     * operator who edits a key this version no longer reads and reloads is told it has no effect.
     */
    @Override
    protected void onReload() {
        warnAboutRemovedConfigKeys();
    }

    private void warnAboutRemovedConfigKeys() {
        // Advisory only: this runs on the enable path of the module whose absence means nobody is
        // asked to log in, so nothing it throws may cost the module its enable or its reload.
        try {
            RemovedConfigKeys.warnAboutLeftovers(operatorConfigFile(), getLogger()::warn);
        } catch (RuntimeException e) {
            getLogger().warn(e, "Could not check " + LoginConfig.CONFIG_FILE
                    + " for removed configuration keys; the module continues without that check.");
        }
    }

    /**
     * The path of this module's configuration file, relative to its folder -- read from
     * {@link LoginConfig#CONFIG_FILE}, the same constant that entity binds, never a copy of it.
     * Package-private so a test can require the two to be equal.
     *
     * @return {@code config/login.yml}
     */
    String operatorConfigPath() {
        return LoginConfig.CONFIG_FILE;
    }

    /**
     * The operator's own copy of this module's configuration file.
     * <p>
     * A seam, package-private on purpose. {@code UltiToolsPlugin#getConfigFile} is {@code protected}
     * and {@code final}, so a test in this package can neither call it nor stub it, and a mocked
     * plugin returns {@code null} from it -- which means that without this method the removed-key
     * check's wiring could not be asserted at all, only its predicate.
     *
     * @return the file {@code config/login.yml} resolves to for this installation
     */
    File operatorConfigFile() {
        return getConfigFile(operatorConfigPath());
    }
}
