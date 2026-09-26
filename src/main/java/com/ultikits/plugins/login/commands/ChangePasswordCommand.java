package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.*;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Change password command executor.
 *
 * @author wisdomme
 * @version 1.1.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"changepassword", "changepw", "cpw"},
    permission = "ultilogin.changepassword",
    description = "command_changepassword_description"
)
public class ChangePasswordCommand extends BaseCommandExecutor {

    private final LoginService loginService;

    public ChangePasswordCommand(LoginService loginService) {
        this.loginService = loginService;
    }
    
    @CmdMapping(format = "<oldPassword> <newPassword> <confirm>")
    public void changePassword(
        @CmdSender Player player, 
        @CmdParam("oldPassword") String oldPassword,
        @CmdParam("newPassword") String newPassword,
        @CmdParam("confirm") String confirm
    ) {
        LoginConfig config = loginService.getConfig();
        
        // Check if logged in
        if (!loginService.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + loginService.i18n("please_login_first"));
            return;
        }
        
        // Validate new password based on mode
        if (!loginService.isPasswordValid(newPassword)) {
            String error = loginService.getPasswordValidationError(newPassword);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', error));
            return;
        }
        
        // Check confirm
        if (!newPassword.equals(confirm)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', loginService.i18n("change_password_mismatch")));
            return;
        }
        
        // Change password
        if (loginService.changePassword(player.getUniqueId(), oldPassword, newPassword)) {
            player.sendMessage(ChatColor.GREEN + loginService.i18n("change_password_success"));
        } else {
            player.sendMessage(ChatColor.RED + loginService.i18n("change_password_wrong"));
        }
    }
    
    @CmdMapping(format = "")
    public void help(@CmdSender Player player) {
        handleHelp(player);
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        LoginConfig config = loginService.getConfig();
        sender.sendMessage(ChatColor.YELLOW + loginService.i18n("help_change_password"));
        if (config.isGuiModeEnabled()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', loginService.i18n("help_password_digits")
                .replace("{LENGTH}", String.valueOf(config.getGuiPasswordLength()))));
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', loginService.i18n("help_password_length")
                .replace("{MIN}", String.valueOf(config.getMinPasswordLength()))
                .replace("{MAX}", String.valueOf(config.getMaxPasswordLength()))));
        }
    }
}
