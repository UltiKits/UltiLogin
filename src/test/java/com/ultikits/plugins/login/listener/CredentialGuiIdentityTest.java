package com.ultikits.plugins.login.listener;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.gui.LoginGUIPage;
import com.ultikits.plugins.login.gui.RegisterGUIPage;
import com.ultikits.plugins.login.service.LoginService;

import com.ultikits.ultitools.entities.Colors;
import com.ultikits.ultitools.utils.XVersionUtils;

import mc.obliviate.inventory.InventoryAPI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The credential GUI is recognised by what it IS, not by what its title says
 * (<a href="https://github.com/UltiKits/UltiLogin/issues/35">UltiLogin#35</a>).
 *
 * <h2>The defect</h2>
 * The allowance for an unauthenticated player's inventory clicks and opens was decided from the
 * view's title. A view's title covers the whole view, including the player's own inventory rows, so
 * while the credential GUI was open every click in the player's own rows was allowed and they could
 * rearrange, merge or split their stacks. The same title test also let any other inventory whose title
 * reads like a credential title be opened and clicked.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * The GUI library is real ({@link InventoryAPI#init()} over a MockBukkit server), the credential page
 * is the module's own {@link LoginGUIPage}/{@link RegisterGUIPage} opened through {@code Gui#open()},
 * and every event is built over the player's real open view with a real raw slot, so which inventory a
 * click lands in is decided by Bukkit's own slot mapping. Each refusal is paired with an allowance on
 * the same view (a click in the GUI itself) so "cancelled" cannot come from a guard that refuses
 * everything.
 */
@DisplayName("Credential GUI allowance decided by identity and the top inventory (UltiLogin#35)")
class CredentialGuiIdentityTest {

    /** Raw slot 10 is the keypad's "1" in the 54-slot credential GUI. */
    private static final int KEYPAD_RAW_SLOT = 10;
    /** A 6-row top inventory has raw slots 0-53; raw slot 60 is in the player's own rows. */
    private static final int OWN_ROWS_RAW_SLOT = 60;

    private InventoryAPI inventoryApi;
    private LoginService loginService;
    private LoginConfig config;
    private LoginProtectionListener listener;
    private PlayerMock player;
    private MockedStatic<XVersionUtils> xVersion;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
        UltiLoginTestHelper.bootstrapLiveServer();
        // The pages' onOpen paints a glass-pane background through the framework's XVersionUtils,
        // whose XSeries dependency is provided by the server at runtime and absent from this test
        // classpath; the background is not what this test is about.
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
        lenient().when(config.getGuiLoginTitle()).thenReturn("&6Enter Password");
        lenient().when(config.getGuiRegisterTitle()).thenReturn("&6Set Password");
        lenient().when(config.getGuiConfirmTitle()).thenReturn("&6Confirm Password");
        lenient().when(config.getGuiPasswordLength()).thenReturn(4);

        listener = new LoginProtectionListener(UltiLoginTestHelper.getMockPlugin(), loginService);
        player = MockBukkit.getMock().addPlayer("Unauthenticated");
        lenient().when(loginService.isLoggedIn(player.getUniqueId())).thenReturn(false);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
        player.getInventory().setItem(9, new ItemStack(Material.OAK_LOG, 16));
    }

    @AfterEach
    void tearDown() throws Exception {
        HandlerList.unregisterAll(inventoryApi.getListener());
        xVersion.close();
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    private LoginGUIPage openLoginPage() {
        LoginGUIPage page = new LoginGUIPage(player, UltiLoginTestHelper.getMockPlugin(), loginService);
        page.open();
        return page;
    }

    private InventoryClickEvent clickAt(int rawSlot) {
        InventoryView view = player.getOpenInventory();
        InventoryType.SlotType type = rawSlot < view.getTopInventory().getSize()
                ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR;
        return new InventoryClickEvent(view, type, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    @Test
    @DisplayName("A click in the player's own rows while the credential GUI is open is refused")
    void ownRowsRefused() {
        openLoginPage();
        InventoryClickEvent ownRows = clickAt(OWN_ROWS_RAW_SLOT);
        assertThat(ownRows.getClickedInventory())
                .as("precondition: raw slot %d is in the player's own inventory", OWN_ROWS_RAW_SLOT)
                .isSameAs(player.getOpenInventory().getBottomInventory());

        listener.onInventoryClick(ownRows);

        assertThat(ownRows.isCancelled()).as("the player's own rows stay refused").isTrue();
    }

    @Test
    @DisplayName("Control: a click on the credential GUI's own keypad is allowed")
    void keypadAllowed() {
        openLoginPage();
        InventoryClickEvent keypad = clickAt(KEYPAD_RAW_SLOT);

        listener.onInventoryClick(keypad);

        assertThat(keypad.isCancelled()).as("the keypad still takes digits").isFalse();
    }

    @Test
    @DisplayName("The register page is recognised the same way")
    void registerPageRecognised() {
        new RegisterGUIPage(player, UltiLoginTestHelper.getMockPlugin(), loginService).open();
        InventoryClickEvent keypad = clickAt(KEYPAD_RAW_SLOT);
        InventoryClickEvent ownRows = clickAt(OWN_ROWS_RAW_SLOT);

        listener.onInventoryClick(keypad);
        listener.onInventoryClick(ownRows);

        assertThat(keypad.isCancelled()).isFalse();
        assertThat(ownRows.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("A customised title with none of the built-in words still opens and accepts digits")
    void customisedTitleNotLockedOut() {
        when(config.getGuiLoginTitle()).thenReturn("&bWelcome back, traveller");
        LoginGUIPage page = openLoginPage();
        InventoryOpenEvent open = new InventoryOpenEvent(player.getOpenInventory());
        InventoryClickEvent keypad = clickAt(KEYPAD_RAW_SLOT);

        listener.onInventoryOpen(open);
        listener.onInventoryClick(keypad);

        assertThat(page.getInventory()).as("precondition: the page is what is open")
                .isEqualTo(player.getOpenInventory().getTopInventory());
        assertThat(open.isCancelled()).as("the customised credential GUI opens").isFalse();
        assertThat(keypad.isCancelled()).as("and accepts digits").isFalse();
    }

    @Test
    @DisplayName("Another inventory titled exactly like the credential GUI is refused, open and click")
    void lookAlikeRefused() {
        Inventory chest = Bukkit.createInventory(null, 54, "§6Enter Password");
        player.openInventory(chest);
        InventoryOpenEvent open = new InventoryOpenEvent(player.getOpenInventory());
        InventoryClickEvent click = clickAt(KEYPAD_RAW_SLOT);

        listener.onInventoryOpen(open);
        listener.onInventoryClick(click);

        assertThat(open.isCancelled()).as("a chest is not the credential GUI whatever it is called").isTrue();
        assertThat(click.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("An inventory opened over a still-registered credential GUI is refused")
    void inventoryNotFromTheGuiRefused() {
        openLoginPage();
        Inventory other = Bukkit.createInventory(null, 27, "Anything");
        InventoryView view = player.getOpenInventory();
        InventoryOpenEvent open = new InventoryOpenEvent(view) {
            @Override
            public Inventory getInventory() {
                return other;
            }
        };

        listener.onInventoryOpen(open);

        assertThat(open.isCancelled()).as("only the credential GUI's own inventory may open").isTrue();
    }

    @Test
    @DisplayName("An authenticated player is unaffected")
    void authenticatedUnaffected() {
        when(loginService.isLoggedIn(player.getUniqueId())).thenReturn(true);
        Inventory chest = Bukkit.createInventory(null, 27, "Chest");
        player.openInventory(chest);
        InventoryOpenEvent open = new InventoryOpenEvent(player.getOpenInventory());
        InventoryClickEvent ownRows = clickAt(40);

        listener.onInventoryOpen(open);
        listener.onInventoryClick(ownRows);

        assertThat(open.isCancelled()).isFalse();
        assertThat(ownRows.isCancelled()).isFalse();
    }
}
