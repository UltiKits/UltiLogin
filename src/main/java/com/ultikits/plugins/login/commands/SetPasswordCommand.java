package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.*;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Set-password command (inert seam for the RED commit; implemented in the matching GREEN commit).
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
        // inert until GREEN
    }

    @CmdMapping(format = "")
    public void help(@CmdSender Player player) {
        // inert until GREEN
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        // inert until GREEN
    }
}
