package com.ultikits.plugins.login;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.ConfigManager;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    @DisplayName("unregisterSelf should log message")
    void unregisterSelf() throws Exception {
        UltiLogin plugin = mock(UltiLogin.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenReturn("UltiLogin disabled");
        doCallRealMethod().when(plugin).unregisterSelf();

        plugin.unregisterSelf();

        verify(logger).info("UltiLogin disabled");
    }

    @Test
    @DisplayName("reloadSelf should log message")
    void reloadSelf() throws Exception {
        UltiLogin plugin = mock(UltiLogin.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenReturn("UltiLogin reloaded");
        doCallRealMethod().when(plugin).reloadSelf();

        ConfigManager mockConfigManager = mock(ConfigManager.class);
        try (MockedStatic<UltiToolsPlugin> staticMock =
                mockStatic(UltiToolsPlugin.class, CALLS_REAL_METHODS)) {
            staticMock.when(UltiToolsPlugin::getConfigManager).thenReturn(mockConfigManager);

            plugin.reloadSelf();
        }

        verify(logger).info("UltiLogin reloaded");
    }

    /**
     * UltiLogin#13 (13-13): {@code reloadSelf()} used to log a success message without ever
     * calling {@code super.reloadSelf()}, so {@code ConfigManager.reloadConfigs(this)} -- the
     * only thing that re-reads {@code login.yml} into a running {@code LoginConfig} -- was never
     * invoked. This is the direct regression guard at the bug's own site: it is RED against the
     * pre-fix override (0 invocations measured in 13-LEDGER-UltiLogin.md's "Recovery command
     * diagnosis" instrument 2) and GREEN once {@code reloadSelf()} calls {@code super.reloadSelf()}.
     */
    @Test
    @DisplayName("reloadSelf should reach ConfigManager.reloadConfigs so login.yml is actually re-read")
    void reloadSelfReachesConfigManager() throws Exception {
        UltiLogin plugin = mock(UltiLogin.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenReturn("UltiLogin reloaded");
        doCallRealMethod().when(plugin).reloadSelf();

        ConfigManager mockConfigManager = mock(ConfigManager.class);
        try (MockedStatic<UltiToolsPlugin> staticMock =
                mockStatic(UltiToolsPlugin.class, CALLS_REAL_METHODS)) {
            staticMock.when(UltiToolsPlugin::getConfigManager).thenReturn(mockConfigManager);

            plugin.reloadSelf();

            verify(mockConfigManager).reloadConfigs(plugin);
        }
    }
}
