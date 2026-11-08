package com.ultikits.plugins.login;

import com.ultikits.plugins.login.config.ConfigTextDefaults;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.config.RemovedConfigKeys;
import com.ultikits.plugins.login.logging.CloudSensitiveCommands;
import com.ultikits.plugins.login.logging.PasswordCommandMask;
import com.ultikits.plugins.login.logging.PasswordLogFilterInstaller;
import com.ultikits.plugins.login.util.Placeholders;
import com.ultikits.plugins.login.util.RuntimeCompatibility;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.plugins.compat.login.EquipmentSwapProtectionListener;
import com.ultikits.plugins.compat.login.MountProtectionListener;
import com.ultikits.plugins.compat.login.OpenSignProtectionListener;
import com.ultikits.plugins.compat.login.PickItemProtectionListener;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import com.ultikits.ultitools.websocket.SensitiveCommandRegistry;

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
 * <p>
 * On enable the module installs a filter on the server's Log4j root logger that masks the
 * arguments of its password commands in the console, {@code logs/latest.log} and the framework's
 * panel log mirror ({@link PasswordLogFilterInstaller}); {@link #onUnregister()} removes it.
 * It also registers every argument-taking command with UltiTools-API 6.3.1's sensitive-command
 * registry, so their arguments are redacted in everything the framework sends to UltiCloud
 * ({@link CloudSensitiveCommands}).
 * </p>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@UltiToolsModule(scanBasePackages = {"com.ultikits.plugins.login"})
public class UltiLogin extends UltiToolsPlugin {

    static final String MODERN_MOUNT_EVENT = "org.bukkit.event.entity.EntityMountEvent";
    static final String LEGACY_MOUNT_EVENT = "org.spigotmc.event.entity.EntityMountEvent";
    static final String PICK_ITEM_EVENT = "io.papermc.paper.event.player.PlayerPickItemEvent";
    static final String OPEN_SIGN_EVENT = "io.papermc.paper.event.player.PlayerOpenSignEvent";

    private Listener mountListener;
    private Listener pickItemListener;
    private Listener openSignListener;
    private boolean versionedProtectionsInitialized;
    private Listener equipmentSwapListener;
    private boolean equipmentSwapProtectionInitialized;

    /** Package-private seam so enable tests can exercise the runtime check without core bootstrap. */
    boolean refusesUnsupportedRuntime() {
        String server = Bukkit.getBukkitVersion();
        Plugin framework = Bukkit.getPluginManager().getPlugin("UltiTools");
        String version = framework == null ? null : framework.getDescription().getVersion();
        RuntimeCompatibility.Result result = RuntimeCompatibility.check(server, version);
        if (result == RuntimeCompatibility.Result.UNKNOWN) {
            getLogger().warn(Placeholders.fill(i18n("log_runtime_unknown"),
                    "{SERVER}", String.valueOf(server), "{FRAMEWORK}", String.valueOf(version)));
        } else if (result == RuntimeCompatibility.Result.REFUSE) {
            getLogger().error(Placeholders.fill(i18n("log_runtime_incompatible"),
                    "{SERVER}", server, "{FRAMEWORK}", version));
            return true;
        }
        return false;
    }

    /** Player-link callbacks must not be installed by container refresh before the gate accepts. */
    void activatePlayerLinks() {
        getContext().getBean(LoginService.class).registerPlayerLinkHandler();
    }

    @Override
    public boolean registerSelf() {
        if (refusesUnsupportedRuntime()) {
            return false;
        }
        activatePlayerLinks();
        getLogger().info(i18n("login_enabled"));
        registerSensitiveCommands();
        installPasswordLogFilter();
        // Deleting a key from LoginConfig does nothing to the operator's existing file, so tell
        // them about any key this version no longer reads (UltiKits/UltiLogin#23).
        warnAboutRemovedConfigKeys();
        warnAboutUnusableLockoutType();
        writeConfigTextInServerLanguage();
        registerVersionedProtections();
        registerEquipmentSwapProtection();
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
        warnAboutUnusableLockoutType();
        writeConfigTextInServerLanguage();
    }

    /**
     * Removes the password log filter, so a disabled module leaves no filter behind and the next
     * enable installs exactly one again. Runs before the framework unregisters this module's commands.
     */
    @Override
    protected void onUnregister() {
        try {
            unregisterVersionedProtections();
            unregisterEquipmentSwapProtection();
        } finally {
            String problem = PasswordLogFilterInstaller.uninstall();
            if (problem != null) {
                getLogger().warn(Placeholders.fill(i18n("log_password_filter_removal_failed"), "{REASON}", problem));
            }
        }
    }

    /**
     * Select exactly one mount event, then independently register each available Paper hook.
     * No optional event type appears in a scanned signature or field. Reload never repeats this work.
     */
    void registerVersionedProtections() {
        if (versionedProtectionsInitialized) {
            return;
        }
        PluginManager manager = Bukkit.getPluginManager();
        Plugin owner = manager.getPlugin("UltiTools");
        if (eventClass(MODERN_MOUNT_EVENT) != null) {
            mountListener = createMountListener();
            manager.registerEvents(mountListener, owner);
        } else {
            Class<? extends Event> legacy = eventClass(LEGACY_MOUNT_EVENT);
            if (legacy != null) {
                // Both measured mount types extend EntityEvent and implement Cancellable. The old
                // event is not on the compile classpath; register its actual class, not an annotation
                // on the new event or a second core bean with an empty join-tick set.
                LoginProtectionListener protection = getContext().getBean(LoginProtectionListener.class);
                mountListener = createLegacyMountListener();
                manager.registerEvent(legacy, mountListener, EventPriority.LOWEST, (listener, event) -> {
                    if (legacy.isInstance(event)) {
                        protection.protectMount(((EntityEvent) event).getEntity(), (Cancellable) event);
                    }
                }, owner, false);
            } else {
                getLogger().warn(i18n("log_mount_hook_unavailable"));
            }
        }
        if (eventClass(PICK_ITEM_EVENT) != null) {
            pickItemListener = createPickItemListener();
            manager.registerEvents(pickItemListener, owner);
        } else {
            getLogger().warn(i18n("log_pick_item_hook_unavailable"));
        }
        if (eventClass(OPEN_SIGN_EVENT) != null) {
            openSignListener = createOpenSignListener();
            manager.registerEvents(openSignListener, owner);
        } else {
            getLogger().warn(i18n("log_open_sign_hook_unavailable"));
        }
        versionedProtectionsInitialized = true;
    }

    /** Package-private presence seam; never initializes a server event class. */
    Class<? extends Event> eventClass(String name) {
        return eventClass(name, UltiLogin.class.getClassLoader());
    }

    static Class<? extends Event> eventClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader).asSubclass(Event.class);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    Listener createMountListener() {
        return new MountProtectionListener(getContext().getBean(LoginProtectionListener.class));
    }

    Listener createLegacyMountListener() {
        return new Listener() { };
    }

    Listener createPickItemListener() {
        return new PickItemProtectionListener(getContext().getBean(LoginService.class));
    }

    Listener createOpenSignListener() {
        return new OpenSignProtectionListener(getContext().getBean(LoginService.class));
    }

    /** Unregister only the instances manually owned by this enable, never the shared Bukkit owner. */
    void unregisterVersionedProtections() {
        if (mountListener != null) {
            HandlerList.unregisterAll(mountListener);
            mountListener = null;
        }
        if (pickItemListener != null) {
            HandlerList.unregisterAll(pickItemListener);
            pickItemListener = null;
        }
        if (openSignListener != null) {
            HandlerList.unregisterAll(openSignListener);
            openSignListener = null;
        }
        versionedProtectionsInitialized = false;
    }

    /**
     * Registers the optional hook once per enable, without exposing its event type to component
     * scanning or IoC method reflection. No config reload repeats this startup-only work.
     */
    void registerEquipmentSwapProtection() {
        if (equipmentSwapProtectionInitialized) {
            return;
        }
        if (!equipmentSwapEventAvailable()) {
            equipmentSwapProtectionInitialized = true;
            getLogger().warn(i18n("log_equipment_swap_hook_unavailable"));
            return;
        }
        Listener listener = createEquipmentSwapListener();
        Bukkit.getPluginManager().registerEvents(listener, Bukkit.getPluginManager().getPlugin("UltiTools"));
        equipmentSwapListener = listener;
        equipmentSwapProtectionInitialized = true;
    }

    /** Package-private presence seam; production uses the module's defining loader. */
    boolean equipmentSwapEventAvailable() {
        return equipmentSwapEventAvailable(UltiLogin.class.getClassLoader());
    }

    static boolean equipmentSwapEventAvailable(ClassLoader loader) {
        try {
            Class.forName("io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent", false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /**
     * The optional class appears only in this guarded method body, never a scanned signature.
     * Returning Listener keeps method reflection safe before the event-presence probe.
     */
    Listener createEquipmentSwapListener() {
        return new EquipmentSwapProtectionListener(getContext().getBean(LoginService.class));
    }

    /** Direct Bukkit registration is not tracked by the framework's ListenerManager. */
    void unregisterEquipmentSwapProtection() {
        if (equipmentSwapListener != null) {
            HandlerList.unregisterAll(equipmentSwapListener);
            equipmentSwapListener = null;
        }
        equipmentSwapProtectionInitialized = false;
    }

    /**
     * Registers this module's argument-taking commands with the framework's sensitive-command
     * registry, so their arguments never reach UltiCloud. Idempotent, so a reload is harmless; the
     * framework has no unregister, so {@link #onUnregister()} has nothing to undo. No guard is
     * needed: {@code api-version: 631} makes an older framework, which lacks the registry, refuse
     * this module before it enables.
     */
    private void registerSensitiveCommands() {
        CloudSensitiveCommands.registerWith(sensitiveCommandRegistry());
    }

    /**
     * Masks the password arguments of this module's commands in the server log (Paper logs every
     * player command line). A server without a usable Log4j Core gets a warning and runs unmasked,
     * as before this filter existed; the module itself still enables.
     */
    private void installPasswordLogFilter() {
        String problem = PasswordLogFilterInstaller.install(PasswordCommandMask.forModuleCommands());
        if (problem != null) {
            getLogger().warn(Placeholders.fill(i18n("log_password_filter_unavailable"), "{REASON}", problem));
        }
    }

    /**
     * Names a {@code security.lockout-type} the module cannot use, with the value as written and the
     * default it falls back to (UltiKits/UltiLogin#37). The lockout itself already applies the
     * default ({@link LoginConfig#lockoutTypeOrDefault}); this only tells the operator.
     */
    private void warnAboutUnusableLockoutType() {
        LoginConfig config = getConfig(LoginConfig.class);
        if (config == null || LoginConfig.isUsableLockoutType(config.getLockoutType())) {
            return;
        }
        // The value as written is inserted in the same pass as the rest (Placeholders#fill), so a
        // value that contains a placeholder token is shown exactly as the operator wrote it.
        getLogger().warn(Placeholders.fill(i18n("log_unusable_lockout_type"),
                "{FILE}", LoginConfig.CONFIG_FILE,
                "{KEY}", "security.lockout-type",
                "{VALUE}", String.valueOf(config.getLockoutType()),
                "{DEFAULT}", LoginConfig.LOCKOUT_TYPE_DEFAULT));
    }

    /**
     * Writes every GUI title and message in {@code login.yml} that is still built-in text in the
     * server's language and saves the file once, so the file holds what the module sends; any other
     * value is the operator's and is kept (maintainer decision 2026-09-25, UltiKits/UltiLogin#20). Runs
     * from {@link #registerSelf()} and from {@link #onReload()}, both after the module's language is
     * loaded -- never from a configuration change listener, which the framework fires before it reloads
     * the language. A value already in the current language matches nothing to replace, so a second
     * start writes nothing.
     * The text comes from this jar's own catalogue for the server's language, not from {@code i18n} (which
     * reads the operator's extracted language file first), so every value written is one the next pass
     * recognises.
     */
    private void writeConfigTextInServerLanguage() {
        LoginConfig config = getConfig(LoginConfig.class);
        if (config == null || !config.materializeText(ConfigTextDefaults.jarLanguage(LoginConfig.class, getLanguageCode())::getLocalizedText)) {
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

    /**
     * The framework's sensitive-command registry.
     * <p>
     * A seam, package-private on purpose, like {@link #operatorConfigFile()}: a mocked plugin returns
     * {@code null} from it, which {@link CloudSensitiveCommands#registerWith} ignores, so tests that
     * enable a mocked module need no static mock of {@code UltiTools}.
     *
     * @return {@code UltiTools.getInstance().getSensitiveCommandRegistry()}
     */
    SensitiveCommandRegistry sensitiveCommandRegistry() {
        return UltiTools.getInstance().getSensitiveCommandRegistry();
    }
}
