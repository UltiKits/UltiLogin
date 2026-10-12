package com.ultikits.plugins.login.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Installing the password log filter may fail -- Log4j Core absent or not the server's
 * logging backend -- and that must cost the module only the mask, never its enable.
 */
@DisplayName("PasswordLogFilterInstaller")
class PasswordLogFilterInstallerTest {

    @AfterEach
    void tearDown() {
        PasswordLogFilterInstaller.uninstall();
        Log4jPasswordFilter.removeFiltersOfThisClass(Log4jTestSupport.root());
    }

    @Test
    @DisplayName("with Log4j Core present, install succeeds and puts one filter on the root logger")
    void installsWhenCorePresent() {
        assertThat(PasswordLogFilterInstaller.install(PasswordCommandMask.forModuleCommands())).isNull();
        assertThat(PasswordLogFilterInstaller.isInstalled()).isTrue();
        assertThat(Log4jTestSupport.passwordFiltersOnRoot()).isEqualTo(1);

        assertThat(PasswordLogFilterInstaller.uninstall()).isNull();
        assertThat(PasswordLogFilterInstaller.isInstalled()).isFalse();
        assertThat(Log4jTestSupport.passwordFiltersOnRoot()).isZero();
    }

    @Test
    @DisplayName("a missing Log4j Core (NoClassDefFoundError) becomes a reason, not a failure")
    void linkageErrorBecomesAReason() {
        String reason = PasswordLogFilterInstaller.install(() -> {
            throw new NoClassDefFoundError("org/apache/logging/log4j/core/filter/AbstractFilter");
        });

        assertThat(reason).contains("NoClassDefFoundError").contains("AbstractFilter");
        assertThat(PasswordLogFilterInstaller.isInstalled()).isFalse();
    }

    @Test
    @DisplayName("a runtime failure, or a reported reason, leaves nothing installed")
    void otherFailures() {
        assertThat(PasswordLogFilterInstaller.install(() -> {
            throw new IllegalStateException("no root logger");
        })).contains("no root logger");
        assertThat(PasswordLogFilterInstaller.install(() -> "not Log4j Core")).isEqualTo("not Log4j Core");
        assertThat(PasswordLogFilterInstaller.isInstalled()).isFalse();
    }

    @Test
    @DisplayName("uninstall touches Log4j only when an install succeeded, and only once")
    void uninstallOnlyAfterInstall() {
        AtomicInteger removals = new AtomicInteger();

        assertThat(PasswordLogFilterInstaller.uninstall(() -> {
            removals.incrementAndGet();
            return null;
        })).isNull();
        assertThat(removals).hasValue(0);

        PasswordLogFilterInstaller.install(() -> null);
        PasswordLogFilterInstaller.uninstall(() -> {
            removals.incrementAndGet();
            return null;
        });
        PasswordLogFilterInstaller.uninstall(() -> {
            removals.incrementAndGet();
            return null;
        });
        assertThat(removals).hasValue(1);
    }

    @Test
    @DisplayName("a failed second install keeps the first one removable")
    void failedReinstallKeepsTheFirstRemovable() {
        PasswordLogFilterInstaller.install(() -> null);
        PasswordLogFilterInstaller.install(() -> "broken");

        assertThat(PasswordLogFilterInstaller.isInstalled()).isTrue();
    }

    @Test
    @DisplayName("a failing removal becomes a reason")
    void failingRemoval() {
        PasswordLogFilterInstaller.install(() -> null);

        String reason = PasswordLogFilterInstaller.uninstall(() -> {
            throw new NoClassDefFoundError("org/apache/logging/log4j/core/Filter");
        });

        assertThat(reason).contains("NoClassDefFoundError");
        assertThat(PasswordLogFilterInstaller.isInstalled()).isFalse();
    }

    @Test
    @DisplayName("the real install path, loaded where Log4j Core cannot be found, reports a reason")
    void realPathWithoutLog4jCore() throws Exception {
        URL classes = PasswordLogFilterInstaller.class.getProtectionDomain().getCodeSource().getLocation();
        ClassLoader hidesCore = new ClassLoader(PasswordLogFilterInstaller.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.apache.logging.log4j.core.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        // Child-first for this module's own classes, so the installer and the filter are defined
        // by a loader that cannot see Log4j Core.
        try (URLClassLoader isolated = new URLClassLoader(new URL[]{classes}, hidesCore) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.ultikits.plugins.login.logging.")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        return loaded != null ? loaded : findClass(name);
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> installer = isolated.loadClass(PasswordLogFilterInstaller.class.getName());
            Class<?> maskType = isolated.loadClass(PasswordCommandMask.class.getName());
            Object mask = maskType.getMethod("forModuleCommands").invoke(null);
            Method install = installer.getMethod("install", maskType);
            Method isInstalled = installer.getMethod("isInstalled");
            Method uninstall = installer.getMethod("uninstall");

            Object reason = install.invoke(null, mask);

            assertThat((String) reason).contains("org/apache/logging/log4j/core");
            assertThat(isInstalled.invoke(null)).isEqualTo(false);
            assertThat(uninstall.invoke(null)).isNull();
        }
        assertThat(Log4jTestSupport.passwordFiltersOnRoot()).isZero();
    }
}
