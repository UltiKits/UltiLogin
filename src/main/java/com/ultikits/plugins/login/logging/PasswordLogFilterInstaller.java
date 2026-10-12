package com.ultikits.plugins.login.logging;

import java.util.function.Supplier;

/**
 * Installs and removes {@link Log4jPasswordFilter} without letting a missing Log4j Core cost the
 * module its enable.
 * <p>
 * Paper ships Log4j Core, but a fork, a test harness or a future server may not. This class holds
 * no Log4j type, so loading it never needs Log4j Core; the first use of {@link Log4jPasswordFilter}
 * is inside a guard that turns a {@link LinkageError} (Log4j Core absent or incompatible) or a
 * {@link RuntimeException} into a reason the caller logs as a warning. The module then runs without
 * the mask, which is what it did before the mask existed.
 */
public final class PasswordLogFilterInstaller {

    /** Whether the last {@link #install} succeeded and nothing has removed it since. Guarded by the class lock. */
    private static boolean installed;

    private PasswordLogFilterInstaller() {
    }

    /**
     * Installs the filter for {@code mask} on the server's root logger configuration, replacing any
     * earlier one, so calling it twice leaves exactly one.
     *
     * @param mask what to mask
     * @return {@code null} when installed, otherwise a short reason it was not
     */
    public static String install(PasswordCommandMask mask) {
        return install(() -> Log4jPasswordFilter.install(mask));
    }

    /**
     * Removes the filter; does nothing when none was installed.
     *
     * @return {@code null} when nothing failed, otherwise a short reason
     */
    public static String uninstall() {
        return uninstall(() -> {
            Log4jPasswordFilter.uninstall();
            return null;
        });
    }

    /**
     * Whether the filter is installed.
     *
     * @return whether the last {@link #install} succeeded and {@link #uninstall} has not run since
     */
    public static synchronized boolean isInstalled() {
        return installed;
    }

    /**
     * The guarded install. Package-private so a test can stand in for Log4j Core being absent.
     *
     * @param hook installs the filter, returning {@code null} or a reason it did not
     * @return {@code null} when installed, otherwise a short reason it was not
     */
    // Deliberate barrier: a failure here must cost the module only the mask, never its enable.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    static synchronized String install(Supplier<String> hook) {
        String reason;
        try {
            reason = hook.get();
        } catch (LinkageError | RuntimeException e) {
            reason = e.toString();
        }
        // A failed second install leaves an earlier filter in place, so it must still be removable.
        installed |= reason == null;
        return reason;
    }

    /**
     * The guarded uninstall. Package-private for the same reason as {@link #install(Supplier)}.
     *
     * @param hook removes the filter, returning {@code null} or a reason it could not
     * @return {@code null} when nothing failed, otherwise a short reason
     */
    // Deliberate barrier: an unload must carry on with the framework's own cleanup.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    static synchronized String uninstall(Supplier<String> hook) {
        if (!installed) {
            return null;
        }
        installed = false;
        try {
            return hook.get();
        } catch (LinkageError | RuntimeException e) {
            return e.toString();
        }
    }
}
