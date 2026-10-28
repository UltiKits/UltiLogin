package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.gui.LoginGUIPage;
import com.ultikits.plugins.login.gui.RegisterGUIPage;
import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.PostConstruct;
import com.ultikits.ultitools.annotations.PreDestroy;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for managing player login and registration.
 *
 * @author wisdomme
 * @version 1.1.0
 */
@com.ultikits.ultitools.annotations.Service
public class LoginService {

    private final UltiToolsPlugin plugin;
    private final LoginConfig config;
    private final DataOperator<AccountData> dataOperator;
    private final Plugin bukkitPlugin;

    // Track logged in players
    private final Map<UUID, Boolean> loggedInPlayers = new ConcurrentHashMap<>();

    // Track player join times for timeout
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    // Track player original locations
    private final Map<UUID, Location> originalLocations = new ConcurrentHashMap<>();

    // Sessions (IP:UUID -> last login time)
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    /** IP-mode failure count with the time of its latest failure; success never clears it. */
    private static final class AddressFailures {
        private final int count;
        private final long lastFailure;

        private AddressFailures(int count, long lastFailure) {
            this.count = count;
            this.lastFailure = lastFailure;
        }
    }

    private final Map<String, AddressFailures> failedAttempts = new ConcurrentHashMap<>();
    // UUID mode alone counts by account, independent of its address.
    private final Map<UUID, Integer> failedAttemptsByUuid = new ConcurrentHashMap<>();
    // The outer map's compute serialises each address's distinct-account membership updates.
    private final Map<String, Map<UUID, Long>> failingAccountsByIp = new ConcurrentHashMap<>();

    /** Immutable identity for one account attempting a login from one address. */
    private static final class LoginPair {
        private final String address;
        private final UUID uuid;

        private LoginPair(String address, UUID uuid) {
            this.address = address;
            this.uuid = uuid;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) { return true; }
            if (!(other instanceof LoginPair)) { return false; }
            LoginPair pair = (LoginPair) other;
            return address.equals(pair.address) && uuid.equals(pair.uuid);
        }

