package com.ultikits.plugins.login;

import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UltiLogin Main Class Tests")
class UltiLoginTest {

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
    }

    @Test
    @DisplayName("registerSelf should return true")
    void registerSelf() throws Exception {
        UltiLogin plugin = mock(UltiLogin.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenReturn("UltiLogin enabled");
        when(plugin.registerSelf()).thenCallRealMethod();

        boolean result = plugin.registerSelf();

        assertThat(result).isTrue();
        verify(logger).info("UltiLogin enabled");
    }

    /**
     * UltiKits/UltiLogin#29: UltiTools 6.3.0 makes {@code unregisterSelf()} and
     * {@code reloadSelf()} {@code final} template methods. This module's two overrides only
     * logged a line (the reload one after calling the framework's own reload, UltiLogin#13), so
     * both are deleted rather than renamed to a hook: unload and reload are performed entirely by
     * the framework's final methods, and the reload one is what re-reads {@code login.yml} into
     * {@code LoginConfig}. Neither template method may be declared here, and no hook is needed.
     */
    @Nested
    @DisplayName("Lifecycle template methods (UltiKits/UltiLogin#29)")
    class LifecycleTemplateMethods {

        @Test
        @DisplayName("declares neither framework template method, unregisterSelf() nor reloadSelf()")
        void declaresNoTemplateMethodOverride() {
            assertThat(declaredMethodNames())
                    .doesNotContain("unregisterSelf", "reloadSelf");
        }

        @Test
        @DisplayName("declares no onUnregister() or onReload() hook, because both overrides were log-only")
        void declaresNoLifecycleHook() {
            assertThat(declaredMethodNames())
                    .doesNotContain("onUnregister", "onReload");
        }

        private List<String> declaredMethodNames() {
            List<String> names = new ArrayList<>();
            for (Method method : UltiLogin.class.getDeclaredMethods()) {
                names.add(method.getName());
            }
            return names;
        }
    }
}
