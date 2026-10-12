package com.ultikits.plugins.login.logging;

import com.ultikits.plugins.login.commands.ChangePasswordCommand;
import com.ultikits.plugins.login.commands.EmailBindCommand;
import com.ultikits.plugins.login.commands.LoginAdminCommand;
import com.ultikits.plugins.login.commands.LoginCommand;
import com.ultikits.plugins.login.commands.PanelCommand;
import com.ultikits.plugins.login.commands.RecoverCommand;
import com.ultikits.plugins.login.commands.RegisterCommand;
import com.ultikits.plugins.login.commands.SetPasswordCommand;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which player command lines carry a password, and masks their arguments.
 * <p>
 * Paper logs every player command as {@code <player> issued server command: /<label> <args>}, so
 * without a mask {@code /login <password>} reaches the console, {@code logs/latest.log} and, through
 * UltiTools-API 6.3.0's console mirror, the UltiPanel log stream. This class holds no Log4j types,
 * so it is usable (and testable) whether or not Log4j Core is present; {@link Log4jPasswordFilter}
 * applies it to the server's log events.
 * <p>
 * <b>Derived from the command declarations, not a copy of them.</b> The labels and sub-commands come
 * from the {@link CmdExecutor#alias()} and {@link CmdMapping#format()} of {@link #MODULE_COMMANDS}: a
 * mapping is password-bearing when its format names a parameter in {@link #SECRET_PARAMETERS}, and
 * the literal words in front of that format's first parameter (none for {@code /login <password>},
 * {@code reset} for {@code /logadmin reset <player> <password>}) stay readable, while everything
 * after them is replaced by {@link #MASK}. A new alias is therefore covered without touching this
 * class; a new command class or parameter name is caught by {@code PasswordCommandMaskTest}, which
 * fails until it is classified.
 * <p>
 * Matching is by the label's last segment, case-insensitively, so {@code /LOGIN}, {@code
 * /ultitools:login} (the framework registers module commands under its own name) and any other
 * namespace prefix are masked the same way. Another plugin's command
 * that happens to share one of these labels is masked as well; for a log, hiding too much is the safe
 * direction.
 */
public final class PasswordCommandMask {

    /** What replaces the arguments: always the same text, so neither their length nor their count shows. */
    public static final String MASK = "******";

    /** The text Paper puts between the player's name and the command line. */
    public static final String ISSUED_COMMAND_MARKER = " issued server command: ";

    /**
     * Every {@code @CmdExecutor} class of this module. {@code PasswordCommandMaskTest} compares it with
     * the classes actually compiled into the module, so a command class added without being listed here
     * fails the build.
     */
    public static final List<Class<?>> MODULE_COMMANDS = Collections.unmodifiableList(Arrays.<Class<?>>asList(
            ChangePasswordCommand.class,
            EmailBindCommand.class,
            LoginAdminCommand.class,
            LoginCommand.class,
            PanelCommand.class,
            RecoverCommand.class,
            RegisterCommand.class,
            SetPasswordCommand.class));

    /**
     * {@code @CmdMapping} parameter names whose value is a secret, compared case-insensitively.
     * {@code PasswordCommandMaskTest} requires every parameter name used by a module command to be
     * either in this set or in its own explicit list of names that are not secret.
     */
    public static final Set<String> SECRET_PARAMETERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "password", "confirm", "oldpassword", "newpassword", "code")));

    /** Lower-case label to the literal sub-command prefixes (lower case) after which arguments are secret. */
    private final Map<String, List<List<String>>> prefixesByLabel;

    private PasswordCommandMask(Map<String, List<List<String>>> prefixesByLabel) {
        this.prefixesByLabel = prefixesByLabel;
    }

    /**
     * The mask for this module's own commands.
     *
     * @return a mask derived from {@link #MODULE_COMMANDS}
     */
    public static PasswordCommandMask forModuleCommands() {
        return fromCommandClasses(MODULE_COMMANDS);
    }

    /**
     * Derives a mask from command classes' {@code @CmdExecutor} aliases and {@code @CmdMapping} formats.
     * A class without {@code @CmdExecutor} contributes nothing.
     *
     * @param commandClasses the command classes to read
     * @return the derived mask
     */
    public static PasswordCommandMask fromCommandClasses(Iterable<Class<?>> commandClasses) {
        Map<String, List<List<String>>> prefixes = new HashMap<>();
        for (Class<?> commandClass : commandClasses) {
            CmdExecutor executor = commandClass.getAnnotation(CmdExecutor.class);
            if (executor == null) {
                continue;
            }
            for (Method method : commandClass.getDeclaredMethods()) {
                CmdMapping mapping = method.getAnnotation(CmdMapping.class);
                List<String> prefix = mapping == null ? null : secretPrefix(mapping.format());
                if (prefix == null) {
                    continue;
                }
                for (String alias : executor.alias()) {
                    List<List<String>> forLabel = prefixes.computeIfAbsent(alias.toLowerCase(Locale.ROOT),
                            key -> new ArrayList<>());
                    if (!forLabel.contains(prefix)) {
                        forLabel.add(prefix);
                    }
                }
            }
        }
        return new PasswordCommandMask(prefixes);
    }

    /**
     * The parameter names of a {@code @CmdMapping} format, in order: every {@code <name>} or
     * {@code [name]} token, without its brackets and without a trailing {@code ...}.
     *
     * @param format a {@code @CmdMapping} format
     * @return the parameter names, possibly empty
     */
    public static List<String> parameterNames(String format) {
        List<String> names = new ArrayList<>();
        for (String token : tokens(format)) {
            if (isParameter(token)) {
                names.add(parameterName(token));
            }
        }
        return names;
    }

    /**
     * The labels (lower case, without namespace) whose arguments may be masked.
     *
     * @return an unmodifiable view of the labels
     */
    public Set<String> labels() {
        return Collections.unmodifiableSet(prefixesByLabel.keySet());
    }

    /**
     * Masks a full Paper command log line.
     *
     * @param logLine the formatted log message, for example {@code Steve issued server command: /login pw}
     * @return the line with the command's secret arguments replaced by {@link #MASK}, or {@code null}
     *     when the line is not a command line, carries no secret, or is already masked
     */
    public String maskLogLine(String logLine) {
        if (logLine == null) {
            return null;
        }
        int marker = logLine.indexOf(ISSUED_COMMAND_MARKER);
        if (marker < 0) {
            return null;
        }
        int commandStart = marker + ISSUED_COMMAND_MARKER.length();
        String masked = maskCommandLine(logLine.substring(commandStart));
        return masked == null ? null : logLine.substring(0, commandStart) + masked;
    }

    /**
     * Masks one command line as a player typed it.
     *
     * @param commandLine the command line, with or without its leading {@code /} (or several)
     * @return the line with the label (and any literal sub-command) kept and every later argument
     *     replaced by one {@link #MASK}, or {@code null} when nothing needs masking: not one of these
     *     labels, no arguments, no secret sub-command, or the arguments are already exactly {@link #MASK}
     */
    public String maskCommandLine(String commandLine) {
        if (commandLine == null) {
            return null;
        }
        // Every leading slash is skipped: Paper logs a mistyped "//login <password>" as well.
        int labelStart = 0;
        while (labelStart < commandLine.length() && commandLine.charAt(labelStart) == '/') {
            labelStart++;
        }
        int labelEnd = labelStart;
        while (labelEnd < commandLine.length() && !Character.isWhitespace(commandLine.charAt(labelEnd))) {
            labelEnd++;
        }
        String label = commandLine.substring(labelStart, labelEnd);
        List<List<String>> prefixes = prefixesByLabel.get(baseLabel(label));
        if (prefixes == null) {
            return null;
        }
        String[] arguments = commandLine.substring(labelEnd).trim().split("\\s+");
        if (arguments.length == 1 && arguments[0].isEmpty()) {
            return null;
        }
        int kept = keptArgumentCount(prefixes, arguments);
        if (kept < 0 || kept == arguments.length) {
            return null;
        }
        if (kept == arguments.length - 1 && MASK.equals(arguments[kept])) {
            return null;
        }
        StringBuilder masked = new StringBuilder(commandLine.substring(0, labelEnd));
        for (int i = 0; i < kept; i++) {
            masked.append(' ').append(arguments[i]);
        }
        return masked.append(' ').append(MASK).toString();
    }

    /**
     * How many leading arguments match the longest secret prefix, or {@code -1} when none matches.
     */
    private static int keptArgumentCount(List<List<String>> prefixes, String[] arguments) {
        int best = -1;
        for (List<String> prefix : prefixes) {
            if (prefix.size() > best && startsWith(arguments, prefix)) {
                best = prefix.size();
            }
        }
        return best;
    }

    private static boolean startsWith(String[] arguments, List<String> prefix) {
        if (prefix.size() > arguments.length) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equalsIgnoreCase(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    /** The label without any {@code namespace:} prefix, lower case. */
    private static String baseLabel(String label) {
        return label.substring(label.lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * The literal words in front of a format's first parameter, when the format names a secret
     * parameter; {@code null} when it names none.
     */
    private static List<String> secretPrefix(String format) {
        boolean secret = false;
        for (String name : parameterNames(format)) {
            if (SECRET_PARAMETERS.contains(name.toLowerCase(Locale.ROOT))) {
                secret = true;
                break;
            }
        }
        if (!secret) {
            return null;
        }
        List<String> prefix = new ArrayList<>();
        for (String token : tokens(format)) {
            if (isParameter(token)) {
                break;
            }
            prefix.add(token.toLowerCase(Locale.ROOT));
        }
        return prefix;
    }

    private static String[] tokens(String format) {
        String trimmed = format == null ? "" : format.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    private static boolean isParameter(String token) {
        return token.startsWith("<") || token.startsWith("[");
    }

    private static String parameterName(String token) {
        String name = token.substring(1);
        if (name.endsWith(">") || name.endsWith("]")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.endsWith("...")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }
}
