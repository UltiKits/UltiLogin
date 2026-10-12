package com.ultikits.plugins.login.logging;

import com.ultikits.plugins.login.logging.Log4jTestSupport.CapturingAppender;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.MarkerFilter;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * The filter on Log4j's root logger configuration keeps a password command line out of
 * every appender of that configuration -- Paper's console and {@code latest.log}, and UltiTools-API
 * 6.3.0's console mirror to the panel log stream -- and gives them a masked copy instead.
 */
@DisplayName("Log4jPasswordFilter")
class Log4jPasswordFilterTest {

    /** The logger Paper 1.21.11 writes "{} issued server command: {}" through. */
    private static final String PAPER_COMMAND_LOGGER = "net.minecraft.server.network.ServerGamePacketListenerImpl";

    private static final String FAKE_PASSWORD = "fake-pw-not-real-1";

    @Nested
    @DisplayName("on a root logger configuration shaped like Paper's")
    class OnRootConfiguration {

        private LoggerContext context;

        private CapturingAppender console;

        private CapturingAppender mirror;

        private Logger commandLogger;

        @BeforeEach
        void setUp() {
            context = new LoggerContext("password-filter-test");
            context.start();
            LoggerConfig root = context.getConfiguration().getRootLogger();
            for (String name : new ArrayList<>(root.getAppenders().keySet())) {
                root.removeAppender(name);
            }
            root.setLevel(Level.INFO);
            // Paper's own root filter, kept in front of ours as on a real server.
            root.addFilter(MarkerFilter.createFilter("NETWORK_PACKETS", Filter.Result.DENY, Filter.Result.NEUTRAL));
            console = new CapturingAppender("console");
            mirror = new CapturingAppender("UltiToolsConsoleMirror");
            root.addAppender(console, null, null);
            root.addAppender(mirror, null, null);
            Log4jPasswordFilter filter = new Log4jPasswordFilter(PasswordCommandMask.forModuleCommands(), root);
            filter.start();
            root.addFilter(filter);
            context.updateLoggers();
            commandLogger = context.getLogger(PAPER_COMMAND_LOGGER);
        }

        @AfterEach
        void tearDown() {
            context.stop();
        }

        @Test
        @DisplayName("Paper's /login line reaches the console and the mirror masked, never in plain text")
        void masksTheLoginLine() {
            commandLogger.info("{} issued server command: {}", "Steve", "/login " + FAKE_PASSWORD);

            String expected = "Steve issued server command: /login " + PasswordCommandMask.MASK;
            assertThat(console.messages()).containsExactly(expected);
            assertThat(mirror.messages()).containsExactly(expected);
        }

        @Test
        @DisplayName("namespaced, mixed-case and multi-argument forms are masked too")
        void masksOtherForms() {
            commandLogger.info("{} issued server command: {}", "Steve", "/UltiLogin:REGISTER " + FAKE_PASSWORD + " " + FAKE_PASSWORD);
            commandLogger.info("{} issued server command: {}", "Steve", "/cpw old-" + FAKE_PASSWORD + " " + FAKE_PASSWORD + " " + FAKE_PASSWORD);
            commandLogger.info("{} issued server command: {}", "Admin", "/logadmin reset Steve " + FAKE_PASSWORD);

            assertThat(console.messages()).containsExactly(
                    "Steve issued server command: /UltiLogin:REGISTER " + PasswordCommandMask.MASK,
                    "Steve issued server command: /cpw " + PasswordCommandMask.MASK,
                    "Admin issued server command: /logadmin reset " + PasswordCommandMask.MASK);
            assertThat(String.join("\n", mirror.messages())).doesNotContain(FAKE_PASSWORD);
        }

        @Test
        @DisplayName("a line logged as one pre-formatted string is masked as well")
        void masksAPreformattedLine() {
            commandLogger.info("Steve issued server command: /l " + FAKE_PASSWORD);

            assertThat(console.messages()).containsExactly("Steve issued server command: /l " + PasswordCommandMask.MASK);
        }

        @Test
        @DisplayName("the masked copy keeps the original's logger, level and thread")
        void keepsTheEventShape() {
            commandLogger.warn("{} issued server command: {}", "Steve", "/login " + FAKE_PASSWORD);

            LogEvent event = console.events().get(0);
            assertThat(event.getLoggerName()).isEqualTo(PAPER_COMMAND_LOGGER);
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThreadName()).isEqualTo(Thread.currentThread().getName());
        }

        @Test
        @DisplayName("POSITIVE CONTROL: an ordinary command line still reaches both outputs unchanged")
        void leavesOrdinaryCommands() {
            commandLogger.info("{} issued server command: {}", "Steve", "/list");
            commandLogger.info("{} issued server command: {}", "Steve", "/login");
            context.getLogger("Minecraft").info("Steve joined the game");

            assertThat(console.messages()).containsExactly(
                    "Steve issued server command: /list",
                    "Steve issued server command: /login",
                    "Steve joined the game");
            assertThat(mirror.messages()).isEqualTo(console.messages());
        }

