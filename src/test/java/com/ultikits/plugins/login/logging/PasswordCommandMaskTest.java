package com.ultikits.plugins.login.logging;

import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paper logs every player command line, so the arguments of this module's password
 * commands must be masked. These tests hold the mask itself; {@code Log4jPasswordFilterTest} holds
 * its application to real Log4j events. Every password here is an obvious fake.
 */
@DisplayName("PasswordCommandMask")
class PasswordCommandMaskTest {

    /**
     * Parameter names that are not secret. A parameter name in neither this list nor {@link
     * PasswordCommandMask#SECRET_PARAMETERS} fails {@link Coverage#everyParameterNameIsClassified}.
     * {@code arg} is {@code /regs <arg>}: an email address or the email-binding code, neither a password.
     */
    private static final Set<String> NOT_SECRET = new HashSet<>(Arrays.asList("player", "arg"));

    private final PasswordCommandMask mask = PasswordCommandMask.forModuleCommands();

    @Nested
    @DisplayName("derived from the command declarations")
    class Coverage {

        @Test
        @DisplayName("MODULE_COMMANDS lists every @CmdExecutor class compiled into the module")
        void listsEveryCommandClass() throws Exception {
            assertThat(PasswordCommandMask.MODULE_COMMANDS)
                    .containsExactlyInAnyOrderElementsOf(compiledCommandClasses());
        }

        @Test
        @DisplayName("POSITIVE CONTROL: the class scan finds the module's eight command classes")
        void scanFindsTheCommands() throws Exception {
            assertThat(compiledCommandClasses()).hasSize(8);
        }

        @Test
        @DisplayName("every @CmdMapping parameter name is classified as secret or not secret")
        void everyParameterNameIsClassified() {
            List<String> unclassified = new ArrayList<>();
            for (String name : allParameterNames()) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (!PasswordCommandMask.SECRET_PARAMETERS.contains(lower) && !NOT_SECRET.contains(lower)) {
                    unclassified.add(name);
                }
            }
            assertThat(unclassified)
                    .as("add each name to PasswordCommandMask.SECRET_PARAMETERS or to this test's NOT_SECRET")
                    .isEmpty();
        }

        @Test
        @DisplayName("the derived labels are exactly the password commands and all their aliases")
        void derivedLabels() {
            assertThat(mask.labels()).containsExactlyInAnyOrder(
                    "login", "l",
                    "register", "reg",
                    "changepassword", "changepw", "cpw",
                    "setpassword", "setpw",
                    "recover",
                    "logadmin", "loginadmin");
        }

