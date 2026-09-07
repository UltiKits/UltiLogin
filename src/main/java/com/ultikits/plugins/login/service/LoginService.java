package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.gui.LoginGUIPage;
import com.ultikits.plugins.login.gui.RegisterGUIPage;
import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ultikits.ultitools.utils.CommonUtils;
import com.ultikits.ultitools.utils.SimpleHttpClient;

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

    // Failed login attempts tracking (IP -> count)
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();

    // Locked IPs (IP -> unlock time)
    private final Map<String, Long> lockedIps = new ConcurrentHashMap<>();

    // Locked UUIDs (UUID -> unlock time)
    private final Map<UUID, Long> lockedUuids = new ConcurrentHashMap<>();

    // Invalidation generation per player -- incremented by invalidateSession(UUID), used to
    // fence panel-link requests captured before an invalidation that landed before the request
    // was actually published. See Codex PR #18 thread 3945030000 (round 4) and
    // requestPanelLink(Player, long)'s javadoc.
    private final Map<UUID, AtomicLong> invalidationGenerations = new ConcurrentHashMap<>();

    // Random generator for password generation
    private final SecureRandom random = new SecureRandom();

    // Pending panel login requests (requestId -> player UUID)
    private final Map<String, UUID> pendingPanelRequests = new ConcurrentHashMap<>();

    // Pending panel request timestamps (requestId -> creation time)
    private final Map<String, Long> pendingPanelTimestamps = new ConcurrentHashMap<>();

    // Active polling tasks per player (playerUUID -> task)
    private final Map<UUID, BukkitTask> pollingTasks = new ConcurrentHashMap<>();

    private final Gson gson = new Gson();

    /**
     * Constructor with dependency injection.
     */
    public LoginService(UltiToolsPlugin plugin, LoginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.dataOperator = plugin.getDataOperator(AccountData.class);
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
    }

    /**
     * Cleanup on shutdown.
     */
    @PreDestroy
    public void shutdown() {
        // Cancel all polling tasks
        for (BukkitTask task : pollingTasks.values()) {
            task.cancel();
        }
        pollingTasks.clear();
        loggedInPlayers.clear();
        joinTimes.clear();
        originalLocations.clear();
        failedAttempts.clear();
        lockedIps.clear();
        lockedUuids.clear();
        pendingPanelRequests.clear();
        pendingPanelTimestamps.clear();
        invalidationGenerations.clear();
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
     * Check if IP/UUID is locked due to failed attempts.
     */
    public boolean isLocked(Player player) {
        String ip = getPlayerIp(player);
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        String lockoutType = config.getLockoutType().toUpperCase();
        
        // Check IP lock
        if ("IP".equals(lockoutType) || "BOTH".equals(lockoutType)) {
            Long ipLockEnd = lockedIps.get(ip);
            if (ipLockEnd != null && now < ipLockEnd) {
                return true;
            } else if (ipLockEnd != null) {
                lockedIps.remove(ip);
                failedAttempts.remove(ip);
            }
        }

        // Check UUID lock
        if ("UUID".equals(lockoutType) || "BOTH".equals(lockoutType)) {
            Long uuidLockEnd = lockedUuids.get(uuid);
            if (uuidLockEnd != null && now < uuidLockEnd) {
                return true;
            } else if (uuidLockEnd != null) {
                lockedUuids.remove(uuid);
            }
        }
        
        return false;
    }
    
    /**
     * Get remaining lockout time in seconds.
     */
    public long getRemainingLockoutTime(Player player) {
        String ip = getPlayerIp(player);
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long remaining = 0;
        
        Long ipEnd = lockedIps.get(ip);
        if (ipEnd != null) {
            remaining = Math.max(remaining, (ipEnd - now) / 1000);
        }
        Long uuidEnd = lockedUuids.get(uuid);
        if (uuidEnd != null) {
            remaining = Math.max(remaining, (uuidEnd - now) / 1000);
        }
        
        return remaining;
    }
    
    /**
     * Record a failed login attempt.
     */
    private void recordFailedAttempt(Player player) {
        if (config.getMaxLoginAttempts() <= 0) {
            return;
        }
        
        String ip = getPlayerIp(player);
        int attempts = failedAttempts.getOrDefault(ip, 0) + 1;
        failedAttempts.put(ip, attempts);
        
        if (attempts >= config.getMaxLoginAttempts()) {
            long unlockTime = System.currentTimeMillis() + (config.getLockoutDuration() * 1000L);
            String lockoutType = config.getLockoutType().toUpperCase();
            
            if ("IP".equals(lockoutType) || "BOTH".equals(lockoutType)) {
                lockedIps.put(ip, unlockTime);
            }
            if ("UUID".equals(lockoutType) || "BOTH".equals(lockoutType)) {
                lockedUuids.put(player.getUniqueId(), unlockTime);
            }
        }
    }
    
    /**
     * Get remaining login attempts for a player.
     */
    public int getRemainingAttempts(Player player) {
        if (config.getMaxLoginAttempts() <= 0) {
            return -1; // Unlimited
        }
        String ip = getPlayerIp(player);
        int attempts = failedAttempts.getOrDefault(ip, 0);
        return config.getMaxLoginAttempts() - attempts;
    }
    
    /**
     * Clear failed attempts for a player (on successful login).
     */
    private void clearFailedAttempts(Player player) {
        String ip = getPlayerIp(player);
        failedAttempts.remove(ip);
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
                player.sendMessage(ChatColor.RED + "该IP已达到最大注册数量！");
                return false;
            }
        }
        
        // Generate salt and hash password
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        
        // Create account
        AccountData account = new AccountData();
        account.setPlayerUuid(player.getUniqueId().toString());
        account.setPlayerName(player.getName());
        account.setPasswordHash(hash);
        account.setSalt(salt);
        account.setRegisterIp(ip);
        account.setLastIp(ip);
        account.setLastLogin(System.currentTimeMillis());
        
        dataOperator.insert(account);
        
        // Auto login after register
        completeLogin(player);
        
        return true;
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
        
        // Verify password
        String hash = hashPassword(password, account.getSalt());
        if (!hashesMatch(hash, account.getPasswordHash())) {
            recordFailedAttempt(player);
            int remaining = getRemainingAttempts(player);
            if (remaining > 0) {
                return new LoginResult(false, config.getAttemptsRemaining()
                    .replace("{COUNT}", String.valueOf(remaining)));
            } else {
                return new LoginResult(false, config.getAccountLocked()
                    .replace("{TIME}", String.valueOf(config.getLockoutDuration())));
            }
        }
        
        // Clear failed attempts on success
        clearFailedAttempts(player);
        
        // Update last login
        String ip = getPlayerIp(player);
        account.setLastIp(ip);
        account.setLastLogin(System.currentTimeMillis());
        account.setLoginCount(account.getLoginCount() + 1);
        account.setFailedAttempts(0);
        try {
            dataOperator.update(account);
        } catch (IllegalAccessException e) {
            plugin.getLogger().error("Failed to update account", e);
        }
        
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
     * Real-machine finding F-L1 (13-uat-results.md #14, Laojun 2026-09-06): a real
     * {@code /changepassword} called only this method and reported success while the player
     * stayed authenticated -- {@code LoginProtectionListener} authorizes in-game actions through
     * {@link #isLoggedIn(UUID)}, not {@link #hasValidSession(Player)}, and only the unregister and
     * resetPassword paths separately called {@code forceReauthenticationIfOnline}. This is
     * now the single entry point every credential-changing path must be sufficient by calling
     * alone: it forces re-authentication itself, so no caller can forget the step.
     * <p>
     * Also advances this player's invalidation generation ({@link #getInvalidationGeneration
     * (UUID)}), first -- before {@link #cancelPendingPanelRequest(UUID)}, which can only cancel
     * a panel request that has already been inserted into {@code pendingPanelRequests}. Codex PR
     * #18 thread 3945030000 (round 4): a {@code /panel} request captured before this call, but
     * not yet published by {@link #requestPanelLink(Player, long)} at the time this call runs,
     * has nothing for the cancellation to find. The generation bump is what {@code
     * requestPanelLink} checks to refuse publishing that now-stale request when its worker
     * finally runs.
     * <p>
     * Round 5 (13-REVIEW-UltiLogin.md, own deep review of bcadfb5): the generation bump and the
     * cancellation below run inside a {@code synchronized} block keyed on this player's own
     * {@link AtomicLong} generation cell -- the same cell {@link #requestPanelLink(Player, long)}
     * synchronizes on around its own check-then-insert. That is what closes the narrow window a
     * plain read-then-write left open: without a shared lock, an invalidation landing between
     * {@code requestPanelLink}'s generation read and its {@code pendingPanelRequests} insert
     * would find nothing to cancel (nothing had been inserted yet) while the generation had
     * already been read as matching, so the stale request would still get published. With both
     * sides synchronized on the same per-player cell, either this method's bump-and-cancel runs
     * completely before {@code requestPanelLink}'s check-and-insert (so the check then sees the
     * bumped generation and refuses), or it runs completely after (so the cancellation finds the
     * entry {@code requestPanelLink} just inserted and removes it) -- there is no interleaving
     * that lets a stale request survive either path.
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
     * Round 7 (Codex PR #18 thread 3946170649): {@link #resetPasswordForRecovery(UUID, String)}
     * -- the only caller that passes {@code false} -- is followed immediately by the caller
     * ({@code RecoverCommand}) calling {@link #completeLogin(Player)} itself. Presenting the
     * prompt in between would show "log in now" immediately followed by "you are logged in" in
     * text mode, or leave a credential GUI open that {@code completeLogin} never used to close in
     * GUI mode (see the fix there). Every other caller of {@link #invalidateSession(UUID)}
     * (unregister, both admin {@code resetPassword} overloads, {@code changePassword}) keeps
     * going through the single-argument overload above, so it keeps presenting the prompt exactly
     * as before -- this overload only changes behaviour for the one path that opts in.
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
     * /panel}'s worker, which makes a blocking HTTP call before {@link #requestPanelLink(Player,
     * long)} actually publishes anything) should capture this value up front, before scheduling,
     * and pass it back in so the eventual publish can refuse itself if an invalidation landed in
     * the gap. See Codex PR #18 thread 3945030000 (round 4).
     *
     * @param playerUuid the player to check
     * @return the current invalidation generation, {@code 0} if never invalidated
     */
    public long getInvalidationGeneration(UUID playerUuid) {
        AtomicLong generation = invalidationGenerations.get(playerUuid);
        return generation == null ? 0L : generation.get();
    }

    /**
     * Cancel any in-flight UltiCloud panel magic-link request (and its polling task) for a
     * player. This is a single entry point reached via invalidateSession, so every
     * credential-changing path (unregister, both resetPassword overloads, changePassword)
     * cancels a pending /panel request the same way -- rather than a deleted or credential-reset
     * account being logged back in when the worker later confirms an authentication that was
     * requested before the account changed. See 13-REVIEW-UltiLogin.md CR-01.
     *
     * @param playerUuid the player whose pending panel request should be cancelled
     */
    private void cancelPendingPanelRequest(UUID playerUuid) {
        pendingPanelRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(playerUuid)) {
                pendingPanelTimestamps.remove(entry.getKey());
                return true;
            }
            return false;
        });
        BukkitTask task = pollingTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Revoke a currently online player's active login state and let {@link #checkTimeouts()}
     * enforce the configured login timeout on them again, as if they had just joined.
     * <p>
     * Reported by Codex on PR #18: {@link #invalidateSession(UUID)} used to only end the
     * remembered {@code sessions} entry, but {@code LoginProtectionListener} authorizes in-game
     * actions through {@link #isLoggedIn(UUID)}, not {@link #hasValidSession(Player)} -- so an
     * already-authenticated online connection stayed fully authorized with the old credentials
     * until it happened to disconnect, defeating an administrative password reset issued in
     * response to a compromised, currently-connected account. Deliberately does not replay
     * {@link #onPlayerJoin(Player)}: that replay is a separate, already-fixed defect (13-06/D-08)
     * because it re-runs the session auto-login check.
     * <p>
     * As of the F-L1 fix, {@link #invalidateSession(UUID)} calls this itself, so this method no
     * longer needs a separate call from every credential-changing path -- kept {@code private}
     * since {@code invalidateSession} is the only remaining caller and the sole intended entry
     * point.
     * <p>
     * Codex PR #18 thread 3945030004 (round 4): flipping the flag alone left a revoked online
     * player with no way back to the credential screen -- {@code LoginProtectionListener} only
     * ever opens the login/register GUI from {@code PlayerJoinEvent} or a blocked action,
     * neither of which fires again on its own after this silent flag flip, so the player sat
     * frozen by the action guards until {@link #checkTimeouts()} eventually kicked them. Now
     * also calls {@link #presentCredentialPrompt(Player)} to show the same prompt immediately.
     * <p>
     * Round 7 (Codex PR #18 thread 3946170649): {@code presentPrompt} lets {@link
     * #invalidateSession(UUID, boolean)} suppress the prompt for the successful-recovery path,
     * which re-authenticates the player itself immediately afterward -- see that overload's
     * javadoc.
     *
     * @param playerUuid the player to force back into the unauthenticated state, if online
     * @param presentPrompt whether to show the credential prompt; {@code false} only for
     *                      successful recovery
     */
    private void forceReauthenticationIfOnline(UUID playerUuid, boolean presentPrompt) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            loggedInPlayers.put(playerUuid, false);
            joinTimes.put(playerUuid, System.currentTimeMillis());
            if (presentPrompt) {
                presentCredentialPrompt(player);
            }
        }
    }

    /**
     * Present the login or register credential prompt -- the GUI page or a plain chat message,
     * chosen by {@link LoginConfig#isGuiModeEnabled()} and the player's current registration
     * state -- exactly as {@link LoginProtectionListener} already does when re-prompting a
     * player after a blocked action.
     * <p>
     * Delegates to {@link LoginProtectionListener#presentCredentialPrompt(Player,
     * UltiToolsPlugin, LoginService, Plugin)} so {@code forceReauthenticationIfOnline}
     * can show the same prompt after an administrative credential change (Codex PR #18 thread
     * 3945030004, round 4) without duplicating the GUI-vs-text branching a second time.
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
     * Handle player join.
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        loggedInPlayers.put(uuid, false);
        joinTimes.put(uuid, System.currentTimeMillis());
        
        // Store original location
        originalLocations.put(uuid, player.getLocation().clone());
        
        // Check session
        if (hasValidSession(player)) {
            completeLogin(player);
            player.sendMessage(ChatColor.GREEN + "会话有效，自动登录成功！");
            return;
        }
        
        // Apply blind effect
        if (config.isBlindEffect()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }
        
        // Teleport to spawn if enabled
        if (config.isSpawnLocationEnabled()) {
            World world = Bukkit.getWorld(config.getSpawnWorld());
            if (world != null) {
                Location spawn = new Location(world, config.getSpawnX(), config.getSpawnY(), config.getSpawnZ());
                player.teleport(spawn);
            }
        }
        
        // Send prompt based on mode
        if (isRegistered(uuid)) {
            String message = config.isGuiModeEnabled() 
                ? config.getLoginPromptGui() 
                : config.getLoginPrompt();
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
        BukkitTask pollingTask = pollingTasks.remove(uuid);
        if (pollingTask != null) {
            pollingTask.cancel();
        }
    }
    
    /**
     * Complete login process.
     * <p>
     * Round 7 (Codex PR #18 thread 3946170649): closes an already-open credential GUI, if the
     * player is looking at one. Before this, a caller that authenticates a player directly --
     * most notably the successful email-recovery path ({@code RecoverCommand.resetPassword} ->
     * {@code EmailVerificationService.resetPasswordAfterRecovery} -> here) without going through
     * that GUI's own {@code attemptLogin()} click handler -- left a {@link LoginGUIPage} or {@link
     * RegisterGUIPage} open on screen even though {@code loggedInPlayers} was already flipped to
     * {@code true}: nothing else ever closes it for that caller. The flag is flipped above,
     * before this check runs, so if {@code closeInventory()} below triggers {@link
     * LoginGUIPage#onClose}'s own re-open-if-not-logged-in guard, it already sees the player as
     * logged in and does not reopen the GUI it is closing.
     *
     * @param player the player to complete login for
     */
    public void completeLogin(Player player) {
        UUID uuid = player.getUniqueId();
        loggedInPlayers.put(uuid, true);
        joinTimes.remove(uuid);

        closeCredentialGuiIfOpen(player);

        // Remove blind effect
        player.removePotionEffect(PotionEffectType.BLINDNESS);

        // Restore original location if we teleported to spawn
        if (config.isSpawnLocationEnabled()) {
            Location original = originalLocations.get(uuid);
            if (original != null) {
                player.teleport(original);
            }
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
        try {
            dataOperator.update(account);
        } catch (IllegalAccessException e) {
            plugin.getLogger().error("Failed to reset password", e);
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
     * Round 7 (Codex PR #18 thread 3946170649): the email-recovery flow used to delegate here
     * too, but this overload's {@link #invalidateSession(UUID)} call presents the credential
     * prompt -- correct for an admin resetting someone else's password, wrong for a player who
     * just recovered their own and is about to be logged straight back in by the caller. Recovery
     * now goes through {@link #resetPasswordForRecovery(UUID, String)} instead, which shares this
     * method's account-update logic but suppresses the prompt.
     */
    public boolean resetPassword(UUID playerUuid, String newPassword) {
        return resetPasswordInternal(playerUuid, newPassword, true);
    }

    /**
     * Reset player password for the successful email-recovery path specifically: same
     * account-update and invalidation as {@link #resetPassword(UUID, String)}, but without
     * presenting the credential prompt.
     * <p>
     * Round 7 (Codex PR #18 thread 3946170649): {@code EmailVerificationService
     * .resetPasswordAfterRecovery} is called from {@code RecoverCommand.resetPassword}, which
     * calls {@link #completeLogin(Player)} immediately afterward on success. Going through the
     * public, prompt-presenting {@link #resetPassword(UUID, String)} used to show the login/
     * register prompt (text message, or in GUI mode a freshly reopened {@code LoginGUIPage}) an
     * instant before {@code completeLogin} ran -- in text mode a jarring "log in now" immediately
     * followed by "you are logged in"; in GUI mode a credential GUI left open that {@code
     * completeLogin} never used to close (see the fix there). Package-private: {@code
     * EmailVerificationService} (same package) is the only intended caller -- every other
     * credential-changing path (unregister, admin resetPassword, changePassword) keeps presenting
     * the prompt through the public overload above, since none of those callers re-authenticates
     * the player themselves afterward.
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
        try {
            dataOperator.update(account);
        } catch (IllegalAccessException e) {
            plugin.getLogger().error("Failed to reset password", e);
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
        // state and reinitialize their login timeout (Codex PR #18 comment 3944181260):
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
        long now = System.currentTimeMillis();
        long timeout = config.getLoginTimeout() * 1000L;

        for (Map.Entry<UUID, Long> entry : joinTimes.entrySet()) {
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
        try {
            dataOperator.update(account);
        } catch (IllegalAccessException e) {
            plugin.getLogger().error("Failed to update account", e);
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
     * Compare two Base64-encoded password hashes in constant time. WR-03
     * (13-REVIEW-UltiLogin.md): {@code String.equals} short-circuits on the first mismatched
     * character, which is a textbook timing side-channel for a stored-hash comparison. Decodes
     * both to bytes and defers to {@link MessageDigest#isEqual(byte[], byte[])}, which always
     * compares the full length of the shorter input regardless of where the first difference is.
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
    
    // ==================== Panel Magic Link Methods ====================

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
     * Request a panel magic link for a player, without fencing against an invalidation racing
     * the request itself.
     * <p>
     * Equivalent to {@link #requestPanelLink(Player, long)} called with the player's
     * <em>current</em> invalidation generation, captured at the start of this call -- which
     * means it can never observe itself as stale. Suitable only for a caller on the same,
     * uninterrupted call stack as the eventual publish (e.g. tests). {@code /panel}'s own
     * worker uses the two-argument overload instead, capturing the generation before it
     * schedules any asynchronous work. See {@link #requestPanelLink(Player, long)}.
     * <p>
     * Round 5 (13-REVIEW-UltiLogin.md, own deep review of bcadfb5, Info finding): package-private
     * rather than {@code public} -- its safety depends entirely on the caller staying on the
     * same, uninterrupted call stack as the eventual publish, and nothing in the signature
     * enforces that. A future caller wrapping this overload in its own asynchronous scheduling
     * (as {@code PanelCommand} used to do before this fence existed) would silently reintroduce
     * the very race {@link #requestPanelLink(Player, long)} exists to close, because the
     * generation would then be captured inside the async worker instead of before it was
     * scheduled. No caller outside this class needs it -- only this package's tests do.
     *
     * @param player the player requesting the link
     * @return PanelLinkResult with the URL or error message
     */
    PanelLinkResult requestPanelLink(Player player) {
        return requestPanelLink(player, getInvalidationGeneration(player.getUniqueId()));
    }

    /**
     * Request a panel magic link for a player, refusing to publish it if the invalidation
     * generation the caller captured before starting no longer matches the player's current
     * one.
     * <p>
     * Codex PR #18 thread 3945030000 (round 4), see {@link #invalidateSession(UUID)}: {@code
     * /panel} (see {@code PanelCommand}) captures {@link #getInvalidationGeneration(UUID)} before
     * scheduling its asynchronous worker; this method runs later, on that worker thread, after a
     * blocking HTTP call. An administrator's reset/unregister landing in the gap between those
     * two points calls {@link #invalidateSession(UUID)}, which bumps the generation and calls
     * {@link #cancelPendingPanelRequest(UUID)} -- but that cancellation finds nothing yet, since
     * this request has not been inserted into {@code pendingPanelRequests} at that point.
     * Without this check, the worker would go on to publish the (already-stale) request, send
     * the link, and start polling; since a password reset or change does not remove the
     * account, {@link #completePanelLogin(String, boolean)}'s registration check would later
     * pass and re-authenticate the revoked connection. Checking the generation immediately
     * before publishing closes that gap; once a request has been inserted, any subsequent
     * invalidation is already covered by {@link #cancelPendingPanelRequest(UUID)} and, for a
     * poll already in flight past that point, by {@link #handlePanelPollCompleted(Player, String,
     * boolean)}'s request-id-keyed lookup (round 2, comment 3944418953; keyed by request id as of
     * round 7) -- so this is the one remaining fence, not a duplicate of either.
     * <p>
     * Round 5 (13-REVIEW-UltiLogin.md, own deep review of bcadfb5): the generation check and the
     * {@code pendingPanelRequests} insert below now run inside a {@code synchronized} block keyed
     * on the same per-player {@link AtomicLong} generation cell {@link #invalidateSession(UUID)}
     * synchronizes on. This closes the narrow window a plain read-then-insert left open: the
     * check and the insert are now one atomic step from {@code invalidateSession}'s point of
     * view, so an invalidation landing "in between" is impossible -- it either happens
     * completely before this block (and the check sees it) or completely after (and {@code
     * invalidateSession}'s own cancellation finds the entry this block just inserted). Only the
     * publish decision and the map insert are inside the lock; the blocking HTTP call and
     * everything after it run outside it.
     * <p>
     * Round 7 (Codex PR #18 thread 3946170644): an invalidation racing an in-flight publish was
     * still able to slip through, because nothing re-checked the generation or the pending-request
     * entry after this method's own blocking HTTP call returned -- the method would hand back a
     * success result (URL included) even though {@link #cancelPendingPanelRequest(UUID)} had
     * already removed the entry and the generation had already moved on. This method now performs
     * that re-check, under the same lock, immediately before returning a success result (see
     * below); a stale result is discarded there rather than being returned. The returned {@link
     * PanelLinkResult} also now carries the {@code requestId} it was published under, so a caller
     * that goes on to poll for completion (see {@link #startAuthPolling(String, Player, String)})
     * keys its poll on the exact request this call published -- never on "whatever is currently
     * pending" for the player, which round 7 also found could pick up an unrelated, newer request.
     *
     * @param player the player requesting the link
     * @param expectedGeneration the invalidation generation the caller captured before issuing
     *                           this request, from {@link #getInvalidationGeneration(UUID)}
     * @return PanelLinkResult with the URL, request id, or error message
     */
    public PanelLinkResult requestPanelLink(Player player, long expectedGeneration) {
        if (!isPanelEnabled()) {
            return new PanelLinkResult(false, null, "Panel integration not enabled");
        }

        UUID playerUuidRaw = player.getUniqueId();
        String requestId = UUID.randomUUID().toString();

        AtomicLong generationCell = invalidationGenerations.computeIfAbsent(playerUuidRaw, k -> new AtomicLong());
        synchronized (generationCell) {
            if (generationCell.get() != expectedGeneration) {
                return new PanelLinkResult(false, null,
                        "Session was invalidated before the request completed");
            }
            // Track the pending request -- this insert and the generation check above must stay
            // atomic with each other (see javadoc above); nothing else belongs inside this lock.
            pendingPanelRequests.put(requestId, playerUuidRaw);
            pendingPanelTimestamps.put(requestId, System.currentTimeMillis());
        }

        String code = generateVerificationCode();
        String playerUuid = playerUuidRaw.toString();
        String playerName = player.getName();

        // Build API request
        String apiUrl;
        try {
            apiUrl = UltiTools.getEnv().getString("api-url");
        } catch (Exception e) {
            cleanupPanelRequest(requestId);
            return new PanelLinkResult(false, null, "API URL not configured");
        }

        String serverUuid;
        try {
            serverUuid = CommonUtils.getUltiToolsUUID();
        } catch (Exception e) {
            cleanupPanelRequest(requestId);
            return new PanelLinkResult(false, null, "Server UUID not available");
        }

        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("code", code);
        body.addProperty("playerUuid", playerUuid);
        body.addProperty("playerName", playerName);
        body.addProperty("serverUuid", serverUuid);

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            SimpleHttpClient.Response response = SimpleHttpClient.post(
                apiUrl + "/auth/magic-link",
                headers,
                gson.toJson(body)
            );

            if (response.isOk()) {
                JsonObject responseBody = JsonParser.parseString(response.getBody()).getAsJsonObject();
                // API returns wrapped response: {"code":"200","data":{"url":"..."}}
                String url = null;
                if (responseBody.has("data") && responseBody.get("data").isJsonObject()) {
                    JsonObject data = responseBody.getAsJsonObject("data");
                    url = data.has("url") ? data.get("url").getAsString() : null;
                } else if (responseBody.has("url")) {
                    url = responseBody.get("url").getAsString();
                }
                if (url != null) {
                    // Round 7 (Codex PR #18 thread 3946170644): the generation check and the
                    // pendingPanelRequests insert above only fence the gap *before* this blocking
                    // POST. An invalidateSession(...) landing anywhere *during* the POST (which
                    // can run for an arbitrary, unbounded amount of time) removes this request's
                    // pendingPanelRequests entry and bumps the generation, but neither of those
                    // stops this in-flight HTTP call from finishing and this method from handing
                    // back a success result regardless -- the caller (PanelCommand) would then
                    // send the now-stale magic link and start a poll for it. Re-checking both,
                    // under the same per-player lock invalidateSession synchronizes on, closes
                    // that: either this re-check runs completely before invalidateSession's own
                    // bump-and-cancel (so it still sees the matching generation and the request
                    // still pending, and the result is safe to publish), or completely after (so
                    // it observes the bumped generation or the already-removed entry, and
                    // discards the result instead).
                    synchronized (generationCell) {
                        if (generationCell.get() != expectedGeneration
                                || !pendingPanelRequests.containsKey(requestId)) {
                            cleanupPanelRequest(requestId);
                            return new PanelLinkResult(false, null,
                                    "Session was invalidated before the request completed");
                        }
                    }
                    return new PanelLinkResult(true, url, null, requestId);
                }
                cleanupPanelRequest(requestId);
                return new PanelLinkResult(false, null, "Invalid response from API");
            } else {
                cleanupPanelRequest(requestId);
                return new PanelLinkResult(false, null, "API returned status " + response.getStatus());
            }
        } catch (Exception e) {
            cleanupPanelRequest(requestId);
            plugin.getLogger().warn("Failed to request panel link: " + e.getMessage());
            return new PanelLinkResult(false, null, "Request failed: " + e.getMessage());
        }
    }

    /**
     * Start polling the Worker API for auth completion.
     * Polls every 3 seconds for up to 5 minutes, then auto-cancels.
     * <p>
     * Round 7 (Codex PR #18 thread 3946170644): {@code requestId} is the exact pending request
     * this poll exists to confirm -- captured by the caller from {@link PanelLinkResult#getUrl()}
     * '}s sibling {@link PanelLinkResult#getRequestId()} at the moment the link was published, not
     * re-derived here. {@link #handlePanelPollCompleted(Player, String, boolean)} passes it
     * straight through to {@link #completePanelLogin(String, boolean)}, which only ever resolves
     * this exact id. Before this round, completion was resolved by scanning {@code
     * pendingPanelRequests} for "whatever is currently pending" for this player -- if this poll's
     * own request had already been cancelled (e.g. by an invalidation) but a newer, unrelated
     * request happened to be pending for the same player by the time this poll's HTTP call
     * returned "completed", that scan would pick up the newer request and authenticate through
     * it instead of failing closed.
     *
     * @param playerUuid the player's UUID string
     * @param player the online player
     * @param requestId the pending request id this poll is confirming, from {@link
     *                  PanelLinkResult#getRequestId()}
     */
    public void startAuthPolling(String playerUuid, Player player, String requestId) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing polling task for this player
        BukkitTask existing = pollingTasks.remove(uuid);
        if (existing != null) {
            existing.cancel();
        }

        String apiUrl;
        try {
            apiUrl = UltiTools.getEnv().getString("api-url");
        } catch (Exception e) {
            plugin.getLogger().warn("Cannot start auth polling: API URL not configured");
            return;
        }

        String pollUrl = apiUrl + "/auth/magic-link/poll?playerUuid=" + playerUuid;
        final int maxPolls = 100; // 100 * 3s = 5 minutes
        final int[] pollCount = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(bukkitPlugin, () -> {
            pollCount[0]++;

            if (!player.isOnline() || pollCount[0] > maxPolls) {
                BukkitTask self = pollingTasks.remove(uuid);
                if (self != null) {
                    self.cancel();
                }
                return;
            }

            try {
                SimpleHttpClient.Response response = SimpleHttpClient.get(pollUrl);
                if (!response.isOk()) {
                    return;
                }

                JsonObject body = JsonParser.parseString(response.getBody()).getAsJsonObject();
                JsonObject data = body.has("data") && body.get("data").isJsonObject()
                    ? body.getAsJsonObject("data") : null;
                if (data == null) {
                    return;
                }

                String status = data.has("status") ? data.get("status").getAsString() : null;
                if (!"completed".equals(status)) {
                    return;
                }

                // Auth completed — cancel polling
                BukkitTask self = pollingTasks.remove(uuid);
                if (self != null) {
                    self.cancel();
                }

                boolean isServerOwner = data.has("is_server_owner")
                    && !data.get("is_server_owner").isJsonNull()
                    && data.get("is_server_owner").getAsBoolean();
                final boolean finalIsServerOwner = isServerOwner;

                // Complete login on main thread, resolving by the exact request id this poll was
                // started for -- see handlePanelPollCompleted's javadoc for why.
                Bukkit.getScheduler().runTask(bukkitPlugin,
                        () -> handlePanelPollCompleted(player, requestId, finalIsServerOwner));
            } catch (Exception e) {
                plugin.getLogger().debug("Auth poll error: " + e.getMessage());
            }
        }, 60L, 60L); // 60 ticks = 3 seconds

        pollingTasks.put(uuid, task);
    }

    /**
     * Handle a poll's own observation that the panel magic-link request is "completed", on the
     * main thread.
     * <p>
     * Resolves strictly by the exact {@code requestId} this poll was started for -- see {@link
     * #startAuthPolling(String, Player, String)} -- and delegates to {@link
     * #completePanelLogin(String, boolean)}, which independently re-validates that this specific
     * id is still pending (and still belongs to a registered account) before granting anything.
     * Every path that removes a pending request ({@link #cancelPendingPanelRequest(UUID)},
     * reached via {@link #invalidateSession(UUID)} from account deletion, both password-reset
     * overloads, and password change; or {@link #completePanelLogin(String, boolean)}'s own
     * cleanup) runs on this same main thread, so whichever removal happened first is guaranteed
     * to be visible here.
     * <p>
     * Fixes a P1 Codex reported on PR #18 (review comment 3944418953, against the CR-01 fix
     * commits): the original implementation looked up the request ID once, on the same call
     * stack as the async HTTP fetch, and treated "not found" as authorization to call {@link
     * #completeLogin(Player)} directly -- bypassing {@link #completePanelLogin(String, boolean)}
     * 's registration check entirely. {@link BukkitTask#cancel()} only prevents a scheduled
     * task's future executions; it does not interrupt an invocation already inside its HTTP
     * call. A poll that started before an admin reset or unregister could therefore still
     * observe "completed" and re-authenticate an online player after their credentials were
     * revoked or their account deleted -- undoing {@code forceReauthenticationIfOnline}
     * in the same stroke.
     * <p>
     * Round 2's fix (comment 3944418953) replaced that with a fresh lookup at this main-thread
     * completion point, scanning {@code pendingPanelRequests} for whichever entry was pending for
     * this player -- an improvement, but still not keyed on this specific poll's own request.
     * Round 7 (Codex PR #18 thread 3946170644) found that scan could pick up an unrelated,
     * <em>newer</em> request for the same player: if this poll's original request had already
     * been cancelled but the player had since started a fresh {@code /panel} request, the stale
     * "completed" observation would authenticate through the newer request instead of failing.
     * Passing {@code requestId} straight through from {@link #startAuthPolling(String, Player,
     * String)} removes the scan entirely -- absence of exactly this id in {@code
     * pendingPanelRequests} (checked inside {@link #completePanelLogin(String, boolean)}) means
     * either this login was already completed by another path, or this request was cancelled
     * because the account changed; either way, nothing else pending for this player is eligible
     * to be picked up instead.
     *
     * @param player the online player the poll was running for
     * @param requestId the exact pending request id this poll was started for
     * @param isServerOwner whether the confirmed panel session reported server-owner status
     */
    private void handlePanelPollCompleted(Player player, String requestId, boolean isServerOwner) {
        if (!player.isOnline()) {
            return;
        }
        completePanelLogin(requestId, isServerOwner);
    }

    /**
     * Handle a panel login completion (called when API Worker confirms auth via WebSocket).
     *
     * @param requestId the request ID
     * @return true if the player was successfully logged in
     */
    public boolean completePanelLogin(String requestId) {
        return completePanelLogin(requestId, false);
    }

    /**
     * Handle a panel login completion with role information.
     *
     * @param requestId the request ID
     * @param isServerOwner whether the user is a server owner
     * @return true if the player was successfully logged in
     */
    public boolean completePanelLogin(String requestId, boolean isServerOwner) {
        UUID playerUuid = pendingPanelRequests.get(requestId);
        if (playerUuid == null) {
            return false;
        }

        cleanupPanelRequest(requestId);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return false;
        }

        // Second, independent layer of defense against CR-01: the account may have been
        // unregistered after this request was created but before invalidateSession's
        // cancellation reached it (or, in the future, through some other path that never calls
        // invalidateSession). Refuse to grant the login rather than trust that the account still
        // exists just because a pending request for it does.
        if (!isRegistered(playerUuid)) {
            return false;
        }

        // Complete the login
        completeLogin(player);

        // Send role-specific message
        String messageKey = isServerOwner ? "panel_auth_success_owner" : "panel_auth_success_player";
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            plugin.i18n(messageKey)));

        // Update account last login
        AccountData account = getAccount(playerUuid);
        if (account != null) {
            account.setLastIp(getPlayerIp(player));
            account.setLastLogin(System.currentTimeMillis());
            account.setLoginCount(account.getLoginCount() + 1);
            try {
                dataOperator.update(account);
            } catch (IllegalAccessException e) {
                plugin.getLogger().error("Failed to update account after panel login", e);
            }
        }

        // Create session
        if (config.isSessionEnabled()) {
            String ip = getPlayerIp(player);
            sessions.put(ip + ":" + playerUuid, System.currentTimeMillis());
        }

        return true;
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
        pendingPanelRequests.remove(requestId);
        pendingPanelTimestamps.remove(requestId);
    }

    /**
     * Clean up expired panel requests (older than 5 minutes).
     */
    public void cleanupExpiredPanelRequests() {
        long now = System.currentTimeMillis();
        long expiry = 5 * 60 * 1000L; // 5 minutes
        pendingPanelTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > expiry) {
                pendingPanelRequests.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Result of a panel link request.
     */
    public static class PanelLinkResult {
        private final boolean success;
        private final String url;
        private final String error;
        private final String requestId;

        public PanelLinkResult(boolean success, String url, String error) {
            this(success, url, error, null);
        }

        /**
         * @param requestId the pending-request id this result was published under, so a caller
         *                  that goes on to poll for completion (see {@link #startAuthPolling
         *                  (String, Player, String)}) keys its poll on the exact request this
         *                  result corresponds to -- never on "whatever is currently pending" for
         *                  the player. Round 7 (Codex PR #18 thread 3946170644) found the latter
         *                  could pick up an unrelated, newer request. {@code null} for a failed
         *                  result, since there is nothing to poll for.
         */
        public PanelLinkResult(boolean success, String url, String error, String requestId) {
            this.success = success;
            this.url = url;
            this.error = error;
            this.requestId = requestId;
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

        public String getRequestId() {
            return requestId;
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
