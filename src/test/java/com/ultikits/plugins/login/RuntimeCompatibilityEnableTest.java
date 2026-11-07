package com.ultikits.plugins.login;

import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.logging.PasswordLogFilterInstaller;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.PostConstruct;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.PluginManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RuntimeCompatibilityEnableTest {
    private UltiLogin module(String language) {
        UltiLogin module = mock(UltiLogin.class);
        when(module.getLogger()).thenReturn(mock(PluginLogger.class));
        when(module.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        when(module.registerSelf()).thenCallRealMethod();
        when(module.refusesUnsupportedRuntime()).thenCallRealMethod();
        return module;
    }

    private org.bukkit.plugin.PluginManager runtime(MockedStatic<Bukkit> bukkit, String minecraft, String framework) {
        org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
        Plugin core = mock(Plugin.class);
        when(core.getDescription()).thenReturn(new PluginDescriptionFile("UltiTools", framework, "example.Core"));
        when(plugins.getPlugin("UltiTools")).thenReturn(core);
        bukkit.when(Bukkit::getBukkitVersion).thenReturn(minecraft);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("runtime-compatibility-test"));
        return plugins;
    }

    @Test
    void refusesBeforeAnyModuleActivation() {
        UltiLogin module = module("en");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PasswordLogFilterInstaller> filters = mockStatic(PasswordLogFilterInstaller.class)) {
            org.bukkit.plugin.PluginManager plugins = runtime(bukkit, "1.20.6-R0.1-SNAPSHOT", "6.3.1-SNAPSHOT");
            assertThat(module.registerSelf()).isFalse();
            verify(plugins).getPlugin("UltiTools");
            verifyNoMoreInteractions(plugins);
            bukkit.verify(() -> Bukkit.getScheduler(), never());
            verify(module.getLogger()).error("UltiLogin needs UltiTools 6.4.0 or later on Minecraft versions below 1.21 "
                    + "(server: 1.20.6-R0.1-SNAPSHOT, UltiTools: 6.3.1-SNAPSHOT). Please update UltiTools.");
            verify(module.getLogger(), never()).info(anyString());
            verify(module, never()).sensitiveCommandRegistry();
            verify(module, never()).activatePlayerLinks();
            verify(module, never()).registerEquipmentSwapProtection();
            verify(module, never()).getConfig(any(Class.class));
            filters.verifyNoInteractions();
        }
    }

    @Test
    void refusalMessageUsesChineseCatalogue() {
        UltiLogin module = module("zh");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            runtime(bukkit, "1.19.2", "6.3.0");
            assertThat(module.registerSelf()).isFalse();
            verify(module.getLogger()).error(CatalogueText.entries("zh").get("log_runtime_incompatible")
                    .replace("{SERVER}", "1.19.2").replace("{FRAMEWORK}", "6.3.0"));
        }
    }

    @Test
    void unknownVersionWarnsAndStillEnables() {
        UltiLogin module = module("en");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            runtime(bukkit, "unknown", "6.3.0");
            assertThat(module.registerSelf()).isTrue();
            verify(module.getLogger()).warn("UltiLogin cannot determine runtime compatibility "
                    + "(server: unknown, UltiTools: 6.3.0); continuing without the version gate.");
            verify(module).activatePlayerLinks();
        } finally {
            PasswordLogFilterInstaller.uninstall();
        }
    }

    @Test
    void allowedVersionKeepsActivation() {
        UltiLogin module = module("en");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            runtime(bukkit, "1.21.11-R0.1-SNAPSHOT", "6.3.1-SNAPSHOT");
            assertThat(module.registerSelf()).isTrue();
            verify(module).activatePlayerLinks();
            verify(module.getLogger(), never()).error(anyString());
            verify(module.getLogger(), never()).warn(anyString());
        } finally {
            PasswordLogFilterInstaller.uninstall();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"6.4.0", "6.4.0-SNAPSHOT"})
    void oldServerWithCompatibleFrameworkActivatesBothHooks(String framework) {
        UltiLogin module = module("en");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PasswordLogFilterInstaller> filters = mockStatic(PasswordLogFilterInstaller.class)) {
            runtime(bukkit, "1.19.2-R0.1-SNAPSHOT", framework);
            assertThat(module.registerSelf()).isTrue();
            org.mockito.InOrder hooks = inOrder(module);
            hooks.verify(module).refusesUnsupportedRuntime();
            hooks.verify(module).activatePlayerLinks();
            hooks.verify(module).registerEquipmentSwapProtection();
            verify(module.getLogger(), never()).error(anyString());
            verify(module.getLogger(), never()).warn(anyString());
        }
    }

    @Test
    void playerLinkRegistrationIsNotAPreEnableHook() throws Exception {
        assertThat(LoginService.class.getMethod("registerPlayerLinkHandler").isAnnotationPresent(PostConstruct.class))
                .isFalse();
    }

    @Test
    void acceptedModuleActivatesItsOwnService() {
        UltiLogin module = mock(UltiLogin.class);
        SimpleContainer context = mock(SimpleContainer.class);
        LoginService service = mock(LoginService.class);
        when(module.getContext()).thenReturn(context);
        when(context.getBean(LoginService.class)).thenReturn(service);
        doCallRealMethod().when(module).activatePlayerLinks();
        module.activatePlayerLinks();
        verify(service).registerPlayerLinkHandler();
    }

    @Test
    void frameworkRefusalClosesContextAndNeverListsOrSchedulesModule() throws Exception {
        UltiLogin module = module("en");
        SimpleContainer context = mock(SimpleContainer.class);
        when(module.getContext()).thenReturn(context);
        when(module.getPluginName()).thenReturn("UltiLogin");
        PluginManager manager = new PluginManager();
        UltiTools core = mock(UltiTools.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<UltiTools> framework = mockStatic(UltiTools.class)) {
            runtime(bukkit, "1.20.6", "6.3.1-SNAPSHOT");
            framework.when(UltiTools::getInstance).thenReturn(core);
            // Invoke the actual framework callback boundary after container assembly, not a copied branch.
            Method attempt = PluginManager.class.getDeclaredMethod("attemptPluginRegistration", UltiToolsPlugin.class);
            attempt.setAccessible(true);
            assertThat(attempt.invoke(manager, module)).isEqualTo(false);
            assertThat(manager.getPluginList()).doesNotContain(module);
            verify(context).close();
            verify(module, never()).activatePlayerLinks();
            // onPluginRegistered is the path which schedules tasks; registerClass calls registerBukkit
            // only when this callback returns true (also checked against the actual 6.3.x bytecode).
            assertThat(manager.getTaskManager()).isNull();
            bukkit.verify(() -> Bukkit.getScheduler(), never());
        }
    }
}
