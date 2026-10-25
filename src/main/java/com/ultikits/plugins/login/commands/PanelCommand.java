package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.*;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Panel command: asks the framework's UltiCloud player-link API for a one-time link that signs the player in to the
 * web panel, and sends it as a clickable chat line. The link's completion and expiry arrive later as events.
 * 面板命令：通过框架的 UltiCloud 玩家链接接口生成一次性网页面板登录链接。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"panel"},
    description = "command_panel_description"
)
public class PanelCommand extends BaseCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final LoginService loginService;
    private final Plugin bukkitPlugin;

    public PanelCommand(UltiToolsPlugin plugin, LoginService loginService) {
        this.plugin = plugin;
        this.loginService = loginService;
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
    }

    @CmdMapping(format = "")
    public void openPanel(@CmdSender Player player) {
        if (!loginService.isPanelEnabled()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.i18n("panel_not_enabled")));
            return;
        }

        // Send generating message
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            plugin.i18n("panel_generating")));

        // Capture the invalidation generation now, before scheduling the async worker below --
        // not inside it. An admin reset/unregister landing between this line and the worker's
        // call to requestPanelLink() finds nothing to cancel yet (the request has not been
        // published), so requestPanelLink() itself refuses to publish a request whose captured
        // generation no longer matches. See PR #18.
        long invalidationGeneration = loginService.getInvalidationGeneration(player.getUniqueId());

        // Run async: the worker waits for the framework's answer to the create, never on the main thread
        Bukkit.getScheduler().runTaskAsynchronously(bukkitPlugin, () -> {
            LoginService.PanelLinkResult result = loginService.requestPanelLink(player, invalidationGeneration);

            // Send result back on main thread
            Bukkit.getScheduler().runTask(bukkitPlugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                // Revalidate the link one last time, right here, before acting on it. This
                // callback is itself scheduled -- requestPanelLink()'s own re-check after the create
                // only closes the gap up to the moment that method returned; an invalidation landing
                // between that return and this callback actually running on the main thread would
                // otherwise still let a revoked player receive the link. See PR #18.
                boolean current = result.isSuccess()
                        && loginService.isPanelRequestCurrent(player.getUniqueId(),
                                result.getLinkId(), invalidationGeneration);

                if (current) {
                    String url = result.getUrl();
                    // Send clickable link using Bungee chat API
                    TextComponent message = new TextComponent(
                        ChatColor.translateAlternateColorCodes('&',
                            plugin.i18n("panel_link_sent")
                                .replace("{URL}", url)));
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ChatColor.translateAlternateColorCodes('&',
                            plugin.i18n("panel_hover"))).create()));
                    player.spigot().sendMessage(message);

                    // The countdown has been paused since the request existed; say so now that the
                    // player actually holds a link. The outcome arrives as an event, nothing polls.
                    loginService.announcePanelPause(player);
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', failureMessage(result)));
                }
            });
        });
    }

    /**
     * The text for a link that was not delivered: a refused server credential and a failed UltiCloud
     * request each have their own message (Phase 18 magic-link contract, section 13); everything else,
     * including a result discarded as stale, is the generic failure.
     */
    private String failureMessage(LoginService.PanelLinkResult result) {
        if (!result.isSuccess() && result.getFailure() != null) {
            switch (result.getFailure()) {
                case CREDENTIAL_REFUSED:
                    return plugin.i18n("panel_credential_refused");
                case LINK_ERROR:
                    return plugin.i18n("panel_link_error");
                default:
                    break;
            }
        }
        return plugin.i18n("panel_error");
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.i18n("help_panel")));
    }
}
