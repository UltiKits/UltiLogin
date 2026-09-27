package com.ultikits.plugins.login.gui;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.entities.Colors;
import com.ultikits.ultitools.utils.XVersionUtils;

import mc.obliviate.inventory.InventoryAPI;

import org.bukkit.Bukkit;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiLogin#25: the registration window's title follows its stage.
 * <p>
 * {@code Gui#setTitle} only stores the new title (measured: its body is one {@code putfield}); the
 * window a player already has open keeps the title it was created with. The page therefore re-opens
 * itself with the new title. These tests drive the real GUI library ({@link InventoryAPI#init()} over
 * a MockBukkit server) by clicking the keypad, and read the title of the view the player actually has
 * open, so a title stored but never shown cannot pass.
 */
@DisplayName("RegisterGUIPage title follows the stage (UltiKits/UltiLogin#25)")
class RegisterGUIPageTitleTest {

    /** Keypad raw slots for the digits 1, 2, 3 and 4. */
    private static final int[] DIGITS_1234 = {10, 11, 12, 19};
    private static final int[] DIGITS_1235 = {10, 11, 12, 20};

    private ServerMock server;
    private InventoryAPI inventoryApi;
    private LoginService loginService;
    private LoginConfig config;
    private PlayerMock player;
    private MockedStatic<XVersionUtils> xVersion;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
        UltiLoginTestHelper.bootstrapLiveServer();
        server = MockBukkit.getMock();
        // The keypad paints glass panes and wool through the framework's XVersionUtils, whose XSeries
        // dependency is provided by the server at runtime and absent from this test classpath.
        xVersion = mockStatic(XVersionUtils.class);
        xVersion.when(() -> XVersionUtils.getColoredPlaneGlass(any(Colors.class)))
                .thenAnswer(inv -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        xVersion.when(() -> XVersionUtils.getColoredWool(any(Colors.class)))
                .thenAnswer(inv -> new ItemStack(Material.WHITE_WOOL));
        inventoryApi = new InventoryAPI(MockBukkit.createMockPlugin("ObliviateHost"));
        inventoryApi.init();

        loginService = mock(LoginService.class);
        config = UltiLoginTestHelper.createDefaultConfig();
        lenient().when(loginService.getConfig()).thenReturn(config);
        lenient().when(config.getGuiRegisterTitle()).thenReturn("&6Set Password");
        lenient().when(config.getGuiConfirmTitle()).thenReturn("&6Confirm Password");
        lenient().when(config.getGuiPasswordLength()).thenReturn(4);
        lenient().when(config.getPasswordMismatch()).thenReturn("&cmismatch");
        lenient().when(config.getRegisterSuccess()).thenReturn("&aregistered");

        // The transition marker, kept the way LoginService keeps it, so this page's onClose sees a
        // deliberate re-open for what it is.
        java.util.Set<UUID> transitioning = java.util.concurrent.ConcurrentHashMap.newKeySet();
        lenient().doAnswer(inv -> transitioning.add(inv.getArgument(0)))
                .when(loginService).beginCredentialGuiTransition(any(UUID.class));
        lenient().doAnswer(inv -> transitioning.remove(inv.getArgument(0)))
                .when(loginService).endCredentialGuiTransition(any(UUID.class));
        lenient().when(loginService.isCredentialGuiTransitioning(any(UUID.class)))
                .thenAnswer(inv -> transitioning.contains(inv.getArgument(0)));

        player = server.addPlayer("Newcomer");
    }

    @AfterEach
    void tearDown() throws Exception {
        HandlerList.unregisterAll(inventoryApi.getListener());
        xVersion.close();
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    private RegisterGUIPage openPage() {
        RegisterGUIPage page = new RegisterGUIPage(player, UltiLoginTestHelper.getMockPlugin(), loginService);
        page.open();
        return page;
    }

    private void type(int... rawSlots) {
        for (int rawSlot : rawSlots) {
            InventoryView view = player.getOpenInventory();
            Bukkit.getPluginManager().callEvent(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                    rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        }
        // The page moves on five ticks after the last digit; let every scheduled step run.
        server.getScheduler().performTicks(10);
    }

    private String openTitle() {
        return player.getOpenInventory().getTitle();
    }

    @Test
    @DisplayName("POSITIVE CONTROL: the window opens with the registration title")
    void opensWithTheRegistrationTitle() {
        openPage();

        assertThat(openTitle()).isEqualTo("§6Set Password");
    }

    @Test
    @DisplayName("After the first password the open window shows the confirmation title")
    void confirmationStageShowsTheConfirmationTitle() {
        RegisterGUIPage page = openPage();

        type(DIGITS_1234);

        assertThat(openTitle()).isEqualTo("§6Confirm Password");
        assertThat(player.getOpenInventory().getTopInventory())
                .as("the window shown is this page's own inventory")
                .isSameAs(page.getInventory());
        verify(loginService, never()).registerCredentialGuiReopenTask(any(UUID.class), any());
    }

    @Test
    @DisplayName("Re-opening keeps the stage: the same password again registers it")
    void reopeningKeepsTheEnteredPassword() {
        when(loginService.register(any(Player.class), anyString())).thenReturn(true);
        openPage();

        type(DIGITS_1234);
        type(DIGITS_1234);

        verify(loginService).register(player, "1234");
    }

    @Test
    @DisplayName("A mismatch returns the open window to the registration title")
    void mismatchReturnsToTheRegistrationTitle() {
        openPage();

        type(DIGITS_1234);
        type(DIGITS_1235);

        assertThat(openTitle()).isEqualTo("§6Set Password");
        verify(loginService, never()).register(any(Player.class), anyString());
    }
}