        @Override
        public int hashCode() {
            return 31 * address.hashCode() + uuid.hashCode();
        }
    }

    private final Map<LoginPair, Integer> failedAttemptsByPair = new ConcurrentHashMap<>();
    private final Map<LoginPair, Long> lockedPairs = new ConcurrentHashMap<>();

    // Locked IPs (IP -> unlock time)
    private final Map<String, Long> lockedIps = new ConcurrentHashMap<>();

    // Locked UUIDs (UUID -> unlock time)
    private final Map<UUID, Long> lockedUuids = new ConcurrentHashMap<>();

    // Invalidation generation per player -- incremented by invalidateSession(UUID), used to
    // fence panel-link requests captured before an invalidation that landed before the request
    // was actually published. See PR #18 and requestPanelLink(Player, long)'s javadoc.
    private final Map<UUID, AtomicLong> invalidationGenerations = new ConcurrentHashMap<>();

    // Random generator for password generation
    private final SecureRandom random = new SecureRandom();

    // Pending /panel requests (id -> player UUID). A request is pending under a local id while its create is in
    // flight and under the gateway's link id once main-thread publishPanelCreation re-keys it under the
    // player's generation cell, never under both.
    private final Map<String, UUID> pendingPanelRequests = new ConcurrentHashMap<>();

    // When each pending request ends locally (id -> clock millis): the local create cap while the create is in
    // flight, then the link's expiresAt clamped to 60 s .. 10 min from receipt (cleanupExpiredPanelRequests).
    private final Map<String, Long> pendingPanelDeadlines = new ConcurrentHashMap<>();

    // Local ids of pending requests whose create has not answered yet; such a request ends silently.
    private final java.util.Set<String> panelCreatesInFlight = ConcurrentHashMap.newKeySet();

    // The id of each player's latest /panel request (player -> id), following the re-key to the link id. Only that
    // link announces its local expiry; an earlier one, which the gateway cancels when the newer one is created,
    // ends silently. Cleared when the player's pending requests are dropped (quit, invalidation).
    private final Map<UUID, String> latestPanelRequest = new ConcurrentHashMap<>();

    // Players currently mid-transition between credential GUIs (e.g. LoginGUIPage ->
    // RegisterGUIPage after an admin unregister revoked them while a GUI was open) -- consulted
    // by LoginGUIPage/RegisterGUIPage's own onClose reopen hooks so the GUI being replaced does
    // not schedule its own reopen while presentCredentialPrompt() is in the middle of opening the
    // correct one.
    private final java.util.Set<UUID> credentialGuiTransitions = ConcurrentHashMap.newKeySet();

    // Players who currently have a LoginGUIPage or RegisterGUIPage open, set by that page's own
    // onOpen and cleared by its onClose (or by markCredentialGuiClosed elsewhere). Consulted by
    // the delayed reopen callback scheduled in onClose -- see pendingCredentialGuiReopenTasks --
    // so a queued reopen from a GUI the player already closed for an unrelated reason cannot open
    // a second credential GUI on top of one presentCredentialPrompt() already opened in the
    // meantime.
    private final java.util.Set<UUID> openCredentialGuiPlayers = ConcurrentHashMap.newKeySet();

    // The delayed reopen task LoginGUIPage/RegisterGUIPage's onClose schedules for a player, if
    // any. presentCredentialPrompt() cancels/replaces this before opening its own credential GUI
    // (see LoginProtectionListener#presentCredentialPrompt), so a reopen queued by a GUI the
    // player closed for an unrelated reason cannot fire after -- and land on top of -- a GUI a
    // credential change has since opened. A registered, logged-out player closing LoginGUIPage
    // schedules a reopen 10 ticks later; an admin password reset landing in that window used to
    // leave the queued reopen live, so it opened a second credential GUI over the one the reset's
    // own prompt had just opened, whose own onClose then queued another reopen -- replacing the GUI
    // every 10 ticks indefinitely.
    private final Map<UUID, BukkitTask> pendingCredentialGuiReopenTasks = new ConcurrentHashMap<>();

    // /panel creates its link only through the framework's player-link API (see PlayerLinkPort); tests
    // replace it with a fake.
    private volatile PlayerLinkPort playerLinkPort;

    // The bounds a created link's local lifetime is clamped to (see linkDeadline).
    private static final long MIN_LINK_LIFETIME_MILLIS = 60_000L;
    private static final long MAX_LINK_LIFETIME_MILLIS = 600_000L;

    // Keep each creating address through the link's local deadline plus the gateway's 24h redelivery window.
    // Quit, invalidation and local end must not erase it. Pruned every second and on receipt; no load-based eviction.
    private static final long LINK_ORIGIN_GRACE_MILLIS = 86_400_000L;
    private final Map<String, PanelLinkOrigin> panelLinkOrigins = new ConcurrentHashMap<>();

    private static final class PanelLinkOrigin {
        private final UUID playerUuid;
        private final String ip;
        private final long retainUntil;

        private PanelLinkOrigin(UUID playerUuid, String ip, long retainUntil) {
            this.playerUuid = playerUuid;
            this.ip = ip;
            this.retainUntil = retainUntil;
        }
    }

    // How long /panel's asynchronous worker waits for a create to answer (tests shorten it).
    private volatile long panelCreateCapMillis = 45_000L;

    // The clock the login timeout and the /panel link lifetime read (tests replace it).
    private volatile java.util.function.LongSupplier clock = System::currentTimeMillis;

    // Login-timeout pause while a /panel link is pending.
    // A player's countdown is paused while at least one of their requests is pending; panelPausedAt
    // records when the pause began, and on resume the join time
    // moves forward by the paused span, so the countdown continues with exactly the time that was
    // left. All transitions go through syncPanelPause under panelPauseLock.
    private final Map<UUID, Long> panelPausedAt = new ConcurrentHashMap<>();
    // Players who were told their countdown is paused; only they are told it resumed.
    private final java.util.Set<UUID> panelPauseAnnounced = ConcurrentHashMap.newKeySet();
    private final Object panelPauseLock = new Object();

    // How many answered completions are remembered (completion id -> result) so a redelivery in this run repeats
    // nothing; across restarts the framework's persisted receipts de-duplicate.
    static final int ANSWERED_COMPLETIONS_KEPT = 256;

    // The results of the completions answered in this run, oldest first, bounded to ANSWERED_COMPLETIONS_KEPT.
    // Touched only on the main thread, so it needs no lock.
    private final Map<String, PlayerLinkPort.CompletionResult> answeredCompletions =
            new LinkedHashMap<String, PlayerLinkPort.CompletionResult>() {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, PlayerLinkPort.CompletionResult> eldest) {
                    return size() > ANSWERED_COMPLETIONS_KEPT;
                }
            };

    // The handler the framework calls with this server's link outcomes; installed by registerPlayerLinkHandler.
    private final PlayerLinkPort.LinkEventHandler linkEventHandler = new PlayerLinkPort.LinkEventHandler() {
        @Override
        public java.util.concurrent.CompletableFuture<PlayerLinkPort.CompletionResult> onCompleted(
                PlayerLinkPort.LinkCompletion completion) {
            return onLinkCompleted(completion);
        }

        @Override
        public void onExpired(PlayerLinkPort.LinkExpiry expiry) {
            onLinkExpired(expiry);
        }
    };

    /**
     * Constructor with dependency injection.
     */
    public LoginService(UltiToolsPlugin plugin, LoginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.dataOperator = plugin.getDataOperator(AccountData.class);
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
        this.playerLinkPort = new FrameworkPlayerLinkPort(plugin);
    }

    /**
     * This module's language-file text for {@code key}, in the server's language. The commands reach
     * the language file through the service they already hold (UltiKits/UltiLogin#20).
     *
     * @param key the language-file key
     * @return the text for the key
     */
    public String i18n(String key) {
        return plugin.i18n(key);
    }

    /**
     * Replace the player-link API {@code /panel} creates its links through (tests only; production always uses
     * {@link FrameworkPlayerLinkPort}).
     *
     * @param port the port to use from now on
     */
    void setPlayerLinkPort(PlayerLinkPort port) {
        this.playerLinkPort = port;
    }

    /**
     * Shorten how long {@code /panel}'s worker waits for a create to answer (tests only; 45 seconds otherwise).
     *
     * @param millis the cap in milliseconds
     */
    void setPanelCreateCap(long millis) {
        this.panelCreateCapMillis = millis;
    }

    /**
     * Replace the clock the login timeout and the {@code /panel} link lifetime read (tests).
     *
     * @param clock milliseconds since the epoch
     */
    void setClock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Install this module's handler for link completions and expiries with the framework's player-link API, once the
     * service exists. The framework calls it for every completion and expiry of this server's links, including
     * completions redelivered after a restart. A framework that refuses the handler (not enabled, or another module
     * owns it) is reported once in the console: {@code /panel} can still create links, but nobody is logged in by
     * them until the module is loaded again.
     */
    @PostConstruct
    public void registerPlayerLinkHandler() {
        if (!playerLinkPort.registerHandler(linkEventHandler)) {
            plugin.getLogger().warn(i18n("log_panel_link_handler_unavailable"));
        }
    }

    /**
     * Cleanup on shutdown. Also removes the player-link handler; the framework removes a module's handler when the
     * module unloads as well, and removing it twice is harmless.
     */
    @PreDestroy
    public void shutdown() {
        playerLinkPort.unregisterHandler();
        // Cancel all queued credential-GUI reopen tasks.
        for (BukkitTask task : pendingCredentialGuiReopenTasks.values()) {
            task.cancel();
        }
        pendingCredentialGuiReopenTasks.clear();
        loggedInPlayers.clear();
        joinTimes.clear();
        originalLocations.clear();
        failedAttempts.clear();
        failedAttemptsByUuid.clear();
        failingAccountsByIp.clear();
        failedAttemptsByPair.clear();
        lockedPairs.clear();
        lockedIps.clear();
        lockedUuids.clear();
        pendingPanelRequests.clear();
        pendingPanelDeadlines.clear();
        panelLinkOrigins.clear();
        panelCreatesInFlight.clear();
        latestPanelRequest.clear();
        panelPausedAt.clear();
        panelPauseAnnounced.clear();
        answeredCompletions.clear();
        invalidationGenerations.clear();
        credentialGuiTransitions.clear();
        openCredentialGuiPlayers.clear();
    }
    
    /**
     * Check if player is registered.
     */
    public boolean isRegistered(UUID playerUuid) {
        List<AccountData> accounts = dataOperator.query()
            .where("player_uuid").eq(playerUuid.toString())
            .list();
        return !accounts.isEmpty();
    }

    /**
     * Check if player is registered by name.
     */
    public boolean isRegisteredByName(String playerName) {
        List<AccountData> accounts = dataOperator.query()
            .where("player_name").eq(playerName)
            .list();
        return !accounts.isEmpty();
    }
    
    /**
     * Check if player is logged in.
     */
    public boolean isLoggedIn(UUID playerUuid) {
        return loggedInPlayers.getOrDefault(playerUuid, false);
    }
    
    /**
     * Remove expired time-bearing counters on reads and writes, including idle addresses.
     * Unlocked UUID and pair counters deliberately have no time-based expiry.
     */
    private void pruneFailureWindows(long now) {
        long duration = config.getLockoutDuration() * 1000L;
        failedAttempts.forEach((ip, value) -> failedAttempts.computeIfPresent(ip,
                (key, current) -> now - current.lastFailure >= duration ? null : current));
        failingAccountsByIp.forEach((ip, value) -> failingAccountsByIp.computeIfPresent(ip, (key, current) -> {
            current.values().removeIf(last -> now - last >= duration);
            return current.isEmpty() ? null : current;
        }));
    }

    /** Expiring a lock also resets its own counter (UltiKits/UltiLogin#38). */
    private <K> boolean activeLock(Map<K, Long> locks, K key, long now, Runnable resetCount) {
        Long end = locks.get(key);
        if (end == null) { return false; }
        if (now < end) { return true; }
        locks.computeIfPresent(key, (target, current) -> {
            if (now >= current) {
                resetCount.run();
                return null;
            }
            return current;
        });
        return false;
    }

    /**
     * Check the configured lock targets. Lockout operations share a monitor so expiry cannot
     * reset a newly recorded count between updating a counter and publishing its lock.
     */
    public synchronized boolean isLocked(Player player) {
        long now = clock.getAsLong();
        pruneFailureWindows(now);
        String ip = getPlayerIp(player);
        UUID uuid = player.getUniqueId();
        String type = LoginConfig.lockoutTypeOrDefault(config.getLockoutType());
        if ("UUID".equals(type)) {
            return activeLock(lockedUuids, uuid, now, () -> failedAttemptsByUuid.remove(uuid));
        }
        boolean addressLocked = activeLock(lockedIps, ip, now, () -> {
            failedAttempts.remove(ip);
            failingAccountsByIp.remove(ip);
        });
        if ("IP".equals(type)) { return addressLocked; }
        LoginPair pair = new LoginPair(ip, uuid);
        boolean pairLocked = activeLock(lockedPairs, pair, now, () -> failedAttemptsByPair.remove(pair));
        return addressLocked || pairLocked;
    }

    /** Get the longest applicable remaining lockout time in seconds. */
    public synchronized long getRemainingLockoutTime(Player player) {
        isLocked(player); // Prune counters and expire locks before reading their times.
        long now = clock.getAsLong();
        String type = LoginConfig.lockoutTypeOrDefault(config.getLockoutType());
        UUID uuid = player.getUniqueId();
        String ip = getPlayerIp(player);
        long end;
        if ("UUID".equals(type)) {
            end = lockedUuids.getOrDefault(uuid, now);
        } else {
            end = lockedIps.getOrDefault(ip, now);
            if ("BOTH".equals(type)) {
                end = Math.max(end, lockedPairs.getOrDefault(new LoginPair(ip, uuid), now));
            }
        }
        return Math.max(0L, (end - now) / 1000L);
    }

    /** Record one password failure, atomically within each target's counter. */
    private synchronized void recordFailedAttempt(Player player) {
        long now = clock.getAsLong();
        pruneFailureWindows(now);
        int maximum = config.getMaxLoginAttempts();
        if (maximum <= 0) { return; }
        String ip = getPlayerIp(player);
        UUID uuid = player.getUniqueId();
        String type = LoginConfig.lockoutTypeOrDefault(config.getLockoutType());
        long unlockTime = now + config.getLockoutDuration() * 1000L;
        if ("IP".equals(type)) {
            AddressFailures failures = failedAttempts.compute(ip, (key, current) ->
                    new AddressFailures(current == null ? 1 : current.count + 1, now));
            if (failures.count >= maximum) { lockedIps.putIfAbsent(ip, unlockTime); }
        } else if ("UUID".equals(type)) {
            int attempts = failedAttemptsByUuid.merge(uuid, 1, Integer::sum);
            if (attempts >= maximum) { lockedUuids.putIfAbsent(uuid, unlockTime); }
        } else {
            LoginPair pair = new LoginPair(ip, uuid);
            int attempts = failedAttemptsByPair.merge(pair, 1, Integer::sum);
            if (attempts >= maximum) { lockedPairs.putIfAbsent(pair, unlockTime); }
            failingAccountsByIp.compute(ip, (key, current) -> {
                Map<UUID, Long> accounts = current == null ? new HashMap<>() : current;
                accounts.put(uuid, now);
                if (accounts.size() >= maximum) { lockedIps.putIfAbsent(ip, unlockTime); }
                return accounts;
            });
        }
    }

    /** Get remaining attempts for the configured counter, or -1 when unlimited. */
    public synchronized int getRemainingAttempts(Player player) {
        boolean locked = isLocked(player);
        int maximum = config.getMaxLoginAttempts();
        if (maximum <= 0) { return -1; }
        if (locked) { return 0; }
        String type = LoginConfig.lockoutTypeOrDefault(config.getLockoutType());
        int attempts;
        if ("IP".equals(type)) {
            AddressFailures failures = failedAttempts.get(getPlayerIp(player));
            attempts = failures == null ? 0 : failures.count;
        } else if ("UUID".equals(type)) {
            attempts = failedAttemptsByUuid.getOrDefault(player.getUniqueId(), 0);
        } else {
            attempts = failedAttemptsByPair.getOrDefault(new LoginPair(getPlayerIp(player), player.getUniqueId()), 0);
        }
        return Math.max(0, maximum - attempts);
    }

    /**
     * Success clears only its account (UUID) or pair (BOTH). Shared IP failures and distinct
     * failing-account memberships expire naturally; success never removes or shortens a lock.
     */
    private synchronized void clearFailedAttempts(Player player) {
        pruneFailureWindows(clock.getAsLong());
        String type = LoginConfig.lockoutTypeOrDefault(config.getLockoutType());
        if ("UUID".equals(type)) {
            failedAttemptsByUuid.remove(player.getUniqueId());
        } else if ("BOTH".equals(type)) {
            failedAttemptsByPair.remove(new LoginPair(getPlayerIp(player), player.getUniqueId()));
        }
    }

    /**
     * Validate password based on current mode (GUI/Command).
     */
    public boolean isPasswordValid(String password) {
        if (config.isGuiModeEnabled()) {
            // GUI mode: must be exact length and digits only
            return password.length() == config.getGuiPasswordLength() 
                && password.matches("\\d+");
        } else {
            // Command mode: check length
            return password.length() >= config.getMinPasswordLength() 
                && password.length() <= config.getMaxPasswordLength();
        }
    }
    
    /**
     * Get password validation error message.
     */
    public String getPasswordValidationError(String password) {
        if (config.isGuiModeEnabled()) {
            return config.getGuiPasswordInvalid()
                .replace("{LENGTH}", String.valueOf(config.getGuiPasswordLength()));
        } else {
            if (password.length() < config.getMinPasswordLength()) {
                return config.getPasswordTooShort()
                    .replace("{MIN}", String.valueOf(config.getMinPasswordLength()));
            }
            if (password.length() > config.getMaxPasswordLength()) {
                return config.getPasswordTooLong()
                    .replace("{MAX}", String.valueOf(config.getMaxPasswordLength()));
            }
        }
        return "";
    }
    
    /**
     * Register a new player.
     * 
     * @param player Player to register
     * @param password Password
     * @return true if success
     */
    public boolean register(Player player, String password) {
        if (isRegistered(player.getUniqueId())) {
            return false;
        }
        
        // Check IP registration limit
        String ip = getPlayerIp(player);
        if (config.getMaxRegisterPerIp() > 0) {
            int count = countRegistrationsByIp(ip);
            if (count >= config.getMaxRegisterPerIp()) {
                player.sendMessage(ChatColor.RED + i18n("ip_limit_reached"));
                return false;
            }
        }
        
        // Generate salt and hash password
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        
        // Create account
        dataOperator.insert(newAccountRow(player, hash, salt, ip));
        
        // Auto login after register
        completeLogin(player);
        
        return true;
    }
    
    /**
     * The account row {@code /register} creates, and the one a web registration creates with an
     * empty password hash and salt.
     */
    private AccountData newAccountRow(Player player, String hash, String salt, String ip) {
        return newAccountRow(player.getUniqueId(), player.getName(), hash, salt, ip);
    }

    private AccountData newAccountRow(UUID playerUuid, String playerName, String hash, String salt, String ip) {
        AccountData account = new AccountData();
        account.setPlayerUuid(playerUuid.toString());
        account.setPlayerName(playerName);
        account.setPasswordHash(hash);
        account.setSalt(salt);
        account.setRegisterIp(ip);
        account.setLastIp(ip);
        account.setLastLogin(System.currentTimeMillis());
        return account;
    }

    /**
     * Whether a stored password hash means "no game password": the account was created by a web
     * registration through {@code /panel} and {@code /setpassword} has not set its backup password yet.
     */
    private static boolean isEmptyHash(String passwordHash) {
        return passwordHash == null || passwordHash.isEmpty();
    }

    /**
     * Login a player.
     * 
     * @param player Player
     * @param password Password
     * @return LoginResult with status and message
     */
    public LoginResult login(Player player, String password) {
        // Check if locked
        if (isLocked(player)) {
            long remaining = getRemainingLockoutTime(player);
            return new LoginResult(false, config.getAccountLocked()
                .replace("{TIME}", String.valueOf(remaining)));
        }
        
        AccountData account = getAccount(player.getUniqueId());
        if (account == null) {
            return new LoginResult(false, config.getNotRegistered());
        }

        // An account created on the web has no game password. Refuse before any hash comparison, so
        // no password can ever match it (fails closed), and do not count it as a wrong password: the
        // player is told to use /panel and then /setpassword (contract section 13).
        if (isEmptyHash(account.getPasswordHash())) {
            return new LoginResult(false, i18n("login_no_game_password_backup"));
        }
        
        // Verify password
        String hash = hashPassword(password, account.getSalt());
        if (!hashesMatch(hash, account.getPasswordHash())) {
            recordFailedAttempt(player);
            // The reply says "locked" only when this attempt actually recorded a lock, never
            // because of the count (UltiLogin#23). Unlimited attempts do not count or lock;
            // an unusable lockout type resolves to the declared default. A lock that existed
            // before this attempt was refused above,
            // before any password check, so the lock here is new and lasts lockout-duration.
            if (isLocked(player)) {
                return new LoginResult(false, config.getAccountLocked()
                    .replace("{TIME}", String.valueOf(config.getLockoutDuration())));
            }
            int remaining = getRemainingAttempts(player);
            if (remaining > 0) {
                return new LoginResult(false, config.getAttemptsRemaining()
                    .replace("{COUNT}", String.valueOf(remaining)));
            }
            return new LoginResult(false, i18n("wrong_password"));
        }
        
        // Update last login
        String ip = getPlayerIp(player);
        account.setLastIp(ip);
        account.setLastLogin(System.currentTimeMillis());
        account.setLoginCount(account.getLoginCount() + 1);
        account.setFailedAttempts(0);
        AccountWrites.update(dataOperator, plugin.getLogger(), account,
                i18n("log_account_update_failed"), i18n("log_account_row_gone"));
        
        // Create session
        if (config.isSessionEnabled()) {
            sessions.put(ip + ":" + player.getUniqueId(), System.currentTimeMillis());
        }
        
        completeLogin(player);
        return new LoginResult(true, config.getLoginSuccess());
    }
    
    /**
     * Check if player has valid session.
     */
    public boolean hasValidSession(Player player) {
        if (!config.isSessionEnabled()) {
            return false;
        }
        
        String ip = getPlayerIp(player);
        String key = ip + ":" + player.getUniqueId();
        Long lastLogin = sessions.get(key);
        
        if (lastLogin == null) {
            return false;
        }
        
        long sessionTimeout = config.getSessionTimeout() * 60 * 1000L;
        return System.currentTimeMillis() - lastLogin < sessionTimeout;
    }

    /**
     * End every session belonging to a player, regardless of which address it was opened from,
     * and -- if the player is currently online -- revoke their active login state too.
     * <p>
     * Called from every path that changes or removes a player's credentials: account deletion,
     * both password-reset overloads, and password change. Matches by the player-identifier
     * suffix of the session key ({@code ip:uuid}), not by the caller's own current address --
     * that distinction is the point, since an administrator deleting an account, or a recovery
     * flow, specifically needs to end a session the actor is not connected from. No separate
     * identifier-to-keys index is kept: the session map holds one entry per authenticated
     * address per player, so a scan over its key set is bounded and small, and a second
     * structure would itself need invalidating.
     * <p>
     * Real-server finding: a real {@code /changepassword} called only this method and reported
     * success while the player stayed authenticated -- {@code LoginProtectionListener} authorizes
     * in-game actions through {@link #isLoggedIn(UUID)}, not {@link #hasValidSession(Player)}, and
     * only the unregister and resetPassword paths separately called {@code
     * forceReauthenticationIfOnline}. This is now the single entry point every credential-changing
     * path must be sufficient by calling alone: it forces re-authentication itself, so no caller
     * can forget the step.
     * <p>
     * Also advances this player's invalidation generation ({@link #getInvalidationGeneration
     * (UUID)}), first -- before {@link #cancelPendingPanelRequest(UUID)}, which can only cancel
     * a panel request that has already been inserted into {@code pendingPanelRequests}. A {@code /panel} request captured before this call, but
     * not yet published by {@link #requestPanelLink(Player, long)} at the time this call runs,
     * has nothing for the cancellation to find. The generation bump is what {@code
     * requestPanelLink} checks to refuse publishing that now-stale request when its worker
     * finally runs.
     * <p>
     * The generation bump and the cancellation below run inside a {@code synchronized} block
     * keyed on this player's own {@link AtomicLong} generation cell -- the same cell {@link
     * #requestPanelLink(Player, long)} synchronizes on around its own check-then-insert. That
     * is what closes the narrow window a plain read-then-write left open: without a shared
     * lock, an invalidation landing between {@code requestPanelLink}'s generation read and its
     * {@code pendingPanelRequests} insert would find nothing to cancel (nothing had been
     * inserted yet) while the generation had already been read as matching, so the stale
     * request would still get published. With both sides synchronized on the same per-player
     * cell, either this method's bump-and-cancel runs completely before {@code
     * requestPanelLink}'s check-and-insert (so the check then sees the bumped generation and
     * refuses), or it runs completely after (so the cancellation finds the entry {@code
     * requestPanelLink} just inserted and removes it) -- there is no interleaving that lets a
     * stale request survive either path.
     *
     * @param playerUuid the player whose sessions should end, and whose active login state
     *                   should be revoked if they are online
     */
    public void invalidateSession(UUID playerUuid) {
        invalidateSession(playerUuid, true);
    }

    /**
     * As {@link #invalidateSession(UUID)}, with control over whether the online-player credential
     * prompt is presented.
     * <p>
     * {@link #resetPasswordForRecovery(UUID, String)} -- the only caller that passes {@code
     * false} -- is followed immediately by the caller ({@code RecoverCommand}) calling {@link
     * #completeLogin(Player)} itself. Presenting the prompt in between would show "log in now"
     * immediately followed by "you are logged in" in text mode, or leave a credential GUI open
     * that {@code completeLogin} never used to close in GUI mode (see the fix there). Every other
     * caller of {@link #invalidateSession(UUID)} (unregister, both admin {@code resetPassword}
     * overloads, {@code changePassword}) keeps going through the single-argument overload above,
     * so it keeps presenting the prompt exactly as before -- this overload only changes behaviour
     * for the one path that opts in.
     *
     * @param playerUuid the player whose sessions should end, and whose active login state
     *                   should be revoked if they are online
     * @param presentPrompt whether to show the credential prompt to an online player being
     *                      revoked; {@code false} only for the successful-recovery path, which
     *                      re-authenticates the player itself immediately afterward
     */
    void invalidateSession(UUID playerUuid, boolean presentPrompt) {
        AtomicLong generationCell = invalidationGenerations.computeIfAbsent(playerUuid, k -> new AtomicLong());
        synchronized (generationCell) {
            generationCell.incrementAndGet();
            String suffix = ":" + playerUuid;
            sessions.keySet().removeIf(key -> key.endsWith(suffix));
            cancelPendingPanelRequest(playerUuid);
        }
        forceReauthenticationIfOnline(playerUuid, presentPrompt);
    }

    /**
     * Get the current invalidation generation for a player -- a counter {@link
     * #invalidateSession(UUID)} increments every time it runs for that player, defaulting to
     * {@code 0} for a player who has never been invalidated.
     * <p>
     * Callers that schedule work asynchronously after a player-facing action (e.g. {@code
     * /panel}'s worker, which waits for the framework's create before {@link #requestPanelLink(Player,
     * long)} actually publishes anything) should capture this value up front, before scheduling,
     * and pass it back in so the eventual publish can refuse itself if an invalidation landed in
     * the gap. See PR #18.
     *
     * @param playerUuid the player to check
     * @return the current invalidation generation, {@code 0} if never invalidated
     */
    public long getInvalidationGeneration(UUID playerUuid) {
        AtomicLong generation = invalidationGenerations.get(playerUuid);
        return generation == null ? 0L : generation.get();
    }

    /**
     * End every pending {@code /panel} request of a player, in flight or created, and resume their countdown. The
     * single entry point reached via invalidateSession, so every credential-changing path (unregister, both
     * resetPassword overloads, changePassword) ends a pending link the same way -- rather than a deleted or
     * credential-reset account being logged back in by a completion of a link requested before the account changed.
     *
     * @param playerUuid the player whose pending panel requests end
     */
    private void cancelPendingPanelRequest(UUID playerUuid) {
        dropPendingPanelRequests(playerUuid);
        syncPanelPause(playerUuid);
    }

    /**
     * Forget every pending {@code /panel} request of a player, with all its bookkeeping (deadline, in-flight mark,
     * latest-link pointer), without touching the countdown pause. A completion or a create answer that arrives
     * afterwards finds no pending request and logs nobody in.
     *
     * @param playerUuid the player whose pending requests are forgotten
     */
    private void dropPendingPanelRequests(UUID playerUuid) {
        pendingPanelRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(playerUuid)) {
                pendingPanelDeadlines.remove(entry.getKey());
                panelCreatesInFlight.remove(entry.getKey());
                return true;
            }
            return false;
        });
        latestPanelRequest.remove(playerUuid);
    }

    /**
     * Revoke a currently online player's active login state and let {@link #checkTimeouts()}
     * enforce the configured login timeout on them again, as if they had just joined.
     * <p>
     * Reported in review of PR #18: {@link #invalidateSession(UUID)} used to only end the
     * remembered {@code sessions} entry, but {@code LoginProtectionListener} authorizes in-game
     * actions through {@link #isLoggedIn(UUID)}, not {@link #hasValidSession(Player)} -- so an
     * already-authenticated online connection stayed fully authorized with the old credentials
     * until it happened to disconnect, defeating an administrative password reset issued in
     * response to a compromised, currently-connected account. Deliberately does not replay
     * {@link #onPlayerJoin(Player)}: that replay is a separate, already-fixed defect because it
     * re-runs the session auto-login check.
     * <p>
     * As of the F-L1 fix, {@link #invalidateSession(UUID)} calls this itself, so this method no
     * longer needs a separate call from every credential-changing path -- kept {@code private}
     * since {@code invalidateSession} is the only remaining caller and the sole intended entry
     * point.
     * <p>
     * Flipping the flag alone left a revoked online player with no way back to the credential
     * screen -- {@code LoginProtectionListener} only ever opens the login/register GUI from
     * {@code PlayerJoinEvent} or a blocked action, neither of which fires again on its own
     * after this silent flag flip, so the player sat frozen by the action guards until {@link
     * #checkTimeouts()} eventually kicked them. Now also calls {@link
     * #presentCredentialPrompt(Player)} to show the same prompt immediately.
     * <p>
     * {@code presentPrompt} lets {@link #invalidateSession(UUID, boolean)} suppress the prompt
     * for the successful-recovery path, which re-authenticates the player itself immediately
     * afterward -- see that overload's javadoc.
     *
     * @param playerUuid the player to force back into the unauthenticated state, if online
     * @param presentPrompt whether to show the credential prompt; {@code false} only for
     *                      successful recovery
     */
    private void forceReauthenticationIfOnline(UUID playerUuid, boolean presentPrompt) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            loggedInPlayers.put(playerUuid, false);
            joinTimes.put(playerUuid, clock.getAsLong());
            if (presentPrompt) {
                applyNoSessionProtections(player);
                presentCredentialPrompt(player);
            }
        }
    }

    /**
     * Reapply the no-session protections -- a temporary blindness effect, and a teleport to the
     * configured spawn location -- that an unauthenticated player already receives on join, for a
     * player whose credentials have just been revoked while connected.
     * <p>
     * {@code forceReauthenticationIfOnline} (PR #18): before this fix, revoking an online
     * player's session (account deletion, an administrative password reset, or a self-service
     * password change) only cleared the login flag and restarted the join timeout. {@link
     * #onPlayerJoin(Player)} was the only code applying {@code blind-effect} / {@code
     * spawn-location.enabled}, and it never runs again for an already-connected player, so a
     * revoked client stayed fully sighted at its current, potentially sensitive location despite
     * now being unauthenticated -- until it happened to reconnect.
     * <p>
     * Shared with {@link #onPlayerJoin(Player)} so both paths apply the identical protections the
     * identical way; neither call site touches {@code loggedInPlayers}/{@code joinTimes} or
     * session auto-login here -- that stays {@link #forceReauthenticationIfOnline(UUID, boolean)}'s
     * and {@link #onPlayerJoin(Player)}'s own concern, not this method's.
     * <p>
     * Dispatched onto the main thread via {@link LoginProtectionListener#dispatchOnMainThread},
     * since potion effects and teleports are main-thread-only Bukkit APIs and a caller revoking a
     * session (e.g. an admin command) is not guaranteed to already be there.
     * <p>
     * If a spawn teleport applies, the player's current location is recorded into {@code
     * originalLocations} (only if not already present -- {@link #completeLogin(Player)} removes
     * that entry on every successful login, so a revoked-and-reconnected player would otherwise
     * have no recorded location to restore on their next successful login).
     *
     * @param player the online player to apply the protections to
     */
    private void applyNoSessionProtections(Player player) {
        LoginProtectionListener.dispatchOnMainThread(player, plugin, bukkitPlugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (config.isBlindEffect()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
            }

            // An unauthenticated player rides nothing: a vehicle would carry them, and a passenger
            // who does not steer gets no PlayerMoveEvent to hold them in place (UltiKits/UltiLogin#41).
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }

            if (config.isSpawnLocationEnabled()) {
                World world = Bukkit.getWorld(config.getSpawnWorld());
                if (world != null) {
                    originalLocations.putIfAbsent(player.getUniqueId(), player.getLocation().clone());
                    Location spawn = new Location(world, config.getSpawnX(), config.getSpawnY(), config.getSpawnZ());
                    player.teleport(spawn);
                }
            }
        });
    }

    /**
     * Present the login or register credential prompt -- the GUI page or a plain chat message,
     * chosen by {@link LoginConfig#isGuiModeEnabled()} and the player's current registration
     * state -- exactly as {@link LoginProtectionListener} already does when re-prompting a
     * player after a blocked action.
     * <p>
     * Delegates to {@link LoginProtectionListener#presentCredentialPrompt(Player,
     * UltiToolsPlugin, LoginService, Plugin)} so {@code forceReauthenticationIfOnline}
     * can show the same prompt after an administrative credential change (PR #18) without duplicating the GUI-vs-text branching a second time.
     * <p>
     * Deliberately does not consult {@link #hasValidSession(Player)}: unlike {@link
     * #onPlayerJoin(Player)}, this must never silently re-authenticate the player through a
     * remembered session -- it only ever shows the credential entry screen.
     *
     * @param player the player to prompt; must be online
     */
    public void presentCredentialPrompt(Player player) {
        LoginProtectionListener.presentCredentialPrompt(player, plugin, this, bukkitPlugin);
    }

    /**
     * Mark a player as mid-transition between credential GUIs, so {@link
     * com.ultikits.plugins.login.gui.LoginGUIPage#onClose} and {@link
     * com.ultikits.plugins.login.gui.RegisterGUIPage#onClose} skip their own reopen logic for the
     * GUI a deliberate transition is in the process of replacing.
     * <p>
     * An online, registered-but-unauthenticated player with {@code LoginGUIPage} open,
     * unregistered by an admin, had the revocation prompt open {@code RegisterGUIPage} over it
     * -- which, in real Bukkit, implicitly fires an {@code InventoryCloseEvent} for the GUI
     * being replaced, synchronously, as part of opening the new one. Without this marker, that
     * close hook could schedule its own reopen of the GUI being replaced, which (once it fired)
     * closed the new GUI in turn and triggered its own reopen -- the two screens ping-ponging
     * every 10 ticks until the player was kicked by the login timeout. See {@link
     * #endCredentialGuiTransition(UUID)} and {@link #isCredentialGuiTransitioning(UUID)}.
     *
     * @param playerUuid the player whose credential GUI is about to be deliberately replaced
     */
    public void beginCredentialGuiTransition(UUID playerUuid) {
        credentialGuiTransitions.add(playerUuid);
    }

    /**
     * Clear the transition marker set by {@link #beginCredentialGuiTransition(UUID)}, once the
     * replacement credential GUI has actually been opened.
     *
     * @param playerUuid the player whose credential GUI transition has finished
     */
    public void endCredentialGuiTransition(UUID playerUuid) {
        credentialGuiTransitions.remove(playerUuid);
    }

    /**
     * Whether a player is currently mid-transition between credential GUIs -- see {@link
     * #beginCredentialGuiTransition(UUID)}.
     *
     * @param playerUuid the player to check
     * @return true if a credential GUI transition is currently in progress for this player
     */
    public boolean isCredentialGuiTransitioning(UUID playerUuid) {
        return credentialGuiTransitions.contains(playerUuid);
    }

    /**
     * Mark a player as currently having a {@link LoginGUIPage} or {@link RegisterGUIPage} open,
     * called from that page's own {@code onOpen}.
     * <p>
     * The delayed reopen callback scheduled by {@code onClose} (see {@link
     * #registerCredentialGuiReopenTask(UUID, BukkitTask)}) checks this before opening another
     * credential GUI, so a reopen queued before a credential change cannot stack a second GUI on
     * top of one {@link #presentCredentialPrompt(Player)} already opened while the reopen was
     * pending.
     *
     * @param playerUuid the player whose credential GUI just opened
     */
    public void markCredentialGuiOpen(UUID playerUuid) {
        openCredentialGuiPlayers.add(playerUuid);
    }

    /**
     * Clear the marker set by {@link #markCredentialGuiOpen(UUID)}, called from a credential GUI
     * page's own {@code onClose} regardless of whether that close hook goes on to schedule a
     * reopen -- the GUI genuinely is closing either way.
     *
     * @param playerUuid the player whose credential GUI just closed
     */
    public void markCredentialGuiClosed(UUID playerUuid) {
        openCredentialGuiPlayers.remove(playerUuid);
    }

    /**
     * Whether a player currently has a {@link LoginGUIPage} or {@link RegisterGUIPage} open --
     * see {@link #markCredentialGuiOpen(UUID)}.
     *
     * @param playerUuid the player to check
     * @return true if a credential GUI is currently open for this player
     */
    public boolean isCredentialGuiOpen(UUID playerUuid) {
        return openCredentialGuiPlayers.contains(playerUuid);
    }

    /**
     * Register the delayed reopen task a credential GUI's {@code onClose} just scheduled for a
     * player, cancelling whatever task was previously registered for them (a stale reopen this
     * new one supersedes).
     * <p>
     * Added in PR #18.
     *
     * @param playerUuid the player the reopen task was scheduled for
     * @param task the task {@code Bukkit.getScheduler().runTaskLater(...)} returned
     */
    public void registerCredentialGuiReopenTask(UUID playerUuid, BukkitTask task) {
        BukkitTask previous = pendingCredentialGuiReopenTasks.put(playerUuid, task);
        if (previous != null && previous != task) {
            previous.cancel();
        }
    }

    /**
     * Remove the pending-reopen registry entry for {@code playerUuid}, called by a queued
     * reopen's own runnable once it actually fires (whether or not it goes on to reopen a GUI).
     * <p>
     * Unconditional, rather than a conditional remove keyed on the specific task removing itself:
     * {@link #registerCredentialGuiReopenTask(UUID, BukkitTask)} always cancels whatever task was
     * previously registered for a player before storing a new one, and a cancelled {@link
     * BukkitTask} is guaranteed by the scheduler contract to never run -- so at most one
     * registered task per player can ever actually reach this method. A conditional remove keyed
     * on task identity would additionally require the caller to already hold a reference to the
     * exact {@link BukkitTask} {@code Bukkit.getScheduler().runTaskLater(...)} returns from
     * *inside* the very runnable that call schedules, which is only well-defined once the
     * scheduling call has returned -- true for every real Bukkit scheduler (delayed tasks cannot
     * run synchronously as part of the call that schedules them), but not for a test double that
     * chooses to invoke the runnable inline as part of stubbing {@code runTaskLater} itself.
     *
     * @param playerUuid the player whose queued reopen task is removing itself
     */
    public void clearCredentialGuiReopenTask(UUID playerUuid) {
        pendingCredentialGuiReopenTasks.remove(playerUuid);
    }

    /**
     * Cancel and remove any credential-GUI reopen task currently queued for a player, without
     * scheduling a replacement.
     * <p>
     * Called from {@link LoginProtectionListener#presentCredentialPrompt} before it opens a fresh
     * credential GUI, so a reopen a player's own GUI close queued before this credential change
     * cannot fire later and stack a second GUI on top of the one this call is about to open.
     *
     * @param playerUuid the player whose queued reopen should be cancelled
     */
    public void cancelPendingCredentialGuiReopen(UUID playerUuid) {
        BukkitTask task = pendingCredentialGuiReopenTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Handle player join.
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        loggedInPlayers.put(uuid, false);
        joinTimes.put(uuid, clock.getAsLong());
        
        // Store original location
        originalLocations.put(uuid, player.getLocation().clone());
        
        // Check session
        if (hasValidSession(player)) {
            completeLogin(player);
            player.sendMessage(ChatColor.GREEN + i18n("session_login"));
            return;
        }
        
        // Apply the no-session protections (blind effect / spawn teleport) -- shared with
        // forceReauthenticationIfOnline(), see applyNoSessionProtections().
        applyNoSessionProtections(player);

        // Send prompt based on mode
        if (isRegistered(uuid)) {
            String message;
            if (hasNoGamePassword(uuid)) {
                // An account created on the web has no game password: no password can match it, so
                // it is pointed at /panel and reminded to set a backup password, in both modes (GUI
                // mode never shows it the number pad, maintainer decision 2026-10-09; the reminder
                // on every join is the maintainer's answer to DECISIONS question 1).
                message = i18n("login_no_game_password_backup");
            } else {
                message = config.isGuiModeEnabled()
                    ? config.getLoginPromptGui()
                    : config.getLoginPrompt();
            }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            String message = config.isGuiModeEnabled() 
                ? config.getRegisterPromptGui() 
                : config.getRegisterPrompt();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }
    
    /**
     * Handle player quit.
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        loggedInPlayers.remove(uuid);
        joinTimes.remove(uuid);
        originalLocations.remove(uuid);
        // A link belongs to the connection that asked for it: a completion still in flight when the
        // player left must not log in whoever joins next under the same name (gate-1 review F1).
        dropPendingPanelRequests(uuid);
        clearPanelPause(uuid);
    }
    
    /**
     * Complete login process.
     * <p>
     * Closes an already-open credential GUI, if the player is looking at one. Before this, a
     * caller that authenticates a player directly -- most notably the successful email-recovery
     * path ({@code RecoverCommand.resetPassword} -> {@code
     * EmailVerificationService.resetPasswordAfterRecovery} -> here) without going through that
     * GUI's own {@code attemptLogin()} click handler -- left a {@link LoginGUIPage} or {@link
     * RegisterGUIPage} open on screen even though {@code loggedInPlayers} was already flipped to
     * {@code true}: nothing else ever closes it for that caller. The flag is flipped above,
     * before this check runs, so if {@code closeInventory()} below triggers {@link
     * LoginGUIPage#onClose}'s own re-open-if-not-logged-in guard, it already sees the player as
     * logged in and does not reopen the GUI it is closing.
     *
     * @param player the player to complete login for
     */
    public void completeLogin(Player player) {
        clearFailedAttempts(player);
        UUID uuid = player.getUniqueId();
        loggedInPlayers.put(uuid, true);
        joinTimes.remove(uuid);
        clearPanelPause(uuid);

        closeCredentialGuiIfOpen(player);

        // Remove blind effect
        player.removePotionEffect(PotionEffectType.BLINDNESS);

        // Restore original location if we teleported to spawn. The check is on the entry's
        // presence, not the *current* value of spawn-location.enabled: the entry is the record
        // that a teleport actually happened (recorded by onPlayerJoin / applyNoSessionProtections
        // at teleport time). Gating this on the current config instead stranded a player at spawn
        // and discarded their saved location if the setting was flipped to false between the
        // teleport and this call (PR #18).
        Location original = originalLocations.get(uuid);
        if (original != null) {
            player.teleport(original);
        }
        originalLocations.remove(uuid);
    }

    /**
     * Close the player's currently open inventory if it is a {@link LoginGUIPage} or {@link
     * RegisterGUIPage}, leaving any other open inventory (or none at all) untouched.
     * <p>
     * Defensive against a bare test double: a mock {@link Player} that has not stubbed {@link
     * Player#getOpenInventory()} returns {@code null} from that call, not a real {@code
     * InventoryView} wrapping the player's own inventory the way a live server always would --
     * both that and an unstubbed {@code getTopInventory()}/{@code getHolder()} are checked for
     * null before use so this never throws for the (overwhelmingly common) case where no
     * credential GUI is open.
     *
     * @param player the player whose open inventory should be checked
     */
    private void closeCredentialGuiIfOpen(Player player) {
        InventoryView openInventory = player.getOpenInventory();
        Inventory topInventory = openInventory == null ? null : openInventory.getTopInventory();
        InventoryHolder holder = topInventory == null ? null : topInventory.getHolder();
        if (holder instanceof LoginGUIPage || holder instanceof RegisterGUIPage) {
            player.closeInventory();
        }
    }
    
    /**
     * Force login a player (admin command).
     */
    public boolean forceLogin(Player player) {
        if (!isRegistered(player.getUniqueId())) {
            return false;
        }
        if (isLoggedIn(player.getUniqueId())) {
            return false;
        }
        completeLogin(player);
        return true;
    }
    
    /**
     * Reset player password (admin command).
     * @return the new random password, or null if failed
     */
    public String resetPassword(UUID playerUuid) {
        AccountData account = getAccount(playerUuid);
        if (account == null) {
            return null;
        }
        
        // Generate random password
        String newPassword = generateRandomPassword();
        String newSalt = generateSalt();
        String newHash = hashPassword(newPassword, newSalt);
        
        account.setSalt(newSalt);
        account.setPasswordHash(newHash);
        if (!AccountWrites.update(dataOperator, plugin.getLogger(), account,
                i18n("log_password_reset_failed"), i18n("log_account_row_gone"))) {
            return null;
        }

        // Only on the success branch -- a failed update must not log the player out.
        // invalidateSession revokes the online player's active login state too (F-L1).
        invalidateSession(playerUuid);

        return newPassword;
    }
    
    /**
     * Reset player password with specific password (admin command).
     * <p>
     * The email-recovery flow used to delegate here too, but this overload's {@link
     * #invalidateSession(UUID)} call presents the credential prompt -- correct for an admin
     * resetting someone else's password, wrong for a player who just recovered their own and is
     * about to be logged straight back in by the caller. Recovery now goes through {@link
     * #resetPasswordForRecovery(UUID, String)} instead, which shares this method's
     * account-update logic but suppresses the prompt.
     */
    public boolean resetPassword(UUID playerUuid, String newPassword) {
        return resetPasswordInternal(playerUuid, newPassword, true);
    }

    /**
     * Reset player password for the successful email-recovery path specifically: same
     * account-update and invalidation as {@link #resetPassword(UUID, String)}, but without
     * presenting the credential prompt.
     * <p>
     * {@code EmailVerificationService .resetPasswordAfterRecovery} is called from {@code
     * RecoverCommand.resetPassword}, which calls {@link #completeLogin(Player)} immediately
     * afterward on success. Going through the public, prompt-presenting {@link
     * #resetPassword(UUID, String)} used to show the login/ register prompt (text message, or in
     * GUI mode a freshly reopened {@code LoginGUIPage}) an instant before {@code completeLogin}
     * ran -- in text mode a jarring "log in now" immediately followed by "you are logged in"; in
     * GUI mode a credential GUI left open that {@code completeLogin} never used to close (see the
     * fix there). Package-private: {@code EmailVerificationService} (same package) is the only
     * intended caller -- every other credential-changing path (unregister, admin resetPassword,
     * changePassword) keeps presenting the prompt through the public overload above, since none
     * of those callers re-authenticates the player themselves afterward.
     *
     * @param playerUuid the player recovering their account
     * @param newPassword the new password the recovery flow already validated
     * @return true if the reset succeeded
     */
    boolean resetPasswordForRecovery(UUID playerUuid, String newPassword) {
        return resetPasswordInternal(playerUuid, newPassword, false);
    }

    /**
     * Shared implementation for {@link #resetPassword(UUID, String)} and {@link
     * #resetPasswordForRecovery(UUID, String)} -- identical account lookup, salt/hash rotation,
     * and persistence; the only difference between the two public entry points is whether the
     * resulting invalidation presents the credential prompt.
     */
    private boolean resetPasswordInternal(UUID playerUuid, String newPassword, boolean presentPrompt) {
        AccountData account = getAccount(playerUuid);
        if (account == null) {
            return false;
        }

        String newSalt = generateSalt();
        String newHash = hashPassword(newPassword, newSalt);

        account.setSalt(newSalt);
        account.setPasswordHash(newHash);
        if (!AccountWrites.update(dataOperator, plugin.getLogger(), account,
                i18n("log_password_reset_failed"), i18n("log_account_row_gone"))) {
            return false;
        }

        // Only on the success branch -- a failed update must not log the player out.
        invalidateSession(playerUuid, presentPrompt);

        return true;
    }

    /**
     * Unregister a player (admin command).
     */
    public boolean unregister(UUID playerUuid) {
        AccountData account = getAccount(playerUuid);
        if (account == null) {
            return false;
        }

        dataOperator.delById(account.getId());

        // End every session and, if online, force the player back into the unauthenticated
        // state and reinitialize their login timeout (PR #18):
        // completeLogin already removed their joinTimes entry when they originally logged in,
        // so without re-adding one here checkTimeouts() would never see this
        // newly-unauthenticated player, silently disabling the login timeout while they remain
        // connected. No onPlayerJoin() replay: that replay was the defect -- it re-ran the join
        // flow, whose first act is the session check, so it found a session that had never been
        // cleared and logged the just-deleted account straight back in. The player is simply
        // left in the normal unauthenticated state.
        invalidateSession(playerUuid);

        return true;
    }
    
    /**
     * Get account by player name.
     */
    public AccountData getAccountByName(String playerName) {
        List<AccountData> accounts = dataOperator.query()
            .where("player_name").eq(playerName)
            .list();
        return accounts.isEmpty() ? null : accounts.get(0);
    }
    
    /**
     * Check for login timeouts.
     * Runs every second (20 ticks).
     */
    @Scheduled(period = 20, async = false)
    public void checkTimeouts() {
        long now = clock.getAsLong();
        long timeout = config.getLoginTimeout() * 1000L;

        for (Map.Entry<UUID, Long> entry : joinTimes.entrySet()) {
            // A pending /panel link holds the countdown (contract section 13); every other
            // unauthenticated restriction stays in LoginProtectionListener.
            if (panelPausedAt.containsKey(entry.getKey())) {
                continue;
            }
            if (now - entry.getValue() > timeout) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    String message = config.getTimeoutKick();
                    player.kickPlayer(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
        }

        // Clean up expired panel requests
        cleanupExpiredPanelRequests();
    }
    
    /**
     * Get account by UUID.
     */
    public AccountData getAccount(UUID playerUuid) {
        List<AccountData> accounts = dataOperator.query()
            .where("player_uuid").eq(playerUuid.toString())
            .list();
        return accounts.isEmpty() ? null : selectCanonicalAccount(accounts);
    }

    private AccountData selectCanonicalAccount(List<AccountData> accounts) {
        return accounts.stream()
            .min(Comparator.comparing(AccountData::getId, Comparator.nullsLast(String::compareTo)))
            .orElse(accounts.get(0));
    }
    
    /**
     * Change password.
     */
    public boolean changePassword(UUID playerUuid, String oldPassword, String newPassword) {
        AccountData account = getAccount(playerUuid);
        if (account == null) {
            return false;
        }
        
        // Verify old password
        String oldHash = hashPassword(oldPassword, account.getSalt());
        if (!hashesMatch(oldHash, account.getPasswordHash())) {
            return false;
        }
        
        // Generate new salt and hash
        String newSalt = generateSalt();
        String newHash = hashPassword(newPassword, newSalt);
        
        account.setSalt(newSalt);
        account.setPasswordHash(newHash);
        if (!AccountWrites.update(dataOperator, plugin.getLogger(), account,
                i18n("log_account_update_failed"), i18n("log_account_row_gone"))) {
            return false;
        }

        // Only on the success branch -- a rejected password change (wrong old password, or a
        // failed persist above) must not log the player out. F-L1 (13-uat-results.md #14):
        // invalidateSession now also revokes the online player's active login state, not just
        // the persisted session -- previously this was the one credential-changing path that
        // called invalidateSession without a matching forceReauthenticationIfOnline, so a real
        // /changepassword reported success while the player stayed fully authenticated.
        invalidateSession(playerUuid);

        return true;
    }

    /**
     * Whether the player has an account and it has no game password -- it was created by a web
     * registration through {@code /panel} and has no backup password yet.
     *
     * @param playerUuid the player
     * @return {@code true} only for an existing account with an empty password hash
     */
    public boolean hasNoGamePassword(UUID playerUuid) {
        AccountData account = getAccount(playerUuid);
        return account != null && isEmptyHash(account.getPasswordHash());
    }

    /**
     * Set the first game password of an account created on the web ({@code /setpassword}).
     * <p>
     * Refuses an account that already has a password -- that is {@code /changepassword}'s job, which
     * asks for the old one -- so this can never replace a password without knowing it. The caller
     * checks that the player is logged in. Unlike a password change, this does not end the player's
     * session: nothing that authenticated them changes.
     *
     * @param playerUuid  the player
     * @param newPassword the new password, already checked against the password policy
     * @return {@code true} when the password was stored
     */
    public boolean setInitialPassword(UUID playerUuid, String newPassword) {
        AccountData account = getAccount(playerUuid);
        if (account == null || !isEmptyHash(account.getPasswordHash())) {
            return false;
        }
        String newSalt = generateSalt();
        account.setSalt(newSalt);
        account.setPasswordHash(hashPassword(newPassword, newSalt));
        return AccountWrites.update(dataOperator, plugin.getLogger(), account,
                i18n("log_account_update_failed"), i18n("log_account_row_gone"));
    }

    /**
     * Count registrations by IP.
     */
    private int countRegistrationsByIp(String ip) {
        List<AccountData> accounts = dataOperator.query()
            .where("register_ip").eq(ip)
            .list();
        return accounts.size();
    }
    
    /**
     * Get player IP.
     */
    public String getPlayerIp(Player player) {
        if (player.getAddress() != null) {
            return player.getAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
    
    /**
     * Generate random salt.
     */
    private String generateSalt() {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Generate random password (for admin reset).
     */
    private String generateRandomPassword() {
        if (config.isGuiModeEnabled()) {
            // GUI mode: generate numeric password
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < config.getGuiPasswordLength(); i++) {
                sb.append(random.nextInt(9) + 1); // 1-9
            }
            return sb.toString();
        } else {
            // Command mode: generate alphanumeric password
            String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        }
    }
    
    /**
     * Hash password with salt using SHA-256.
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = password + salt;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Compare two Base64-encoded password hashes in constant time. {@code String.equals}
     * short-circuits on the first mismatched character, which is a textbook timing side-channel
     * for a stored-hash comparison. Decodes both to bytes and defers to {@link
     * MessageDigest#isEqual(byte[], byte[])}, which always compares the full length of the
     * shorter input regardless of where the first difference is.
     */
    private boolean hashesMatch(String computedHash, String storedHash) {
        if (computedHash == null || storedHash == null) {
            return false;
        }
        byte[] computedBytes;
        byte[] storedBytes;
        try {
            computedBytes = Base64.getDecoder().decode(computedHash);
            storedBytes = Base64.getDecoder().decode(storedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(computedBytes, storedBytes);
    }
    
    /**
     * Check if command is allowed before login.
     */
    public boolean isCommandAllowed(String command) {
        String cmd = command.toLowerCase().split(" ")[0].replaceFirst("/", "");
        return config.getAllowedCommands().contains(cmd);
    }
    
    /**
     * Get config.
     */
    public LoginConfig getConfig() {
        return config;
    }
    
    // ==================== /panel: UltiCloud player links ====================

    /**
     * Check if UltiCloud panel integration is enabled.
     */
    public boolean isPanelEnabled() {
        return config.isUlticloudEnabled();
    }

    /**
     * Generate a 6-digit verification code.
     */
    public String generateVerificationCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * Request a panel link for a player, without fencing against an invalidation racing the request itself.
     * <p>
     * Equivalent to {@link #requestPanelLink(Player, long)} called with the player's <em>current</em> invalidation
     * generation, captured at the start of this call -- which means it can never observe itself as stale. Suitable
     * only for a caller on the same, uninterrupted call stack as the eventual publish (tests). {@code /panel}'s own
     * worker uses the two-argument overload, capturing the generation before it schedules any asynchronous work.
     * Package-private for that reason: wrapping this overload in asynchronous scheduling would capture the
     * generation inside the worker and reopen the race the two-argument overload closes.
     *
     * @param player the player requesting the link
     * @return the result, with the URL and link id on success
     */
    PanelLinkResult requestPanelLink(Player player) {
        return requestPanelLink(player, getInvalidationGeneration(player.getUniqueId()));
    }

    /**
     * Ask the framework's player-link API for a one-time panel link for a player, on the calling (asynchronous)
     * thread, refusing to publish it if the invalidation generation the caller captured before scheduling no longer
     * matches.
     * <p>
     * <b>What is sent.</b> The player's UUID and name, whether the player is logged in on this connection and
     * whether UltiLogin holds an account for them, and the identity proofs those two facts allow (see {@link
     * #offeredProofs(boolean, boolean)}); never a password or a digest. The module makes no HTTP request: the
     * framework sends the create over its UltiCloud connection ({@link PlayerLinkPort}).
     * <p>
     * <b>Fences.</b> The request is published locally (inserted into {@code pendingPanelRequests} under a local id)
     * only if the captured generation still matches; that check and the insert run inside a block synchronized on the
     * player's generation cell, the lock {@link #invalidateSession(UUID)} takes around its bump-and-cancel, so an
     * invalidation either lands before (the check refuses) or after (its cancellation finds the entry). The same
     * cell is held across create and callback registration to order them with event scheduling, including an answer
     * that arrives before create returns. Every operation under this cell must be non-blocking. The callback queues
     * main-thread publication before either event can be queued;
     * only that task re-keys the local id to the gateway's link id, after checking the generation and local entry
     * again. An invalidation or quit in flight discards the link. The worker waits outside the lock, for at most
     * the local create cap (45 seconds), for publication rather than just the raw create answer. A timeout and
     * publication choose one terminal result under the same cell, so a late task cannot resurrect the request.
     * {@code PanelCommand} checks once more on the main thread ({@link #isPanelRequestCurrent(UUID, String, long)}).
     * <p>
     * The login countdown pauses from the local insert; every failure removes the request again and resumes the
     * countdown silently, with exactly the time that was left.
     *
     * @param player             the player requesting the link
     * @param expectedGeneration the invalidation generation the caller captured before scheduling this call, from
     *                           {@link #getInvalidationGeneration(UUID)}
     * @return the result: on success the URL and the link id the request is now pending under
     */
    public PanelLinkResult requestPanelLink(Player player, long expectedGeneration) {
        UUID playerUuid = player.getUniqueId();
        String requestId = UUID.randomUUID().toString();
        AtomicLong generationCell = invalidationGenerations.computeIfAbsent(playerUuid, k -> new AtomicLong());
        java.util.concurrent.CompletableFuture<PanelLinkResult> publication = new java.util.concurrent.CompletableFuture<>();
        try {
            if (!isPanelEnabled()) {
                return new PanelLinkResult(false, null, "Panel integration not enabled");
            }
            String playerName = player.getName();
            boolean authenticated = isLoggedIn(playerUuid);
            // A failed read is not an absent account or spare registration capacity. Return a failure to the
            // command worker so it can still schedule its existing main-thread error message.
            boolean accountExists = isRegistered(playerUuid);
            String requestingIp = getPlayerIp(player);
            if (!accountExists && registrationLimitReached(requestingIp)) {
                return new PanelLinkResult(false, null, "Registration limit reached for this address", null,
                        PanelLinkResult.Failure.IP_LIMIT);
            }
            synchronized (generationCell) {
                if (generationCell.get() != expectedGeneration) {
                    return stalePanelCreation();
                }
                pendingPanelRequests.put(requestId, playerUuid);
                pendingPanelDeadlines.put(requestId, clock.getAsLong() + panelCreateCapMillis);
                panelCreatesInFlight.add(requestId);
                latestPanelRequest.put(playerUuid, requestId);
                syncPanelPause(playerUuid);
                // create is non-blocking. Hold this cell only through callback registration, never through
                // get(): even a future completed before create returns must enqueue publication before an event.
                playerLinkPort.create(playerUuid, playerName, authenticated, accountExists,
                        offeredProofs(authenticated, accountExists)).whenComplete((creation, error) -> {
                    try {
                        // Remember even a create whose publication is now stale: its completion can arrive late.
                        if (error == null && creation != null && creation.isSuccess()) {
                            prunePanelLinkOrigins(clock.getAsLong());
                            panelLinkOrigins.put(creation.linkId(), new PanelLinkOrigin(playerUuid, requestingIp,
                                    linkDeadline(creation.expiresAt()) + LINK_ORIGIN_GRACE_MILLIS));
                        }
                        queuePanelTask(playerUuid, () -> publishPanelCreation(playerUuid, requestId,
                                expectedGeneration, generationCell, publication, creation, error));
                    } catch (RuntimeException e) {
                        failPanelPublication(requestId, generationCell, publication,
                                PlayerLinkPort.LinkFailure.OTHER, String.valueOf(e.getMessage()));
                    }
                });
            }
        } catch (RuntimeException e) {
            return failPanelPublication(requestId, generationCell, publication,
                    PlayerLinkPort.LinkFailure.OTHER, String.valueOf(e.getMessage()));
        }
        return awaitPanelPublication(requestId, generationCell, publication);
    }

    /** Order publication and both event consumers at the same per-player queue boundary. Never wait here. */
    private void queuePanelTask(UUID playerUuid, Runnable task) {
        AtomicLong cell = invalidationGenerations.computeIfAbsent(playerUuid, k -> new AtomicLong());
        synchronized (cell) {
            Bukkit.getScheduler().runTask(bukkitPlugin, task);
        }
    }

    /** Publish only on the main thread, before a subsequently delivered completion or expiry can consume the id. */
    private void publishPanelCreation(UUID playerUuid, String requestId, long expectedGeneration,
            AtomicLong cell, java.util.concurrent.CompletableFuture<PanelLinkResult> publication,
            PlayerLinkPort.LinkCreation creation, Throwable error) {
        synchronized (cell) {
            if (publication.isDone()) {
                return; // The worker already ended this request at its cap or on interruption.
            }
            try {
                if (error != null || creation == null) {
                    failPanelPublication(requestId, cell, publication, PlayerLinkPort.LinkFailure.OTHER,
                            error == null ? "no answer" : String.valueOf(error.getMessage()));
                } else if (!creation.isSuccess()) {
                    failPanelPublication(requestId, cell, publication, creation.failure(), creation.reason());
                } else if (cell.get() != expectedGeneration || !pendingPanelRequests.containsKey(requestId)) {
                    cleanupPanelRequest(requestId);
                    publication.complete(stalePanelCreation());
                } else {
                    String linkId = creation.linkId();
                    long deadline = linkDeadline(creation.expiresAt());
                    pendingPanelRequests.put(linkId, playerUuid);
                    pendingPanelDeadlines.put(linkId, deadline);
                    latestPanelRequest.replace(playerUuid, requestId, linkId);
                    removePanelRequest(requestId);
                    publication.complete(new PanelLinkResult(true, creation.url(), null, linkId));
                }
            } catch (RuntimeException e) {
                failPanelPublication(requestId, cell, publication, PlayerLinkPort.LinkFailure.OTHER,
                        String.valueOf(e.getMessage()));
            }
        }
    }

    private static PanelLinkResult stalePanelCreation() {
        return new PanelLinkResult(false, null, "Session was invalidated before the request completed");
    }

    /** Choose a single terminal result under the publication lock; a late callback cannot resurrect this request. */
    private PanelLinkResult failPanelPublication(String requestId, AtomicLong cell,
            java.util.concurrent.CompletableFuture<PanelLinkResult> publication,
            PlayerLinkPort.LinkFailure failure, String reason) {
        synchronized (cell) {
            if (!publication.isDone()) {
                cleanupPanelRequest(requestId);
                publication.complete(failedCreation(PlayerLinkPort.LinkCreation.failed(failure, reason)));
            }
            return publication.getNow(null);
        }
    }

    /**
     * The identity proofs a link may offer, from what UltiLogin knows (the maintainer's three proofs):
     * <ul>
     *   <li>the in-game session only for a player logged in on this connection;</li>
     *   <li>the UltiKits account already bound to this identity, always (the gateway decides whether one is);</li>
     *   <li>a web registration only for a name UltiLogin has no account for -- never for a name that has one, so a
     *       name with a game password must be logged in in game ({@code /login}) before the web can claim it.</li>
     * </ul>
     * By (logged in, has account): (yes, yes) in-game session and linked account; (no, yes) linked account;
     * (no, no) linked account and web registration; (yes, no) -- an administrator's force-login of an unregistered
     * name -- all three.
     *
     * @param loggedIn   whether the player is logged in on this connection
     * @param hasAccount whether UltiLogin holds an account for the player
     * @return the proofs to offer, never empty
     */
    static java.util.Set<PlayerLinkPort.LinkProof> offeredProofs(boolean loggedIn, boolean hasAccount) {
        java.util.Set<PlayerLinkPort.LinkProof> proofs = java.util.EnumSet.of(PlayerLinkPort.LinkProof.LINKED_WEB_ACCOUNT);
        if (loggedIn) {
            proofs.add(PlayerLinkPort.LinkProof.IN_GAME_SESSION);
        }
        if (!hasAccount) {
            proofs.add(PlayerLinkPort.LinkProof.WEB_REGISTRATION);
        }
        return proofs;
    }

    /**
     * When a created link ends locally: its {@code expiresAt}, converted to this server's clock at receipt and
     * clamped to between 60 seconds and 10 minutes from now, so a clock that disagrees with the gateway's can neither
     * end the link at once nor hold the login countdown longer than ten minutes.
     */
    private long linkDeadline(java.time.Instant expiresAt) {
        long receipt = clock.getAsLong();
        long remaining = expiresAt.toEpochMilli() - receipt;
        return receipt + Math.max(MIN_LINK_LIFETIME_MILLIS, Math.min(MAX_LINK_LIFETIME_MILLIS, remaining));
    }

    /** Whether the operator's {@code max-register-per-ip} forbids another account from {@code ip}. */
    private boolean registrationLimitReached(String ip) {
        return config.getMaxRegisterPerIp() > 0 && countRegistrationsByIp(ip) >= config.getMaxRegisterPerIp();
    }

    /** Wait outside the generation lock for main-thread publication, not merely the framework's raw answer. */
    private PanelLinkResult awaitPanelPublication(String requestId, AtomicLong cell,
            java.util.concurrent.CompletableFuture<PanelLinkResult> publication) {
        try {
            return publication.get(panelCreateCapMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return failPanelPublication(requestId, cell, publication, PlayerLinkPort.LinkFailure.TIMED_OUT,
                    "no answer within " + panelCreateCapMillis + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failPanelPublication(requestId, cell, publication, PlayerLinkPort.LinkFailure.OTHER, "interrupted");
        } catch (java.util.concurrent.ExecutionException | RuntimeException e) {
            return failPanelPublication(requestId, cell, publication, PlayerLinkPort.LinkFailure.OTHER,
                    String.valueOf(e.getMessage()));
        }
    }

    /**
     * The result of a create that produced no link, after its request was cleaned up. Not connected, timed out and
     * any other failure are logged once to the console (they are the operator's to fix); a rate limit and a refusal
     * to issue links are the player's answer and are not logged.
     */
    private PanelLinkResult failedCreation(PlayerLinkPort.LinkCreation creation) {
        PanelLinkResult.Failure failure;
        switch (creation.failure()) {
            case NOT_CONNECTED:
                failure = PanelLinkResult.Failure.LINK_ERROR;
                break;
            case RATE_LIMITED:
                failure = PanelLinkResult.Failure.RATE_LIMITED;
                break;
            case NOT_PERMITTED:
                failure = PanelLinkResult.Failure.NOT_PERMITTED;
                break;
            default:
                failure = PanelLinkResult.Failure.GENERIC;
                break;
        }
        if (failure == PanelLinkResult.Failure.LINK_ERROR || failure == PanelLinkResult.Failure.GENERIC) {
            plugin.getLogger().warn(i18n("log_panel_link_failed").replace("{ERROR}", String.valueOf(creation.reason())));
        }
        return new PanelLinkResult(false, null, "Request failed: " + creation.reason(), null, failure);
    }

    /**
     * Check whether a published panel link request is still current for its player, immediately
     * before acting on a {@link PanelLinkResult} on the main thread.
     * <p>
     * {@link #requestPanelLink(Player, long)}'s own post-create re-check only closes the gap up to
     * the moment that method returns.
     * {@code PanelCommand}'s result-delivery callback runs later still -- it is itself scheduled
     * via {@code Bukkit.getScheduler().runTask(...)} after the worker thread's wait for the create
     * already returned -- so an invalidation landing in that final gap (between {@code
     * requestPanelLink}'s return and this callback actually executing on the main thread) could
     * still let a revoked player receive the link. Calling this at the
     * top of that callback, under the same per-player lock {@link #invalidateSession(UUID)}
     * synchronizes on, closes that last gap: a {@code false} result means the generation moved on
     * or the request itself was already cancelled/completed elsewhere, and the caller must discard
     * the result outright -- no link sent, no pause announced.
     *
     * @param playerUuid the player the request was made for
     * @param requestId the link id published in the {@link PanelLinkResult}, from {@link
     *                  PanelLinkResult#getLinkId()}
     * @param expectedGeneration the invalidation generation the caller captured before scheduling
     *                           its asynchronous work, from {@link #getInvalidationGeneration
     *                           (UUID)}
     * @return true if the request is still current and safe to act on
     */
    public boolean isPanelRequestCurrent(UUID playerUuid, String requestId, long expectedGeneration) {
        AtomicLong generationCell = invalidationGenerations.computeIfAbsent(playerUuid, k -> new AtomicLong());
        synchronized (generationCell) {
            return generationCell.get() == expectedGeneration && pendingPanelRequests.containsKey(requestId);
        }
    }

    private static void sendPanelMessage(Player player, String message) {
        if (player.isOnline()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    /**
     * Answer one link completion the framework delivered (called off the main thread). The work runs in one
     * main-thread task, as {@code /register} does, so an account check and insert can never interleave with it; the
     * returned future is completed there. A task that cannot be scheduled (the plugin is disabling) fails the future,
     * which sends no result: the gateway redelivers the completion later.
     *
     * @param completion what the gateway reported
     * @return the result, completed on the main thread
     */
    java.util.concurrent.CompletableFuture<PlayerLinkPort.CompletionResult> onLinkCompleted(
            PlayerLinkPort.LinkCompletion completion) {
        java.util.concurrent.CompletableFuture<PlayerLinkPort.CompletionResult> answer =
                new java.util.concurrent.CompletableFuture<>();
        try {
            queuePanelTask(completion.playerUuid(), () -> answerCompletion(completion, answer));
        } catch (RuntimeException e) {
            answer.completeExceptionally(e);
        }
        return answer;
    }

    /**
     * Run {@link #handleCompletion} and complete {@code answer} with its result. An unexpected failure answers the
     * result already recorded for this completion, if any; otherwise it fails the future (no result is sent, so the
     * gateway asks again) after resuming the player's countdown.
     */
    private void answerCompletion(PlayerLinkPort.LinkCompletion completion,
                                  java.util.concurrent.CompletableFuture<PlayerLinkPort.CompletionResult> answer) {
        try {
            answer.complete(handleCompletion(completion));
        } catch (RuntimeException e) {
            PlayerLinkPort.CompletionResult recorded = answeredCompletions.get(completion.completionId());
            if (recorded != null) {
                answer.complete(recorded);
                return;
            }
            syncPanelPause(completion.playerUuid());
            plugin.getLogger().warn(i18n("log_panel_completion_failed").replace("{ERROR}", String.valueOf(e)));
            answer.completeExceptionally(e);
        }
    }

    /**
     * Act on one link completion, on the main thread, and say what was done about a game account. Completions are
     * delivered at least once (redelivered after every reconnect until answered; the framework's persisted receipts
     * cover restarts), so a completion id answered in this run is answered again with its recorded result and
     * nothing else happens. Every result, including the error ones, is recorded before anyone is logged in or told
     * anything, so a duplicate can never repeat a side effect.
     * <p>
     * <b>Which connection may be logged in.</b> Only the connection that asked: the completion's link must still be
     * pending for the completion's player, which means the player has neither left nor had their credentials changed
     * since the link was created (both drop every pending link). A completion for any other link is <em>late</em>
     * (the player left, the server restarted, an administrator reset the password, or the link ended locally): it
     * still creates a requested account, but logs nobody in. An {@link PlayerLinkPort.LinkProof#IN_GAME_SESSION}
     * proof never logs a connection in: it proves the web user by the game session, not the other way round.
     * <p>
     * <b>Results for the connection that asked.</b>
     * <ul>
     *   <li>An account exists: logged in; {@code account_exists} if one was requested (it is never changed),
     *       otherwise {@code not_requested}.</li>
     *   <li>No account, none requested: told it is not registered in game; {@code not_requested}.</li>
     *   <li>No account, one requested (a web registration): the row {@code /register} would create, with an empty
     *       password hash and salt, then logged in; {@code created}. Over {@code max-register-per-ip}:
     *       {@code registration_rejected}; a failed insert: {@code auth_plugin_unavailable}.</li>
     * </ul>
     * Account reads and writes run here, on the main thread, as {@code /register}'s do. With
     * {@code ulticloud.enabled: false} nothing is created and nobody is logged in ({@code auth_plugin_unavailable}).
     * Whenever nobody is logged in, the player's countdown resumes with the time that was left.
     *
     * @param completion what the gateway reported
     * @return the result for the framework
     */
    private PlayerLinkPort.CompletionResult handleCompletion(PlayerLinkPort.LinkCompletion completion) {
        PlayerLinkPort.CompletionResult recorded = answeredCompletions.get(completion.completionId());
        if (recorded != null) {
            return recorded;
        }
        UUID playerUuid = completion.playerUuid();
        Player player = Bukkit.getPlayer(playerUuid);
        boolean current = takePendingLink(completion.linkId(), playerUuid) && player != null && player.isOnline();
        if (!isPanelEnabled()) {
            return refuse(completion, PlayerLinkPort.CompletionResult.AUTH_PLUGIN_UNAVAILABLE, null, null);
        }
        AccountData account = getAccount(playerUuid);
        if (!current) {
            return answerLateCompletion(completion, account, player);
        }
        boolean provision = completion.provisionGameAccount();
        if (account != null) {
            return grant(completion, provision ? PlayerLinkPort.CompletionResult.ACCOUNT_EXISTS
                    : PlayerLinkPort.CompletionResult.NOT_REQUESTED, player);
        }
        if (!provision) {
            return refuse(completion, PlayerLinkPort.CompletionResult.NOT_REQUESTED, player,
                    i18n("panel_not_registered_in_game"));
        }
        String ip = getPlayerIp(player);
        if (registrationLimitReached(ip)) {
            refuse(completion, PlayerLinkPort.CompletionResult.REGISTRATION_REJECTED, null, null);
            player.sendMessage(ChatColor.RED + i18n("ip_limit_reached"));
            return PlayerLinkPort.CompletionResult.REGISTRATION_REJECTED;
        }
        if (!insertWebAccount(playerUuid, player.getName(), ip)) {
            return refuse(completion, PlayerLinkPort.CompletionResult.AUTH_PLUGIN_UNAVAILABLE, player,
                    i18n("panel_account_create_failed"));
        }
        return grant(completion, PlayerLinkPort.CompletionResult.CREATED, player);
    }

    /**
     * A completion whose link is no longer pending for the connection that asked (DECISIONS item 10): a requested
     * account still obeys the creating address's cap and records that address while it is retained. After a restart
     * (or beyond the retention window) the address is unknown: the account has an empty address and skips the cap.
     * Nobody is logged in. A player online under another connection and not logged in is told to run {@code /panel}
     * again.
     */
    private PlayerLinkPort.CompletionResult answerLateCompletion(PlayerLinkPort.LinkCompletion completion,
                                                                  AccountData account, Player player) {
        PlayerLinkPort.CompletionResult result;
        if (account != null) {
            result = completion.provisionGameAccount() ? PlayerLinkPort.CompletionResult.ACCOUNT_EXISTS
                    : PlayerLinkPort.CompletionResult.NOT_REQUESTED;
        } else if (!completion.provisionGameAccount()) {
            result = PlayerLinkPort.CompletionResult.NOT_REQUESTED;
        } else {
            PanelLinkOrigin origin = panelLinkOrigins.get(completion.linkId());
            if (origin != null && clock.getAsLong() > origin.retainUntil) {
                panelLinkOrigins.remove(completion.linkId(), origin);
                origin = null;
            }
            String ip = origin != null && origin.playerUuid.equals(completion.playerUuid()) ? origin.ip : "";
            if (!ip.isEmpty() && registrationLimitReached(ip)) {
                result = PlayerLinkPort.CompletionResult.REGISTRATION_REJECTED;
            } else {
                result = insertWebAccount(completion.playerUuid(), completion.playerName(), ip)
                        ? PlayerLinkPort.CompletionResult.CREATED
                        : PlayerLinkPort.CompletionResult.AUTH_PLUGIN_UNAVAILABLE;
            }
        }
        record(completion, result);
        if (player != null && player.isOnline() && !isLoggedIn(completion.playerUuid())) {
            sendPanelMessage(player, i18n("panel_late_completion"));
        }
        return result;
    }

    /** Record {@code result}, log the connection that asked in and return the result. */
    private PlayerLinkPort.CompletionResult grant(PlayerLinkPort.LinkCompletion completion,
                                                  PlayerLinkPort.CompletionResult result, Player player) {
        record(completion, result);
        signIn(player, completion.proof(), result == PlayerLinkPort.CompletionResult.CREATED);
        return result;
    }

    /**
     * Record {@code result}, resume the countdown (nobody is logged in), tell {@code player} {@code message} when
     * both are given, and return the result.
     */
    private PlayerLinkPort.CompletionResult refuse(PlayerLinkPort.LinkCompletion completion,
                                                   PlayerLinkPort.CompletionResult result, Player player,
                                                   String message) {
        record(completion, result);
        syncPanelPause(completion.playerUuid());
        if (player != null && message != null) {
            sendPanelMessage(player, message);
        }
        return result;
    }

    /**
     * Create the account of a player who registered on the web: the row {@code /register} creates, with an empty
     * password hash and salt and the given address. Only called when the player has no account, so it never
     * overwrites one. A failed write is logged and reported as {@code false}.
     */
    private boolean insertWebAccount(UUID playerUuid, String playerName, String ip) {
        try {
            dataOperator.insert(newAccountRow(playerUuid, playerName, "", "", ip));
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().error(i18n("log_web_account_create_failed").replace("{PLAYER}", playerName)
                    .replace("{ERROR}", String.valueOf(e.getMessage())));
            return false;
        }
    }

    private void record(PlayerLinkPort.LinkCompletion completion, PlayerLinkPort.CompletionResult result) {
        answeredCompletions.put(completion.completionId(), result);
    }

    /**
     * End the pending link {@code linkId} if it is pending for {@code playerUuid}, under the lock {@link
     * #invalidateSession(UUID)} takes, without touching the countdown pause.
     *
     * @return whether it was pending for that player
     */
    private boolean takePendingLink(String linkId, UUID playerUuid) {
        AtomicLong generationCell = invalidationGenerations.computeIfAbsent(playerUuid, k -> new AtomicLong());
        synchronized (generationCell) {
            if (!playerUuid.equals(pendingPanelRequests.get(linkId))) {
                return false;
            }
            removePanelRequest(linkId);
            return true;
        }
    }

    /**
     * Log the connection that asked in, as {@code /login} does (account last-login fields, remembered session), and
     * tell it so. A connection already logged in is only told the web sign-in is complete; an in-game-session proof
     * never logs a connection in, it only resumes its countdown.
     * <p>
     * An account without a game password (one the web created) is reminded after the sign-in line, every time, to
     * set a backup password with {@code /setpassword} for when UltiCloud cannot be reached, different from the
     * UltiKits password (maintainer's answer to DECISIONS question 1, option A).
     *
     * @param created whether the account was created by this completion (it has no game password)
     */
    private void signIn(Player player, PlayerLinkPort.LinkProof proof, boolean created) {
        UUID playerUuid = player.getUniqueId();
        if (isLoggedIn(playerUuid)) {
            syncPanelPause(playerUuid);
            sendPanelMessage(player, i18n("panel_web_signin_complete"));
            if (created || hasNoGamePassword(playerUuid)) {
                sendPanelMessage(player, i18n("panel_backup_password_hint"));
            }
            return;
        }
        if (proof == PlayerLinkPort.LinkProof.IN_GAME_SESSION) {
            syncPanelPause(playerUuid);
            return;
        }
        completeLogin(player);
        AccountData account = getAccount(playerUuid);
        boolean noGamePassword = created;
        if (account != null) {
            noGamePassword = isEmptyHash(account.getPasswordHash());
            account.setLastIp(getPlayerIp(player));
            account.setLastLogin(System.currentTimeMillis());
            account.setLoginCount(account.getLoginCount() + 1);
            AccountWrites.update(dataOperator, plugin.getLogger(), account,
                    i18n("log_account_update_after_panel_failed"), i18n("log_account_row_gone"));
        }
        if (config.isSessionEnabled()) {
            sessions.put(getPlayerIp(player) + ":" + playerUuid, System.currentTimeMillis());
        }
        sendPanelMessage(player, i18n("panel_signed_in"));
        if (noGamePassword) {
            sendPanelMessage(player, i18n("panel_backup_password_hint"));
        }
    }

    /**
     * A link stopped being usable without completing (called off the main thread; at most once per link). The link
     * ends on the main thread through the same path as a local expiry: the player is told why (expired, cancelled,
     * refused) only for their latest link, and the countdown resumes with the time that was left. An expiry for a
     * link that is not pending for its player does nothing.
     *
     * @param expiry the link, its player and the reason
     */
    void onLinkExpired(PlayerLinkPort.LinkExpiry expiry) {
        java.util.function.Supplier<String> message;
        switch (expiry.reason()) {
            case CANCELLED:
                message = () -> i18n("panel_link_cancelled");
                break;
            case DENIED:
                message = () -> i18n("panel_outcome_refused");
                break;
            default:
                message = () -> i18n("panel_outcome_expired");
                break;
        }
        try {
            queuePanelTask(expiry.playerUuid(),
                    () -> endPendingLink(expiry.linkId(), expiry.playerUuid(), message));
        } catch (RuntimeException e) {
            // The plugin is disabling: every pending link ends with it.
        }
    }

    /**
     * Check if a player has a pending panel login request.
     */
    public boolean hasPendingPanelRequest(UUID playerUuid) {
        return pendingPanelRequests.containsValue(playerUuid);
    }

    /**
     * Clean up a panel request.
     */
    private void cleanupPanelRequest(String requestId) {
        UUID owner = removePanelRequest(requestId);
        if (owner != null) {
            syncPanelPause(owner);
        }
    }

    /**
     * Forget a panel request without touching the login-timeout pause.
     *
     * @return the player the request belonged to, or {@code null} if it was not pending
     */
    private UUID removePanelRequest(String requestId) {
        pendingPanelDeadlines.remove(requestId);
        panelCreatesInFlight.remove(requestId);
        return pendingPanelRequests.remove(requestId);
    }

    /** Whether any request of the player is pending, in flight or created: each one holds the login countdown. */
    private boolean holdsLoginTimeout(UUID playerUuid) {
        return pendingPanelRequests.containsValue(playerUuid);
    }

    /**
     * Bring the player's login-timeout pause in line with their pending requests.
     * <p>
     * Pauses when a request holds the countdown and the player is still counting down (has not
     * logged in); resumes when none holds any more, moving the join time forward by the paused span
     * so the countdown continues with exactly the time that remained -- never an immediate kick,
     * never a fresh full timeout. Says "resumed" only to a player who was told it paused.
     */
    private void syncPanelPause(UUID playerUuid) {
        boolean resumedAnnounced = false;
        synchronized (panelPauseLock) {
            boolean holds = holdsLoginTimeout(playerUuid);
            Long pausedAt = panelPausedAt.get(playerUuid);
            if (holds && pausedAt == null) {
                if (joinTimes.containsKey(playerUuid)) {
                    panelPausedAt.put(playerUuid, clock.getAsLong());
                }
            } else if (!holds && pausedAt != null) {
                panelPausedAt.remove(playerUuid);
                long pausedFor = clock.getAsLong() - pausedAt;
                joinTimes.computeIfPresent(playerUuid, (uuid, joinedAt) -> joinedAt + pausedFor);
                resumedAnnounced = panelPauseAnnounced.remove(playerUuid);
            }
        }
        if (resumedAnnounced) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                sendPanelMessage(player, i18n("panel_timeout_resumed"));
            }
        }
    }

    /**
     * Tell a paused player, once per pause, that the countdown waits for the web link. {@code PanelCommand} calls it
     * right after delivering a link, so a request that fails never announces a pause.
     *
     * @param player the player who just received a link
     */
    public void announcePanelPause(Player player) {
        boolean announce;
        synchronized (panelPauseLock) {
            UUID uuid = player.getUniqueId();
            announce = panelPausedAt.containsKey(uuid) && panelPauseAnnounced.add(uuid);
        }
        if (announce) {
            sendPanelMessage(player, i18n("panel_timeout_paused"));
        }
    }

    /** End a player's pause without resuming anything: they logged in or left. */
    private void clearPanelPause(UUID playerUuid) {
        synchronized (panelPauseLock) {
            panelPausedAt.remove(playerUuid);
            panelPauseAnnounced.remove(playerUuid);
        }
    }

    private void prunePanelLinkOrigins(long now) {
        panelLinkOrigins.entrySet().removeIf(entry -> now > entry.getValue().retainUntil);
    }

    /**
     * End every pending request whose local deadline has passed (called every second from {@link #checkTimeouts()}).
     * <p>
     * A created link ends at its clamped {@code expiresAt} ({@link #linkDeadline}): the framework sends no expiry of
     * its own for a link whose time simply passed, so this is where the link counts as expired. The player is told
     * ({@code panel_outcome_expired}) only for their latest link; an earlier one ends silently. A request whose create
     * has not answered ends silently at the local create cap -- the worker still waiting on it reports the failure
     * itself and discards a late answer, because the request is no longer pending. Either way the countdown resumes
     * with exactly the time that was left once no request of the player is pending.
     */
    public void cleanupExpiredPanelRequests() {
        long now = clock.getAsLong();
        prunePanelLinkOrigins(now);
        java.util.List<String> expired = new java.util.ArrayList<>();
        for (Map.Entry<String, Long> entry : pendingPanelDeadlines.entrySet()) {
            if (now > entry.getValue()) {
                expired.add(entry.getKey());
            }
        }
        for (String id : expired) {
            endPendingLink(id, null, () -> i18n("panel_outcome_expired"));
        }
    }

    /**
     * End one pending link: tell its player {@code message} if it is their latest link and its create has
     * answered, and resume their countdown once nothing of theirs is pending. Does nothing for a link that is not
     * pending, or that is pending for another player than {@code expectedOwner} (when given).
     */
    private void endPendingLink(String id, UUID expectedOwner, java.util.function.Supplier<String> message) {
        UUID owner = pendingPanelRequests.get(id);
        if (owner == null || (expectedOwner != null && !expectedOwner.equals(owner))) {
            return;
        }
        boolean tell = !panelCreatesInFlight.contains(id) && id.equals(latestPanelRequest.get(owner));
        removePanelRequest(id);
        Player player = Bukkit.getPlayer(owner);
        if (tell && player != null) {
            sendPanelMessage(player, message.get());
        }
        syncPanelPause(owner);
    }

    /**
     * Result of a panel link request.
     */
    public static class PanelLinkResult {
        private final boolean success;
        private final String url;
        private final String error;
        private final String linkId;
        private final Failure failure;

        /**
         * Why a link was not published, so {@code /panel} can tell the player the right thing.
         */
        public enum Failure {
            /** The link was published. */
            NONE,
            /** Timed out, an unexpected answer, or discarded as stale: the generic failure. */
            GENERIC,
            /** The server holds no usable UltiCloud connection. */
            LINK_ERROR,
            /** The gateway rate-limited the create; a later {@code /panel} may succeed. */
            RATE_LIMITED,
            /** The gateway refuses to issue a link for this server or identity. */
            NOT_PERMITTED,
            /**
             * The player has no account and their address has reached {@code max-register-per-ip}; no link was
             * requested.
             */
            IP_LIMIT
        }

        public PanelLinkResult(boolean success, String url, String error) {
            this(success, url, error, null);
        }

        /**
         * @param linkId the link id this result is pending under, so the main-thread re-check
         *               ({@link #isPanelRequestCurrent(UUID, String, long)}) is keyed on this exact link --
         *               never on "whatever is currently pending" for the player, which a review of PR #18
         *               found could pick up an unrelated, newer request. {@code null} for a failed result.
         */
        public PanelLinkResult(boolean success, String url, String error, String linkId) {
            this(success, url, error, linkId, success ? Failure.NONE : Failure.GENERIC);
        }

        /**
         * @param failure why the link was not published ({@link Failure#NONE} for a published link)
         */
        public PanelLinkResult(boolean success, String url, String error, String linkId, Failure failure) {
            this.success = success;
            this.url = url;
            this.error = error;
            this.linkId = linkId;
            this.failure = failure;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getUrl() {
            return url;
        }

        public String getError() {
            return error;
        }

        /** @return the gateway's id of the published link, or {@code null} for a failed result */
        public String getLinkId() {
            return linkId;
        }

        public Failure getFailure() {
            return failure;
        }
    }

    /**
     * Result of login attempt.
     */
    public static class LoginResult {
        private final boolean success;
        private final String message;
        
        public LoginResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
