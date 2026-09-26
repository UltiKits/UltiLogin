package com.ultikits.plugins.login.config;

import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.i18n.LoginSeams;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UltiKits/UltiLogin#23. Deleting {@code messages.wrong-password} from {@link LoginConfig} stops the
 * framework writing it into a fresh {@code login.yml} and does nothing to the files already on
 * disk: the framework writes a declared default only for a key that is missing and never removes
 * one, so every upgraded server keeps the key and whatever value its operator gave it. This check
 * is the only thing that tells that operator the value means nothing.
 * <p>
 * The positive control comes first on purpose: a check that never fires and a server with no
 * leftover key print the same empty console, so the negative cases below prove nothing on their
 * own.
 */
@DisplayName("RemovedConfigKeys (UltiKits/UltiLogin#23)")
class RemovedConfigKeysTest {

    /**
     * The module, answering {@code i18n} from its English catalogue: the assertions below quote the
     * English guidance an operator reads under {@code language: en}.
     */
    private static final UltiToolsPlugin ENGLISH = englishPlugin();

    private static UltiToolsPlugin englishPlugin() {
        UltiToolsPlugin plugin = org.mockito.Mockito.mock(UltiToolsPlugin.class);
        org.mockito.Mockito.when(plugin.i18n(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(CatalogueText.answer("en"));
        return plugin;
    }


    /** The shape the framework writes on an upgraded server: the key sits among its siblings. */
    private static final String FILE_WITH_THE_REMOVED_KEY =
            "security:\n  max-login-attempts: 5\n"
            + "messages:\n"
            + "  login-success: '&aok'\n"
            + "  wrong-password: '&c密码错误！请重试。'\n"
            + "  account-locked: '&clocked {TIME}'\n";

    /** The same file with only the removed key taken out. */
    private static final String FILE_WITHOUT_THE_REMOVED_KEY =
            "security:\n  max-login-attempts: 5\n"
            + "messages:\n"
            + "  login-success: '&aok'\n"
            + "  account-locked: '&clocked {TIME}'\n";

    private static File write(File dir, String body) throws IOException {
        File file = new File(dir, "login.yml");
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    @DisplayName("POSITIVE CONTROL: a leftover messages.wrong-password produces one warning naming the module, the file and the key")
    void warnsAboutTheLeftoverKey(@TempDir File dir) throws IOException {
        File file = write(dir, FILE_WITH_THE_REMOVED_KEY);
        List<String> warnings = new ArrayList<>();

        LoginSeams.warnAboutLeftovers(file, warnings::add, ENGLISH);

        assertThat(warnings).hasSize(1);
        String warning = warnings.get(0);
        assertThat(warning).contains("UltiLogin");
        assertThat(warning).contains(file.getPath());
        assertThat(warning).contains("messages.wrong-password");
        // Where the setting went, so the operator can act on the warning.
        assertThat(warning).contains("wrong_password");
        assertThat(warning).contains("lang/");
        assertThat(warning).contains("UltiKits/UltiLogin#23");
    }

    @Test
    @DisplayName("an empty leftover value is still a leftover key")
    void warnsAboutAnEmptyLeftoverValue(@TempDir File dir) throws IOException {
        File file = write(dir, "messages:\n  wrong-password: ''\n");
        List<String> warnings = new ArrayList<>();

        LoginSeams.warnAboutLeftovers(file, warnings::add, ENGLISH);

        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("no warning for the same file with only the removed key taken out")
    void silentWithoutTheKey(@TempDir File dir) throws IOException {
        File file = write(dir, FILE_WITHOUT_THE_REMOVED_KEY);
        List<String> warnings = new ArrayList<>();

        LoginSeams.warnAboutLeftovers(file, warnings::add, ENGLISH);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("no warning, and no exception, for a missing file or no file at all")
    void silentWithoutAFile(@TempDir File dir) {
        List<String> warnings = new ArrayList<>();

        LoginSeams.warnAboutLeftovers(new File(dir, "absent.yml"), warnings::add, ENGLISH);
        LoginSeams.warnAboutLeftovers(null, warnings::add, ENGLISH);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("every key the check knows about is one LoginConfig no longer declares")
    void knowsOnlyUndeclaredKeys() {
        List<String> declared = new ArrayList<>();
        for (java.lang.reflect.Field field : LoginConfig.class.getDeclaredFields()) {
            com.ultikits.ultitools.annotations.ConfigEntry entry =
                    field.getAnnotation(com.ultikits.ultitools.annotations.ConfigEntry.class);
            if (entry != null) {
                declared.add(entry.path());
            }
        }

        assertThat(declared).as("control: the scan sees LoginConfig's keys").isNotEmpty();
        assertThat(RemovedConfigKeys.removedKeys().keySet())
                .containsExactly("messages.wrong-password")
                .doesNotContainAnyElementsOf(declared);
    }
}
