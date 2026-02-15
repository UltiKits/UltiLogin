package com.ultikits.plugins.login.commands;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.service.LoginService;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PanelCommand Tests")
class PanelCommandTest {

    private PanelCommand command;
    private LoginService loginService;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();

        // Set up Bukkit.server
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            if (serverField.get(null) == null) {
                Server mockServer = mock(Server.class);
                org.bukkit.plugin.PluginManager mockPm = mock(org.bukkit.plugin.PluginManager.class);
                when(mockServer.getPluginManager()).thenReturn(mockPm);
                when(mockPm.getPlugin("UltiTools")).thenReturn(mock(org.bukkit.plugin.Plugin.class));
                serverField.set(null, mockServer);
            }
        } catch (Exception ignored) {
        }

        loginService = mock(LoginService.class);
        command = new PanelCommand(UltiLoginTestHelper.getMockPlugin(), loginService);

        playerUuid = UUID.randomUUID();
        player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
    }

    @Nested
    @DisplayName("openPanel")
    class OpenPanel {

        @Test
        @DisplayName("Should send panel not enabled message when disabled")
        void panelNotEnabled() {
            when(loginService.isPanelEnabled()).thenReturn(false);

            command.openPanel(player);

            verify(player).sendMessage(anyString());
            verify(loginService, never()).requestPanelLink(any());
        }
    }

    @Nested
    @DisplayName("handleHelp")
    class HandleHelp {

        @Test
        @DisplayName("Should send usage message")
        void sendUsage() {
            CommandSender sender = mock(CommandSender.class);

            command.handleHelp(sender);

            verify(sender).sendMessage(contains("/panel"));
        }
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should create command successfully")
        void createCommand() {
            assertThat(command).isNotNull();
        }
    }
}
