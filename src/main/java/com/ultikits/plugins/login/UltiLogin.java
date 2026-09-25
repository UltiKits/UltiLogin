package com.ultikits.plugins.login;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.config.RemovedConfigKeys;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

import java.io.File;
import java.io.IOException;

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
        getLogger().info(i18n("login_enabled"));
        // Deleting a key from LoginConfig does nothing to the operator's existing file, so tell
        // them about any key this version no longer reads (UltiKits/UltiLogin#23).
        warnAboutRemovedConfigKeys();
        blankShippedTextDefaults();
        return true;
    }

    /**
     * Runs after the framework has re-read {@code login.yml}, on every reload of this module -- a bare
     * {@code /ul reload} as well as {@code /ul reload UltiLogin}. Repeats the removed-key warning, so
     * an operator who edits a key this version no longer reads and reloads is told it has no effect.
     */
    @Override
    protected void onReload() {
        warnAboutRemovedConfigKeys();
        blankShippedTextDefaults();
    }

    /**
     * Blanks every GUI title and message in {@code login.yml} that still holds a default an earlier
     * version shipped (all were Chinese) and saves the file, so the language file's text takes over in
     * the server's language; any other value is the operator's and is kept (maintainer ruling
     * 2026-09-24 (d), UltiKits/UltiLogin#20). Runs at start-up and on every reload, after the
     * framework has read the file; a blank value matches no shipped default, so it is never rewritten
     * twice.
     */
    private void blankShippedTextDefaults() {
        LoginConfig config = getConfig(LoginConfig.class);
        if (config == null || !config.migrateLegacyDefaults()) {
            return;
        }
        try {
            config.save();
        } catch (IOException e) {
            getLogger().warn(e, i18n("log_config_default_save_failed").replace("{FILE}", LoginConfig.CONFIG_FILE));
        }
    }

    private void warnAboutRemovedConfigKeys() {
        // Advisory only: this runs on the enable path of the module whose absence means nobody is
        // asked to log in, so nothing it throws may cost the module its enable or its reload.
        try {
            RemovedConfigKeys.warnAboutLeftovers(operatorConfigFile(), getLogger()::warn, this);
        } catch (RuntimeException e) {
            getLogger().warn(e, i18n("log_removed_key_check_failed").replace("{FILE}", LoginConfig.CONFIG_FILE));
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