        @Test
        @DisplayName("Paper's own NETWORK_PACKETS filter keeps working next to this one")
        void keepsPapersFilter() {
            commandLogger.info(MarkerManager.getMarker("NETWORK_PACKETS"), "packet noise");

            assertThat(console.messages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("edge cases of filter(LogEvent)")
    class Edges {

        private final LoggerConfig owner = mock(LoggerConfig.class);

        private final Log4jPasswordFilter filter =
                new Log4jPasswordFilter(PasswordCommandMask.forModuleCommands(), owner);

        @Test
        @DisplayName("an event without a message, or no event, is neutral")
        void noMessage() {
            assertThat(filter.filter((LogEvent) null)).isEqualTo(Filter.Result.NEUTRAL);
            assertThat(filter.filter(new Log4jLogEvent.Builder().setLoggerName("x").build()))
                    .isEqualTo(Filter.Result.NEUTRAL);
        }

        @Test
        @DisplayName("fails closed: if the masked copy cannot be logged, the original is still denied")
        void failsClosed() {
            doThrow(new IllegalStateException("appender broke")).when(owner).log(any(LogEvent.class));
            LogEvent event = new Log4jLogEvent.Builder()
                    .setLoggerName(PAPER_COMMAND_LOGGER)
                    .setLevel(Level.INFO)
                    .setMessage(new SimpleMessage("Steve issued server command: /login " + FAKE_PASSWORD))
                    .build();

            assertThat(filter.filter(event)).isEqualTo(Filter.Result.DENY);
        }
    }

    @Nested
    @DisplayName("install and uninstall on this JVM's Log4j context")
    class InstallOnTheServerContext {

        @AfterEach
        void tearDown() {
            Log4jPasswordFilter.uninstall();
            Log4jPasswordFilter.removeFiltersOfThisClass(Log4jTestSupport.root());
        }

        @Test
        @DisplayName("install puts exactly one filter on the root configuration; uninstall removes it")
        void installAndUninstall() {
            assertThat(Log4jPasswordFilter.install(PasswordCommandMask.forModuleCommands())).isNull();
            assertThat(Log4jTestSupport.passwordFiltersOnRoot()).isEqualTo(1);
            assertThat(Log4jPasswordFilter.isInstalled()).isTrue();

            Log4jPasswordFilter.uninstall();

            assertThat(Log4jTestSupport.passwordFiltersOnRoot()).isZero();
            assertThat(Log4jPasswordFilter.isInstalled()).isFalse();
        }

        @Test
        @DisplayName("installing again (a re-enable) never stacks a second filter")
        void noStacking() {
            Log4jPasswordFilter.install(PasswordCommandMask.forModuleCommands());
            Log4jPasswordFilter.install(PasswordCommandMask.forModuleCommands());
            Log4jPasswordFilter.install(PasswordCommandMask.forModuleCommands());

            assertThat(Log4jTestSupport.passwordFiltersOnRoot()).isEqualTo(1);
        }

        @Test
        @DisplayName("a filter left behind by an earlier load whose disable never ran is replaced, not kept")
        void replacesALeftover() {
            LoggerConfig root = Log4jTestSupport.root();
            Log4jPasswordFilter leftover = new Log4jPasswordFilter(PasswordCommandMask.forModuleCommands(), root);
            root.addFilter(leftover);

            Log4jPasswordFilter.install(PasswordCommandMask.forModuleCommands());

            assertThat(Log4jTestSupport.passwordFiltersOnRoot()).isEqualTo(1);
            assertThat(Log4jTestSupport.filters(root)).doesNotContain(leftover);
        }

        @Test
        @DisplayName("other filters on the root configuration survive install and uninstall")
        void keepsOtherFilters() {
            LoggerConfig root = Log4jTestSupport.root();
            Filter papers = MarkerFilter.createFilter("NETWORK_PACKETS", Filter.Result.DENY, Filter.Result.NEUTRAL);
            root.addFilter(papers);
            try {
                Log4jPasswordFilter.install(PasswordCommandMask.forModuleCommands());
                assertThat(Log4jTestSupport.filters(root)).contains(papers);
                Log4jPasswordFilter.uninstall();
                assertThat(Log4jTestSupport.filters(root)).contains(papers);
            } finally {
                root.removeFilter(papers);
            }
        }

        @Test
        @DisplayName("uninstall without an install does nothing")
        void uninstallAlone() {
            Log4jPasswordFilter.uninstall();

            assertThat(Log4jTestSupport.passwordFiltersOnRoot()).isZero();
        }

        @Test
        @DisplayName("once installed, a password line logged through LogManager reaches an appender masked")
        void masksThroughTheRealContext() {
            LoggerConfig root = Log4jTestSupport.root();
            Level previous = root.getLevel();
            CapturingAppender appender = new CapturingAppender("password-filter-capture");
            root.setLevel(Level.INFO);
            root.addAppender(appender, null, null);
            Log4jTestSupport.context().updateLoggers();
            try {
                Log4jPasswordFilter.install(PasswordCommandMask.forModuleCommands());

                LogManager.getLogger(PAPER_COMMAND_LOGGER)
                        .info("{} issued server command: {}", "Steve", "/login " + FAKE_PASSWORD);

                assertThat(appender.messages())
                        .containsExactly("Steve issued server command: /login " + PasswordCommandMask.MASK);
            } finally {
                root.removeAppender(appender.getName());
                root.setLevel(previous);
                Log4jTestSupport.context().updateLoggers();
            }
        }
    }
}
