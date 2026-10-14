package com.ultikits.plugins.login.gui;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.entities.Colors;
import com.ultikits.ultitools.utils.XVersionUtils;

import mc.obliviate.inventory.InventoryAPI;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

/**
 * The login keypad's masked password display (UAT row {@code ultilogin.gui.login-page}).
 * <p>
 * The display fills one glyph per digit. The frame with every glyph filled is shown only for the five ticks between
 * the last digit and the automatic submit; on a real server that frame is not something a tester can capture while
 * the throwaway-password helper is clicking (Phase 19 UAT, B31), so this test holds it. It drives the real GUI library
 * over a MockBukkit server by clicking the keypad, as {@link RegisterGUIPageTitleTest} does, and reads the display
 * item the player actually has in the open window.
 */
@DisplayName("LoginGUIPage masked display fills one glyph per digit before the automatic submit")
class LoginGUIPageDisplayTest {

    private static final int DISPLAY_SLOT = 4;
    /** Keypad raw slots for the digits 1, 2, 3 and 4. */
    private static final int[] DIGITS_1234 = {10, 11, 12, 19};

    private ServerMock server;
    private InventoryAPI inventoryApi;
    private LoginService loginService;
    private PlayerMock player;
    private MockedStatic<XVersionUtils> xVersion;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
        UltiLoginTestHelper.bootstrapLiveServer();
        server = MockBukkit.getMock();
        xVersion = mockStatic(XVersionUtils.class);
        xVersion.when(() -> XVersionUtils.getColoredPlaneGlass(any(Colors.class)))
                .thenAnswer(inv -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        xVersion.when(() -> XVersionUtils.getColoredWool(any(Colors.class)))
                .thenAnswer(inv -> new ItemStack(Material.WHITE_WOOL));
        inventoryApi = new InventoryAPI(MockBukkit.createMockPlugin("ObliviateHost"));
        inventoryApi.init();

        loginService = mock(LoginService.class);
        LoginConfig config = UltiLoginTestHelper.createDefaultConfig();
        lenient().when(loginService.getConfig()).thenReturn(config);
        lenient().when(config.getGuiLoginTitle()).thenReturn("&6Enter Password");
        lenient().when(config.getGuiPasswordLength()).thenReturn(4);
        lenient().when(loginService.login(any(Player.class), anyString()))
                .thenReturn(new LoginService.LoginResult(true, "&alogged in"));
        lenient().when(loginService.isCredentialGuiTransitioning(any(UUID.class))).thenReturn(false);

        player = server.addPlayer("Returning");
    }

    @AfterEach
    void tearDown() throws Exception {
        HandlerList.unregisterAll(inventoryApi.getListener());
        xVersion.close();
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    private void click(int... rawSlots) {
        for (int rawSlot : rawSlots) {
            InventoryView view = player.getOpenInventory();
            Bukkit.getPluginManager().callEvent(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                    rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        }
    }

    private String maskedLine() {
        ItemStack display = player.getOpenInventory().getTopInventory().getItem(DISPLAY_SLOT);
        assertThat(display).as("display item in slot %d", DISPLAY_SLOT).isNotNull();
        List<String> lore = display.getItemMeta().getLore();
        assertThat(lore).isNotNull();
        return ChatColor.stripColor(lore.get(1));
    }

    @Test
    @DisplayName("POSITIVE CONTROL: one filled glyph per digit while digits are entered")
    void fillsOneGlyphPerDigit() {
        new LoginGUIPage(player, UltiLoginTestHelper.getMockPlugin(), loginService).open();
        click(10);
        assertThat(maskedLine()).isEqualTo("● ○ ○ ○");
        click(11, 12);
        assertThat(maskedLine()).isEqualTo("● ● ● ○");
    }

    @Test
    @DisplayName("After the fourth digit every glyph is filled, and the login is not attempted until the delay passes")
    void showsAllFilledBeforeTheAutomaticSubmit() {
        new LoginGUIPage(player, UltiLoginTestHelper.getMockPlugin(), loginService).open();
        click(DIGITS_1234);

        assertThat(maskedLine()).isEqualTo("● ● ● ●");
        verify(loginService, never()).login(any(Player.class), anyString());

        server.getScheduler().performTicks(10);
        verify(loginService).login(player, "1234");
    }
}
