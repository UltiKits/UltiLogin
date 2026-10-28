package com.ultikits.plugins.login.logging;

import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.ultitools.websocket.SensitiveCommandRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The module's argument-taking commands, registered with UltiTools-API 6.3.1's sensitive-command
 * registry so their arguments never reach UltiCloud (D-01, D-02, D-03).
 * <p>
 * The redaction assertions drive the framework's own {@link SensitiveCommandRegistry}, not a copy of
 * its rule: the module owns only the set of names; matching and redaction are the framework's.
 */
@DisplayName("Cloud-bound redaction: the module registers its argument-taking commands")
class CloudSensitiveCommandsTest {

    private static final String[] EXPECTED = {
            "login", "l", "register", "reg", "changepassword", "changepw", "cpw",
            "setpassword", "setpw", "recover", "logadmin", "loginadmin", "regs"};

    @Nested
    @DisplayName("the derived set")
    class Names {

        @Test
        @DisplayName("is exactly the 13 aliases of the seven argument-taking commands (panel takes none)")
        void exactSet() {
            assertThat(CloudSensitiveCommands.names()).containsExactlyInAnyOrder(EXPECTED);
        }

        @Test
        @DisplayName("is unmodifiable")
        void unmodifiable() {
            Set<String> names = CloudSensitiveCommands.names();
            assertThatThrownBy(() -> names.add("panel")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("a class with a parameter-taking mapping contributes all its aliases, lower-cased")
        void parameterTakingClassContributesAliases() {
            assertThat(CloudSensitiveCommands.fromCommandClasses(
                    Arrays.<Class<?>>asList(TakesACode.class, TakesNothing.class)))
                    .containsExactlyInAnyOrder("foo", "f");
        }

        @Test
        @DisplayName("a class whose only mapping takes no parameter contributes nothing")
        void parameterlessClassContributesNothing() {
            assertThat(CloudSensitiveCommands.fromCommandClasses(
                    Arrays.<Class<?>>asList(TakesNothing.class))).isEmpty();
        }

        @Test
        @DisplayName("a class without @CmdExecutor contributes nothing")
        void classWithoutExecutor() {
            assertThat(CloudSensitiveCommands.fromCommandClasses(
                    Arrays.<Class<?>>asList(NoExecutor.class))).isEmpty();
        }
    }

    @Nested
    @DisplayName("through the framework's real registry")
    class ThroughTheRegistry {

        @Test
        @DisplayName("POSITIVE CONTROL: an empty registry redacts nothing")
        void emptyRegistryRedactsNothing() {
            SensitiveCommandRegistry registry = new SensitiveCommandRegistry();

            assertThat(registry.redactText("Steve issued server command: /login hunter2"))
                    .isEqualTo("Steve issued server command: /login hunter2");
        }

        @Test
        @DisplayName("redacts every registered command's arguments, and only those")
        void redactsRegisteredCommands() {
            SensitiveCommandRegistry registry = new SensitiveCommandRegistry();
            CloudSensitiveCommands.registerWith(registry);

            assertThat(registry.redactText("Steve issued server command: /login hunter2"))
                    .isEqualTo("Steve issued server command: /login ***");
            assertThat(registry.redactText("/ultilogin:CPW a b c")).isEqualTo("/ultilogin:CPW ***");
            assertThat(registry.redactText("/logadmin reset Steve newpass1")).isEqualTo("/logadmin ***");
            assertThat(registry.redactText("/regs a@example.com")).isEqualTo("/regs ***");
            assertThat(registry.redactText("/panel")).isEqualTo("/panel");
            assertThat(registry.redactText("/spawn home")).isEqualTo("/spawn home");
        }

        @Test
        @DisplayName("every one of the 13 labels is redacted with an argument after it")
        void everyLabelIsRedacted() {
            SensitiveCommandRegistry registry = new SensitiveCommandRegistry();
            CloudSensitiveCommands.registerWith(registry);

            for (String label : EXPECTED) {
                assertThat(registry.redactText("/" + label + " secret-value"))
                        .as(label)
                        .isEqualTo("/" + label + " ***");
            }
        }

        @Test
        @DisplayName("registering twice (a module reload) leaves exactly the derived set")
        void registeringTwiceIsHarmless() {
            SensitiveCommandRegistry registry = new SensitiveCommandRegistry();
            CloudSensitiveCommands.registerWith(registry);
            CloudSensitiveCommands.registerWith(registry);

            assertThat(registry.registeredNames()).isEqualTo(CloudSensitiveCommands.names());
        }

        @Test
        @DisplayName("a null registry does nothing")
        void nullRegistry() {
            assertThatCode(() -> CloudSensitiveCommands.registerWith(null)).doesNotThrowAnyException();
        }
    }

    @CmdExecutor(alias = {"foo", "F"}, description = "test")
    static class TakesACode {
        @CmdMapping(format = "<code>")
        public void run() {
            // test fixture
        }
    }

    @CmdExecutor(alias = {"bar"}, description = "test")
    static class TakesNothing {
        @CmdMapping(format = "")
        public void run() {
            // test fixture
        }
    }

    static class NoExecutor {
        @CmdMapping(format = "<code>")
        public void run() {
            // test fixture
        }
    }
}