        @Test
        @DisplayName("for every alias of every mapping with a secret parameter, no secret value survives")
        void noSecretSurvivesAnyMapping() {
            int checked = 0;
            for (Class<?> commandClass : PasswordCommandMask.MODULE_COMMANDS) {
                CmdExecutor executor = commandClass.getAnnotation(CmdExecutor.class);
                for (Method method : commandClass.getDeclaredMethods()) {
                    CmdMapping mapping = method.getAnnotation(CmdMapping.class);
                    if (mapping == null) {
                        continue;
                    }
                    List<String> secrets = new ArrayList<>();
                    String arguments = sampleArguments(mapping.format(), secrets);
                    if (secrets.isEmpty()) {
                        continue;
                    }
                    for (String alias : executor.alias()) {
                        for (String label : Arrays.asList(alias, alias.toUpperCase(Locale.ROOT), "ultitools:" + alias)) {
                            String masked = mask.maskCommandLine("/" + label + " " + arguments);
                            assertThat(masked).as("/%s %s", label, arguments).isNotNull().startsWith("/" + label + " ");
                            for (String secret : secrets) {
                                assertThat(masked).doesNotContain(secret);
                            }
                            checked++;
                        }
                    }
                }
            }
            // Positive control that the loop ran: six mappings carry a secret (login, register,
            // changepassword, setpassword, recover <code> <password> <confirm>, logadmin reset <player>
            // <password>), 12 aliases between them, three label forms each. More aliases only add.
            assertThat(checked).isGreaterThanOrEqualTo(36);
        }
    }

    @Nested
    @DisplayName("masking")
    class Masking {

        @ParameterizedTest
        @ValueSource(strings = {
                "/login fake-pw-1",
                "/l fake-pw-1",
                "/LOGIN fake-pw-1",
                "/LoGiN fake-pw-1",
                "/ultilogin:login fake-pw-1",
                "/ultitools:login fake-pw-1",
                "/ultitools:setpw fake-pw-1 fake-pw-1",
                "/UltiLogin:L fake-pw-1",
                "/register fake-pw-1 fake-pw-1",
                "/reg fake-pw-1 fake-pw-1",
                "/changepassword fake-old-1 fake-pw-1 fake-pw-1",
                "/changepw fake-old-1 fake-pw-1 fake-pw-1",
                "/cpw fake-old-1 fake-pw-1 fake-pw-1",
                "/setpassword fake-pw-1 fake-pw-1",
                "/setpw fake-pw-1 fake-pw-1",
                "/recover 123456 fake-pw-1 fake-pw-1",
                "/login   fake-pw-1   extra"
        })
        @DisplayName("masks every argument of a password command, keeping the label as typed")
        void masksAllArguments(String line) {
            String label = line.substring(0, line.indexOf(' '));
            assertThat(mask.maskCommandLine(line)).isEqualTo(label + " " + PasswordCommandMask.MASK);
        }

        @Test
        @DisplayName("logadmin reset keeps the sub-command readable and masks the player and password")
        void logadminReset() {
            assertThat(mask.maskCommandLine("/logadmin reset Steve fake-pw-1"))
                    .isEqualTo("/logadmin reset " + PasswordCommandMask.MASK);
            assertThat(mask.maskCommandLine("/loginadmin RESET Steve fake-pw-1"))
                    .isEqualTo("/loginadmin RESET " + PasswordCommandMask.MASK);
            assertThat(mask.maskCommandLine("/ultilogin:logadmin reset Steve"))
                    .isEqualTo("/ultilogin:logadmin reset " + PasswordCommandMask.MASK);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/logadmin info Steve",
                "/logadmin unregister Steve",
                "/logadmin forcelogin Steve",
                "/logadmin reset",
                "/logadmin"
        })
        @DisplayName("logadmin sub-commands without a password are left alone")
        void logadminOthers(String line) {
            assertThat(mask.maskCommandLine(line)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/list",
                "/lp user Steve info",
                "/msg Steve hello",
                "/regs someone@example.com",
                "/regs 123456",
                "/panel",
                "/login",
                "/l",
                "/register",
                "/logins fake-pw-1",
                "/other:list fake",
                "",
                "/"
        })
        @DisplayName("lines that are not password-bearing are left alone")
        void leavesOthersAlone(String line) {
            assertThat(mask.maskCommandLine(line)).isNull();
        }

        @Test
        @DisplayName("a mistyped extra leading slash does not hide a password from the mask")
        void extraLeadingSlash() {
            assertThat(mask.maskCommandLine("//login fake-pw-1")).isEqualTo("//login " + PasswordCommandMask.MASK);
            assertThat(mask.maskCommandLine("login fake-pw-1")).isEqualTo("login " + PasswordCommandMask.MASK);
        }

        @Test
        @DisplayName("an already-masked line is left alone, so a masked copy is never masked again")
        void alreadyMasked() {
            assertThat(mask.maskCommandLine("/login " + PasswordCommandMask.MASK)).isNull();
            assertThat(mask.maskCommandLine("/logadmin reset " + PasswordCommandMask.MASK)).isNull();
        }

        @Test
        @DisplayName("null is left alone")
        void nullLine() {
            assertThat(mask.maskCommandLine(null)).isNull();
            assertThat(mask.maskLogLine(null)).isNull();
        }

        @Test
        @DisplayName("Paper's full log line keeps the player and command name, and loses the password")
        void maskLogLine() {
            assertThat(mask.maskLogLine("Steve issued server command: /login fake-pw-1"))
                    .isEqualTo("Steve issued server command: /login " + PasswordCommandMask.MASK);
            assertThat(mask.maskLogLine("Steve issued server command: /list")).isNull();
            assertThat(mask.maskLogLine("Steve joined the game")).isNull();
            assertThat(mask.maskLogLine("Steve issued server command: /login " + PasswordCommandMask.MASK)).isNull();
        }
    }

    @Nested
    @DisplayName("derivation details")
    class Derivation {

        @Test
        @DisplayName("parameter names drop brackets and a trailing ...")
        void parameterNames() {
            assertThat(PasswordCommandMask.parameterNames("reset <player> [password] <message...>"))
                    .containsExactly("player", "password", "message");
            assertThat(PasswordCommandMask.parameterNames("")).isEmpty();
            assertThat(PasswordCommandMask.parameterNames(null)).isEmpty();
        }

        @Test
        @DisplayName("a class without @CmdExecutor contributes nothing")
        void classWithoutExecutor() {
            assertThat(PasswordCommandMask.fromCommandClasses(Arrays.<Class<?>>asList(String.class)).labels())
                    .isEmpty();
        }

        @Test
        @DisplayName("a mapping without a secret parameter contributes nothing")
        void mappingWithoutSecret() {
            PasswordCommandMask onlyPanel = PasswordCommandMask.fromCommandClasses(
                    Arrays.<Class<?>>asList(com.ultikits.plugins.login.commands.PanelCommand.class,
                            com.ultikits.plugins.login.commands.EmailBindCommand.class));
            assertThat(onlyPanel.labels()).isEmpty();
        }
    }

    /** Sample arguments for a format: literals as written, each parameter a distinct fake value. */
    private static String sampleArguments(String format, List<String> secrets) {
        List<String> words = new ArrayList<>();
        int index = 0;
        for (String token : format.trim().isEmpty() ? new String[0] : format.trim().split("\\s+")) {
            if (token.startsWith("<") || token.startsWith("[")) {
                String name = PasswordCommandMask.parameterNames(token).get(0);
                String value = "fake-" + name + "-" + index++;
                if (PasswordCommandMask.SECRET_PARAMETERS.contains(name.toLowerCase(Locale.ROOT))) {
                    secrets.add(value);
                }
                words.add(value);
            } else {
                words.add(token);
            }
        }
        return String.join(" ", words);
    }

    private static List<String> allParameterNames() {
        List<String> names = new ArrayList<>();
        for (Class<?> commandClass : PasswordCommandMask.MODULE_COMMANDS) {
            for (Method method : commandClass.getDeclaredMethods()) {
                CmdMapping mapping = method.getAnnotation(CmdMapping.class);
                if (mapping != null) {
                    names.addAll(PasswordCommandMask.parameterNames(mapping.format()));
                }
            }
        }
        return names;
    }

    /** Every class under the module's package in its compiled output that carries @CmdExecutor. */
    private static List<Class<?>> compiledCommandClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        Path classesRoot = Paths.get(PasswordCommandMask.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path packageRoot = classesRoot.resolve("com/ultikits/plugins/login");
        List<String> classNames;
        try (Stream<Path> files = Files.walk(packageRoot)) {
            classNames = files
                    .filter(p -> p.toString().endsWith(".class"))
                    .map(p -> classesRoot.relativize(p).toString())
                    .map(n -> n.substring(0, n.length() - ".class".length()).replace('/', '.').replace('\\', '.'))
                    .collect(Collectors.toList());
        }
        List<Class<?>> commands = new ArrayList<>();
        for (String name : classNames) {
            Class<?> type = Class.forName(name, false, PasswordCommandMask.class.getClassLoader());
            if (type.isAnnotationPresent(CmdExecutor.class)) {
                commands.add(type);
            }
        }
        return commands;
    }
}
