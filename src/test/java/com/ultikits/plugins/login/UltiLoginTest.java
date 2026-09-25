package com.ultikits.plugins.login;

import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
     * both were deleted rather than renamed to a hook: unload and reload are performed entirely by
     * the framework's final methods, and the reload one is what re-reads {@code login.yml} into
     * {@code LoginConfig}. Neither template method may be declared here.
     * <p>
     * Rewritten for UltiKits/UltiLogin#23: an {@code onReload()} hook now exists, and its only work
     * is the removed-key warning (see {@link RemovedKeyCheckWiring}) -- so that an operator who edits
     * the dead {@code messages.wrong-password} and runs {@code /ul reload UltiLogin} is told it has
     * no effect. The unload hook is still absent: nothing is unloaded that the framework does not
     * already unload.
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
        @DisplayName("declares no onUnregister() hook, because the unload override was log-only")
        void declaresNoUnloadHook() {
            assertThat(declaredMethodNames())
                    .doesNotContain("onUnregister");
        }

        private List<String> declaredMethodNames() {
            List<String> names = new ArrayList<>();
            for (Method method : UltiLogin.class.getDeclaredMethods()) {
                names.add(method.getName());
            }
            return names;
        }
    }

    /**
     * UltiKits/UltiLogin#23. {@code RemovedConfigKeysTest} guards the check's predicate; these
     * tests guard its WIRING, which is a separate claim: with the call sites deleted the predicate
     * tests stay green, and a server with a leftover key prints nothing, exactly like a server
     * without one. Both entry points are covered -- module enable and {@code /ul reload
     * UltiLogin} -- because a guard on one would leave the other free to lose its call silently.
     * <p>
     * The operator's file is reached through {@code operatorConfigFile()}, a package-private seam:
     * the framework's {@code getConfigFile} is {@code protected final}, so this package can neither
     * call nor stub it, and a mocked plugin returns {@code null} from it.
     */
    @Nested
    @DisplayName("the removed-key check is actually called (UltiKits/UltiLogin#23)")
    class RemovedKeyCheckWiring {

        private static final String FILE_WITH_THE_REMOVED_KEY =
                "messages:\n  login-success: 'ok'\n  wrong-password: 'x'\n";

        private static final String FILE_WITHOUT_THE_REMOVED_KEY =
                "messages:\n  login-success: 'ok'\n";

        private PluginLogger logger;

        private UltiLogin pluginReading(File dir, String body) throws IOException {
            File file = new File(dir, "login.yml");
            Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));

            UltiLogin plugin = mock(UltiLogin.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.login.i18n.CatalogueText.answer("en"));
            when(plugin.operatorConfigFile()).thenReturn(file);
            return plugin;
        }

        private List<String> warnings() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(logger, atLeast(0)).warn(captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("POSITIVE CONTROL: enabling the module warns about the leftover key")
        void registerSelfWarns(@TempDir File dir) throws IOException {
            UltiLogin plugin = pluginReading(dir, FILE_WITH_THE_REMOVED_KEY);
            when(plugin.registerSelf()).thenCallRealMethod();

            assertThat(plugin.registerSelf()).isTrue();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("messages.wrong-password");
        }

        @Test
        @DisplayName("POSITIVE CONTROL: /ul reload UltiLogin warns about the leftover key")
        void onReloadWarns(@TempDir File dir) throws IOException {
            UltiLogin plugin = pluginReading(dir, FILE_WITH_THE_REMOVED_KEY);
            doCallRealMethod().when(plugin).onReload();

            plugin.onReload();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("messages.wrong-password");
        }

        @Test
        @DisplayName("the check reads the same file LoginConfig declares, from one source")
        void readsTheFileLoginConfigDeclares() {
            // Gate 1 IN-01. Every other test here stubs operatorConfigFile(), so if the path the
            // check resolves ever drifted from the file LoginConfig binds, the production check
            // would read a file that does not exist, return silently, and look exactly like a
            // server with no leftover key. The path the check uses must equal both places
            // LoginConfig names its file: the @ConfigEntity value and the constructor argument.
            UltiLogin plugin = mock(UltiLogin.class);
            when(plugin.operatorConfigPath()).thenCallRealMethod();

            String declared = com.ultikits.plugins.login.config.LoginConfig.class
                    .getAnnotation(com.ultikits.ultitools.annotations.ConfigEntity.class).value();

            assertThat(declared).isEqualTo("config/login.yml");
            assertThat(new com.ultikits.plugins.login.config.LoginConfig().getConfigFilePath())
                    .isEqualTo(declared);
            assertThat(plugin.operatorConfigPath()).isEqualTo(declared);
        }

        @Test
        @DisplayName("a failure inside the check never costs the module its enable or its reload")
        void aFailingCheckNeverFailsEnableOrReload() {
            // Gate 1 IN-04. The check is advisory and sits on the enable path of the module whose
            // absence means nobody is asked to log in, so an exception escaping it would fail open
            // for the whole server. Simulated with the file lookup itself failing.
            UltiLogin plugin = mock(UltiLogin.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.login.i18n.CatalogueText.answer("en"));
            when(plugin.operatorConfigFile())
                    .thenThrow(new java.io.UncheckedIOException(new IOException("disk unavailable")));
            when(plugin.registerSelf()).thenCallRealMethod();
            doCallRealMethod().when(plugin).onReload();

            assertThat(plugin.registerSelf()).isTrue();
            assertThatCode(plugin::onReload).doesNotThrowAnyException();

            ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
            verify(logger, times(2)).warn(any(Throwable.class), messages.capture());
            assertThat(messages.getAllValues())
                    .allSatisfy(m -> assertThat(m).contains("removed").contains("login.yml"));
        }

        @Test
        @DisplayName("neither entry point warns when the file holds no removed key")
        void neitherWarnsOnACleanFile(@TempDir File dir) throws IOException {
            // Paired with the two controls above: same entry points, same file, the one key
            // taken out and nothing else changed.
            UltiLogin onEnable = pluginReading(dir, FILE_WITHOUT_THE_REMOVED_KEY);
            when(onEnable.registerSelf()).thenCallRealMethod();
            assertThat(onEnable.registerSelf()).isTrue();
            assertThat(warnings()).isEmpty();

            UltiLogin onReload = pluginReading(dir, FILE_WITHOUT_THE_REMOVED_KEY);
            doCallRealMethod().when(onReload).onReload();
            onReload.onReload();
            assertThat(warnings()).isEmpty();
        }
    }
    /**
     * The console line a failed removed-key check prints follows the language setting
     * (UltiKits/UltiLogin#20). Here, not in the language test, because the file seam is
     * package-private.
     */
    @Nested
    @DisplayName("the check-failed console line follows the language setting (UltiKits/UltiLogin#20)")
    class CheckFailedLanguage {

        @Test
        @DisplayName("reported in Chinese under language: zh")
        void chinese() {
            UltiLogin plugin = mock(UltiLogin.class);
            PluginLogger logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.login.i18n.CatalogueText.answer("zh"));
            when(plugin.operatorConfigFile())
                    .thenThrow(new java.io.UncheckedIOException(new IOException("disk unavailable")));
            when(plugin.registerSelf()).thenCallRealMethod();

            plugin.registerSelf();

            String expected = com.ultikits.plugins.login.i18n.CatalogueText.entries("zh")
                    .getOrDefault("log_removed_key_check_failed", "<lang/zh has no log_removed_key_check_failed>")
                    .replace("{FILE}", com.ultikits.plugins.login.config.LoginConfig.CONFIG_FILE);
            verify(logger).warn(any(Throwable.class), org.mockito.ArgumentMatchers.eq(expected));
        }
    }
}
