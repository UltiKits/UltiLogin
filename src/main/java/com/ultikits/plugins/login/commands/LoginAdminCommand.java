package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Admin command executor for login management.
 * Commands:
 * - /logadmin reset <player> [password] - Reset player password
 * - /logadmin forcelogin <player> - Force login a player
 * - /logadmin unregister <player> - Delete player account
 * - /logadmin info <player> - Show player account info
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.BOTH)
@CmdExecutor(
    alias = {"logadmin", "loginadmin"},
    permission = "ultilogin.admin",
    description = "command_logadmin_description"
)
public class LoginAdminCommand extends BaseCommandExecutor {

    private final LoginService loginService;

    public LoginAdminCommand(LoginService loginService) {
        this.loginService = loginService;
    }
    
    /**
     * Reset player password with random password.
     */
    @CmdMapping(format = "reset <player>")
    public void resetPassword(@CmdSender CommandSender sender, @CmdParam("player") String playerName) {
        LoginConfig config = loginService.getConfig();
        
        // Find account
        AccountData account = loginService.getAccountByName(playerName);
        if (account == null) {
            String message = config.getAdminAccountNotFound().replace("{PLAYER}", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        
        // Reset password
        UUID uuid = UUID.fromString(account.getPlayerUuid());
        String newPassword = loginService.resetPassword(uuid);
        
        if (newPassword != null) {
            String message = config.getAdminPasswordReset()
                .replace("{PLAYER}", playerName)
                .replace("{PASSWORD}", newPassword);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            sender.sendMessage(ChatColor.RED + i18n("admin_reset_failed"));
        }
    }
    
    /**
     * Reset player password with specific password.
     */
    @CmdMapping(format = "reset <player> <password>")
    public void resetPasswordWithValue(
        @CmdSender CommandSender sender, 
        @CmdParam("player") String playerName,
        @CmdParam("password") String password
    ) {
        LoginConfig config = loginService.getConfig();
        
        // Find account
        AccountData account = loginService.getAccountByName(playerName);
        if (account == null) {
            String message = config.getAdminAccountNotFound().replace("{PLAYER}", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        
        // Validate password
        if (!loginService.isPasswordValid(password)) {
            String error = loginService.getPasswordValidationError(password);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', error));
            return;
        }
        
        // Reset password
        UUID uuid = UUID.fromString(account.getPlayerUuid());
        if (loginService.resetPassword(uuid, password)) {
            String message = config.getAdminPasswordReset()
                .replace("{PLAYER}", playerName)
                .replace("{PASSWORD}", password);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            sender.sendMessage(ChatColor.RED + i18n("admin_reset_failed"));
        }
    }
    
    /**
     * Force login a player.
     */
    @CmdMapping(format = "forcelogin <player>")
    public void forceLogin(@CmdSender CommandSender sender, @CmdParam("player") String playerName) {
        LoginConfig config = loginService.getConfig();
        
        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            String message = config.getAdminPlayerNotFound().replace("{PLAYER}", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        
        if (!loginService.isRegistered(player.getUniqueId())) {
            String message = config.getAdminAccountNotFound().replace("{PLAYER}", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        
        if (loginService.isLoggedIn(player.getUniqueId())) {
            sender.sendMessage(ChatColor.YELLOW + i18n("admin_player_already_logged").replace("{PLAYER}", playerName));
            return;
        }
        
        if (loginService.forceLogin(player)) {
            String message = config.getAdminForceLogin().replace("{PLAYER}", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            player.sendMessage(ChatColor.GREEN + i18n("admin_force_login_notify"));
        } else {
            sender.sendMessage(ChatColor.RED + i18n("admin_force_login_failed"));
        }
    }
    
    /**
     * Unregister a player.
     */
    @CmdMapping(format = "unregister <player>")
    public void unregister(@CmdSender CommandSender sender, @CmdParam("player") String playerName) {
        LoginConfig config = loginService.getConfig();
        
        // Find account
        AccountData account = loginService.getAccountByName(playerName);
        if (account == null) {
            String message = config.getAdminAccountNotFound().replace("{PLAYER}", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        
        UUID uuid = UUID.fromString(account.getPlayerUuid());
        if (loginService.unregister(uuid)) {
            String message = config.getAdminUnregister().replace("{PLAYER}", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            sender.sendMessage(ChatColor.RED + i18n("admin_unregister_failed"));
        }
    }
    
    /**
     * Show player account info.
     */
    @CmdMapping(format = "info <player>")
    public void showInfo(@CmdSender CommandSender sender, @CmdParam("player") String playerName) {
        LoginConfig config = loginService.getConfig();
        
        // Find account
        AccountData account = loginService.getAccountByName(playerName);
        if (account == null) {
            String message = config.getAdminAccountNotFound().replace("{PLAYER}", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        
        sender.sendMessage(ChatColor.GOLD + i18n("info_header"));
        sender.sendMessage(field(i18n("info_player_name"), account.getPlayerName()));
        sender.sendMessage(field(i18n("info_uuid"), account.getPlayerUuid()));
        sender.sendMessage(field(i18n("info_register_ip"), account.getRegisterIp()));
        sender.sendMessage(field(i18n("info_last_ip"), account.getLastIp()));
        sender.sendMessage(field(i18n("info_login_count"), account.getLoginCount()));
        sender.sendMessage(field(i18n("info_email"),
            account.getEmail() != null ? account.getEmail() : loginService.i18n("info_email_not_bound")));
        sender.sendMessage(field(i18n("info_email_verified"),
            account.isEmailVerified() ? loginService.i18n("info_verified") : loginService.i18n("info_not_verified")));
        
        // Format timestamps
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sender.sendMessage(field(i18n("info_register_time"),
            sdf.format(new java.util.Date(account.getRegisterTime()))));
        sender.sendMessage(field(i18n("info_last_login"),
            sdf.format(new java.util.Date(account.getLastLogin()))));
    }
    
    @CmdMapping(format = "")
    public void help(@CmdSender CommandSender sender) {
        handleHelp(sender);
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + i18n("help_admin_header"));
        sender.sendMessage(usage(i18n("help_admin_reset")));
        sender.sendMessage(usage(i18n("help_admin_forcelogin")));
        sender.sendMessage(usage(i18n("help_admin_unregister")));
        sender.sendMessage(usage(i18n("help_admin_info")));
    }

    /**
     * One usage line: the command in yellow, and from its {@code " - "} on, the description in white,
     * as this help always looked. The colour is the module's rather than the language file's, because a
     * language file extracted by an earlier version holds these keys with the same words and no colour
     * codes (UltiKits/UltiLogin#20). A line without {@code " - "} is shown all in yellow.
     */
    private static String usage(String line) {
        int split = line.indexOf(" - ");
        return split < 0
                ? ChatColor.YELLOW + line
                : ChatColor.YELLOW + line.substring(0, split) + ChatColor.WHITE + line.substring(split);
    }

    /** One line of the info block: {@code label}, then {@code value}. */
    private static String field(String label, Object value) {
        return ChatColor.YELLOW + label + ChatColor.WHITE + value;
    }

    /** This module's language-file text for {@code key}, through the service this command holds. */
    private String i18n(String key) {
        return loginService.i18n(key);
    }
}
