package com.ultikits.plugins.login.i18n;

import com.ultikits.plugins.login.config.RemovedConfigKeys;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Test support for the language sweep's seams.
 * <p>
 * Routing this module's text through its language file gives {@link LoginService} an {@code i18n}
 * pass-through (the commands reach the catalogue through the service they already hold) and
 * {@link RemovedConfigKeys#warnAboutLeftovers} a plugin parameter. The tests are written once and must
 * compile and run against the code before and after that change (a revert proof restores the old
 * production files and runs these same tests), so they reach both reflectively: when the new member
 * exists it is stubbed or called, otherwise the old behaviour runs untouched.
 */
public final class LoginSeams {

    private LoginSeams() {
    }

    /** Makes a mocked {@link LoginService} answer {@code i18n} from {@code code}'s catalogue, when it has one. */
    public static void speak(LoginService mockService, String code) {
        Method i18n;
        try {
            i18n = LoginService.class.getMethod("i18n", String.class);
        } catch (NoSuchMethodException e) {
            return;
        }
        try {
            lenient().when(i18n.invoke(mockService, anyString())).thenAnswer(CatalogueText.answer(code));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot stub LoginService#i18n", e);
        }
    }

    /**
     * Binds a real configuration object to {@code plugin}, as the framework's {@code init} does, so the
     * configuration reads its language file through the same plugin.
     */
    public static void bind(AbstractConfigEntity config, UltiToolsPlugin plugin) {
        try {
            Field field = AbstractConfigEntity.class.getDeclaredField("ultiToolsPlugin");
            field.setAccessible(true);
            field.set(config, plugin);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot bind the configuration to its plugin", e);
        }
    }

    /** {@link RemovedConfigKeys#warnAboutLeftovers}, given the plugin when the method takes one. */
    public static void warnAboutLeftovers(File configFile, Consumer<String> warn, UltiToolsPlugin plugin) {
        try {
            try {
                RemovedConfigKeys.class.getMethod("warnAboutLeftovers", File.class, Consumer.class, UltiToolsPlugin.class)
                        .invoke(null, configFile, warn, plugin);
            } catch (NoSuchMethodException e) {
                RemovedConfigKeys.class.getMethod("warnAboutLeftovers", File.class, Consumer.class)
                        .invoke(null, configFile, warn);
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot call RemovedConfigKeys#warnAboutLeftovers", e);
        }
    }
}
