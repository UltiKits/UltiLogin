package com.ultikits.plugins.login.commands;

import org.bukkit.ChatColor;

/**
 * Help-line colours shared by this module's commands, so sibling commands show the same kind of line the same way.
 */
final class HelpLines {

    private HelpLines() {
    }

    /**
     * One usage line: the command in yellow, and from its {@code " - "} on, the description in white, as
     * {@code /logadmin}'s help always looked and {@code /regs} and {@code /recover} now do too (UltiTools-API 6.3.0
     * Phase 19 sweep). The colour is the module's rather than the language file's, because a language file
     * extracted by an earlier version holds these keys with the same words and no colour codes
     * (UltiKits/UltiLogin#20). A line without {@code " - "} is shown all in yellow.
     *
     * @param line the language file's text for the line
     * @return the coloured line
     */
    static String usage(String line) {
        int split = line.indexOf(" - ");
        return split < 0
                ? ChatColor.YELLOW + line
                : ChatColor.YELLOW + line.substring(0, split) + ChatColor.WHITE + line.substring(split);
    }
}
