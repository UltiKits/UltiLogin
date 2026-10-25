package com.ultikits.plugins.login.logging;

import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.ultitools.websocket.SensitiveCommandRegistry;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The module's commands whose arguments must never reach UltiCloud, and their one registration
 * with UltiTools-API 6.3.1's {@link SensitiveCommandRegistry}.
 * <p>
 * <b>Every argument-taking command is registered, with all its aliases.</b> The arguments of this
 * module's commands are passwords, recovery or e-mail codes, e-mail addresses, or a player name next
 * to a password; UltiCloud needs none of them. The set is derived from the {@link CmdExecutor#alias()}
 * and {@link CmdMapping#format()} declarations of {@link PasswordCommandMask#MODULE_COMMANDS} (the
 * same list the local mask reads, never a copy of it): a command class with any mapping whose format
 * names a parameter contributes every alias, lower-cased. {@code /panel} takes no argument and is
 * therefore not registered. A command class added later is covered without touching this class.
 * <p>
 * <b>Only cloud-bound text is redacted.</b> The framework applies the registry to what it sends to
 * UltiCloud (the panel log stream, error reports and chat); it never changes the local console or
 * the log files. Those stay covered by the module's own {@link PasswordCommandMask}, whose narrower
 * table masks only password-bearing arguments (so {@code /regs} stays readable locally).
 * <p>
 * Registration is idempotent, so a module reload is harmless, and lasts until the server restarts:
 * the framework has no unregister. Because the framework matches the command name, not its
 * sub-command, {@code /logadmin info <player>} also reaches the panel as {@code /logadmin ***}.
 */
public final class CloudSensitiveCommands {

    /** The derived names, computed once from {@link PasswordCommandMask#MODULE_COMMANDS}. */
    private static final Set<String> NAMES = fromCommandClasses(PasswordCommandMask.MODULE_COMMANDS);

    private CloudSensitiveCommands() {
        // No instances.
    }

    /**
     * The lower-case labels this module registers.
     *
     * @return an unmodifiable set of every alias of every argument-taking module command
     */
    public static Set<String> names() {
        return NAMES;
    }

    /**
     * Derives the labels from command classes: a class with {@code @CmdExecutor} and at least one
     * {@code @CmdMapping} whose format names a parameter contributes all its aliases, lower-cased with
     * {@link Locale#ROOT}. A class without {@code @CmdExecutor} contributes nothing.
     *
     * @param classes the command classes to read
     * @return an unmodifiable set of the derived labels
     */
    static Set<String> fromCommandClasses(Iterable<Class<?>> classes) {
        Set<String> names = new TreeSet<>();
        for (Class<?> commandClass : classes) {
            CmdExecutor executor = commandClass.getAnnotation(CmdExecutor.class);
            if (executor != null && takesArguments(commandClass)) {
                for (String alias : executor.alias()) {
                    names.add(alias.toLowerCase(Locale.ROOT));
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }

    /**
     * Registers {@link #names()} with the framework's registry, once per call.
     *
     * @param registry the framework's sensitive-command registry; {@code null} does nothing
     */
    public static void registerWith(SensitiveCommandRegistry registry) {
        if (registry == null) {
            return;
        }
        registry.register(NAMES.toArray(new String[0]));
    }

    private static boolean takesArguments(Class<?> commandClass) {
        for (Method method : commandClass.getDeclaredMethods()) {
            CmdMapping mapping = method.getAnnotation(CmdMapping.class);
            if (mapping != null && !PasswordCommandMask.parameterNames(mapping.format()).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
