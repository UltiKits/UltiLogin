package com.ultikits.plugins.login.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * A Log4j filter on the server's root logger configuration that keeps password-bearing command
 * lines out of every log output, replacing each with a masked copy.
 * <p>
 * <b>Why the root logger.</b> Paper logs a player's command line through Log4j, from a logger named
 * after its packet listener, and its configuration routes every logger into the root logger's
 * appenders: the console, {@code logs/latest.log}, and (with UltiTools-API 6.3.0) the framework's
 * console mirror to the UltiPanel log stream, which is also an appender on the root logger. A
 * filter on a logger configuration runs before any of that configuration's appenders, so one filter
 * here keeps the password out of all three. Filtering only this module's own logger would not see
 * Paper's line at all.
 * <p>
 * <b>Masking, not dropping.</b> A Log4j filter can only accept or deny an event. For a matching
 * event this filter hands the root configuration a copy whose message is the masked line (same
 * logger, level, thread and time) and denies the original, so operators still see that, and by
 * whom, a login was attempted: {@code Steve issued server command: /login ******}. The copy's
 * arguments are exactly {@link PasswordCommandMask#MASK}, which {@link PasswordCommandMask} leaves
 * alone, so the copy passes this filter instead of being masked again.
 * <p>
 * <b>Scope.</b> Only events that reach the root logger configuration are seen. A server whose own
 * Log4j configuration gives the command logger a separate configuration with appenders of its own,
 * or that reconfigures Log4j while running, is outside what this filter covers.
 * <p>
 * Every Log4j Core type this module uses is confined to this class, so the rest of the module loads
 * without Log4j Core; {@link PasswordLogFilterInstaller} is the only caller.
 */
final class Log4jPasswordFilter extends AbstractFilter {

    /** The installed filter, or {@code null}. Guarded by the class lock. */
    private static Log4jPasswordFilter installed;

    /** The context {@link #installed} was installed in. Guarded by the class lock. */
    private static LoggerContext installedContext;

    private final PasswordCommandMask mask;

    private final LoggerConfig owner;

    Log4jPasswordFilter(PasswordCommandMask mask, LoggerConfig owner) {
        super(Result.NEUTRAL, Result.NEUTRAL);
        this.mask = mask;
        this.owner = owner;
    }

    /**
     * Installs the filter on the root logger configuration of the server's Log4j context. Any filter
     * of this class already there -- left by an earlier enable of this module, from this or another
     * class loader -- is removed first, so a re-enable never leaves two.
     *
     * @param mask what to mask
     * @return {@code null} when installed, otherwise why it was not
     */
    static synchronized String install(PasswordCommandMask mask) {
        org.apache.logging.log4j.spi.LoggerContext found =
                LogManager.getContext(LogManager.class.getClassLoader(), false);
        if (!(found instanceof LoggerContext)) {
            return "the server's Log4j context is " + (found == null ? "missing" : found.getClass().getName())
                    + ", not Log4j Core";
        }
        LoggerContext context = (LoggerContext) found;
        LoggerConfig root = context.getConfiguration().getRootLogger();
        removeFiltersOfThisClass(root);
        Log4jPasswordFilter filter = new Log4jPasswordFilter(mask, root);
        filter.start();
        root.addFilter(filter);
        context.updateLoggers();
        installed = filter;
        installedContext = context;
        return null;
    }

    /**
     * Removes the filter {@link #install} added; does nothing when none is installed.
     */
    static synchronized void uninstall() {
        if (installed == null) {
            return;
        }
        try {
            installed.owner.removeFilter(installed);
            installed.stop();
            installedContext.updateLoggers();
        } finally {
            installed = null;
            installedContext = null;
        }
    }

    /**
     * Whether a filter is installed.
     *
     * @return whether {@link #install} succeeded and {@link #uninstall} has not run since
     */
    static synchronized boolean isInstalled() {
        return installed != null;
    }

    /**
     * Removes every filter whose class has this class's name, compared by name so that one created by
     * an earlier class loader of this module is found too.
     */
    static void removeFiltersOfThisClass(LoggerConfig config) {
        Filter current = config.getFilter();
        List<Filter> candidates = new ArrayList<>();
        if (current instanceof CompositeFilter) {
            for (Filter each : ((CompositeFilter) current).getFiltersArray()) {
                candidates.add(each);
            }
        } else if (current != null) {
            candidates.add(current);
        }
        for (Filter candidate : candidates) {
            if (Log4jPasswordFilter.class.getName().equals(candidate.getClass().getName())) {
                config.removeFilter(candidate);
                candidate.stop();
            }
        }
    }

    // Deliberate barrier around the re-emit: whatever an appender throws, the original is denied.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public Result filter(LogEvent event) {
        Message message = event == null ? null : event.getMessage();
        if (message == null) {
            return Result.NEUTRAL;
        }
        // Cheap test first: this runs for every event of the server. For Paper's line the format is
        // "{} issued server command: {}", so nothing is formatted for any other event.
        String format = message.getFormat();
        if (format == null || !format.contains(PasswordCommandMask.ISSUED_COMMAND_MARKER.trim())) {
            return Result.NEUTRAL;
        }
        String masked = mask.maskLogLine(message.getFormattedMessage());
        if (masked == null) {
            return Result.NEUTRAL;
        }
        try {
            owner.log(new Log4jLogEvent.Builder(event).setMessage(new SimpleMessage(masked)).build());
        } catch (RuntimeException e) {
            // Fail closed: the masked copy is lost, but the original, which holds the password, is
            // still denied below. An appender's own failure is Log4j's to report, not this filter's.
            return Result.DENY;
        }
        return Result.DENY;
    }
}
