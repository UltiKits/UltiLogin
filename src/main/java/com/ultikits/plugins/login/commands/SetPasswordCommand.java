package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.*;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /setpassword <new> <confirm>}: sets the first game password of an account that was created on
 * the web through {@code /panel} and therefore has none (Phase 18 magic-link contract, section 13).
 * <p>
 * Accepted only while the player is logged in and the stored password is empty; the new password must
 * satisfy the same policy as {@code /register}. An account that already has a password is pointed at
 * {@code /changepassword}, which asks for the old one. It needs a logged-in player, so it is not in
 * {@code allowed-commands} and needs no permission node: like {@code /register}, every player may run it.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"setpassword", "setpw"},
    description = "command_setpassword_description"
)
public class SetPasswordCommand extends BaseCommandExecutor {

    private final LoginService loginService;

    public SetPasswordCommand(LoginService loginService) {
        this.loginService = loginService;
    }

    @CmdMapping(format = "<newPassword> <confirm>")
    public void setPassword(@CmdSender Player player, @CmdParam("newPassword") String newPassword,
                            @CmdParam("confirm") String confirm) {
        if (!loginService.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + loginService.i18n("please_login_first"));
            return;
        }
        if (!loginService.hasNoGamePassword(player.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', loginService.i18n("setpassword_already_set")));
            return;
        }
        if (!loginService.isPasswordValid(newPassword)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    loginService.getPasswordValidationError(newPassword)));
            return;
        }
        if (!newPassword.equals(confirm)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', loginService.i18n("change_password_mismatch")));
            return;
        }
        if (loginService.setInitialPassword(player.getUniqueId(), newPassword)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', loginService.i18n("setpassword_success")));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', loginService.i18n("setpassword_failed")));
        }
    }

    @CmdMapping(format = "")
    public void help(@CmdSender Player player) {
        handleHelp(player);
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        LoginConfig config = loginService.getConfig();
        sender.sendMessage(ChatColor.YELLOW + loginService.i18n("help_setpassword"));
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
