package com.ultikits.plugins.login;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.plugins.login.listener.LoginProtectionPaperListener;
import com.ultikits.plugins.login.logging.PasswordLogFilterInstaller;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.plugins.logincompat.EquipmentSwapProtectionListener;
import com.ultikits.plugins.logincompat.MountProtectionListener;
import com.ultikits.plugins.logincompat.OpenSignProtectionListener;
import com.ultikits.plugins.logincompat.PickItemProtectionListener;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperProtectionCompatibilityTest {
    private static final String SWAP_EVENT =
            "io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent";
    private static final String CORE_LISTENER = LoginProtectionListener.class.getName();
    private static final String PAPER_LISTENER = LoginProtectionPaperListener.class.getName();
    private static final String OPTIONAL_LISTENER = EquipmentSwapProtectionListener.class.getName();
    private static final Class<?>[] OPTIONAL_LISTENERS = {
            MountProtectionListener.class, PickItemProtectionListener.class,
            OpenSignProtectionListener.class, EquipmentSwapProtectionListener.class
    };
    private UltiLogin module;
    private LoginService service;
    private LoginProtectionListener core;
    private Player player;
    private PluginLogger logger;
    private Plugin owner;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.bootstrapLiveServer();
        module = mock(UltiLogin.class);
        service = mock(LoginService.class);
        when(service.getConfig()).thenReturn(mock(LoginConfig.class));
        logger = mock(PluginLogger.class);
        owner = Bukkit.getPluginManager().getPlugin("UltiTools");
        assertThat(owner).isNotNull();
        when(module.getLogger()).thenReturn(logger);
        when(module.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        SimpleContainer context = mock(SimpleContainer.class);
        when(module.getContext()).thenReturn(context);
        when(context.getBean(LoginService.class)).thenReturn(service);
        core = spy(new LoginProtectionListener(module, service));
        when(context.getBean(LoginProtectionListener.class)).thenReturn(core);
        doCallRealMethod().when(module).registerVersionedProtections();
        doCallRealMethod().when(module).unregisterVersionedProtections();
        doCallRealMethod().when(module).registerEquipmentSwapProtection();
        doCallRealMethod().when(module).unregisterEquipmentSwapProtection();
        when(module.eventClass(anyString())).thenCallRealMethod();
        when(module.equipmentSwapEventAvailable()).thenCallRealMethod();
        when(module.createMountListener()).thenCallRealMethod();
        when(module.createLegacyMountListener()).thenCallRealMethod();
        when(module.createPickItemListener()).thenCallRealMethod();
        when(module.createOpenSignListener()).thenCallRealMethod();
        when(module.createEquipmentSwapListener()).thenCallRealMethod();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        PasswordLogFilterInstaller.uninstall();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    @Test
    void retainedHandlersRemainReflectableWhenEquipmentSwapEventIsAbsent() throws Exception {
        try (MissingEventLoader loader = new MissingEventLoader(SWAP_EVENT)) {
            Class<?> listener = Class.forName(PAPER_LISTENER, false, loader);
            assertThatCode(listener::getDeclaredMethods).doesNotThrowAnyException();
            assertThat(handlerEvents(listener)).containsExactly(PlayerRecipeBookClickEvent.class.getName());
        }
    }

    @Test
    void coreHandlersRemainReflectableWhenModernMountEventIsAbsent() throws Exception {
        String mount = UltiLogin.MODERN_MOUNT_EVENT;
        try (MissingEventLoader loader = new MissingEventLoader(mount)) {
            assertThatThrownBy(() -> Class.forName(mount, false, loader))
                    .as("negative control: the modern event really is hidden")
                    .isInstanceOf(ClassNotFoundException.class);
            Class<?> coreType = Class.forName(CORE_LISTENER, false, loader);
            assertThatCode(coreType::getDeclaredMethods)
                    .as("missing modern mount must not disable the core protection listener")
                    .doesNotThrowAnyException();
            assertThat(handlerEvents(coreType)).hasSize(22);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "io.papermc.paper.event.player.PlayerPickItemEvent",
            "io.papermc.paper.event.player.PlayerOpenSignEvent"
    })
    void recipeHandlerRemainsReflectableWhenAnotherPaperEventIsAbsent(String missing) throws Exception {
        try (MissingEventLoader loader = new MissingEventLoader(missing)) {
            assertThatThrownBy(() -> Class.forName(missing, false, loader))
                    .as("negative control: the optional event really is hidden")
                    .isInstanceOf(ClassNotFoundException.class);
            Class<?> retained = Class.forName(PAPER_LISTENER, false, loader);
            assertThatCode(retained::getDeclaredMethods)
                    .as("missing pick/open event must not disable the recipe-book handler")
                    .doesNotThrowAnyException();
            assertThat(handlerEvents(retained)).containsExactly(PlayerRecipeBookClickEvent.class.getName());
        }
    }

    @Test
    void allAbsentTypesLeaveStartupAndStableListenersReflectableWithoutLoadingFactories() throws Exception {
        try (MissingEventLoader loader = new MissingEventLoader(UltiLogin.MODERN_MOUNT_EVENT,
                UltiLogin.LEGACY_MOUNT_EVENT, UltiLogin.PICK_ITEM_EVENT, UltiLogin.OPEN_SIGN_EVENT, SWAP_EVENT)) {
            for (String name : Arrays.asList(UltiLogin.class.getName(), CORE_LISTENER, PAPER_LISTENER)) {
                Class<?> stable = Class.forName(name, false, loader);
                assertThatCode(stable::getDeclaredMethods).doesNotThrowAnyException();
                assertThatCode(stable::getDeclaredFields).doesNotThrowAnyException();
            }
            assertThat(loader.optionalRequested).isFalse();
            for (Class<?> optional : OPTIONAL_LISTENERS) {
                Class<?> isolated = Class.forName(optional.getName(), false, loader);
                assertThatThrownBy(isolated::getDeclaredMethods).isInstanceOf(NoClassDefFoundError.class);
            }
            assertThat(loader.optionalRequested).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void probesUseProvidedLoaderAndContainBothMissingClassAndLinkageError(boolean linkage) {
        for (String name : Arrays.asList(UltiLogin.MODERN_MOUNT_EVENT, UltiLogin.LEGACY_MOUNT_EVENT,
                UltiLogin.PICK_ITEM_EVENT, UltiLogin.OPEN_SIGN_EVENT, SWAP_EVENT)) {
            assertThat(UltiLogin.eventClass(name, UltiLogin.class.getClassLoader())).isNotNull();
            ClassLoader missing = new ClassLoader(UltiLogin.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String requested, boolean resolve) throws ClassNotFoundException {
                    if (name.equals(requested)) {
                        if (linkage) throw new NoClassDefFoundError("event dependency missing");
                        throw new ClassNotFoundException("test hides " + name);
                    }
                    return super.loadClass(requested, resolve);
                }
            };
            assertThat(UltiLogin.eventClass(name, missing)).isNull();
            if (SWAP_EVENT.equals(name)) {
                assertThat(UltiLogin.equipmentSwapEventAvailable(missing)).isFalse();
            }
        }
    }

    @Test
    void probesNeverInitializeAnEvent() {
        assertThat(InitializationState.initialized).isFalse();
        assertThat(UltiLogin.eventClass(InitializationProbeEvent.class.getName(),
                UltiLogin.class.getClassLoader())).isSameAs(InitializationProbeEvent.class);
        assertThat(InitializationState.initialized).isFalse();
    }

    @Test
    void optionalListenersStayOutsideAllScanRootsWithTheOriginalPriority() {
        for (Class<?> optional : OPTIONAL_LISTENERS) {
            for (String root : UltiLogin.class.getAnnotation(UltiToolsModule.class).scanBasePackages()) {
                assertThat(optional.getName()).doesNotStartWith(root + ".");
            }
            assertThat(optional.getAnnotations()).isEmpty();
            assertThat(Arrays.stream(optional.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(EventHandler.class)))
                    .singleElement().satisfies(method -> {
                        EventHandler handler = method.getAnnotation(EventHandler.class);
                        assertThat(handler.priority()).isEqualTo(EventPriority.LOWEST);
                        assertThat(handler.ignoreCancelled()).isFalse();
                    });
        }
    }

    static IntStream combinations() { return IntStream.range(0, 32); }

    /** Four mount states x two pick x two open x two equipment; dispatch, reload and unload each. */
    @ParameterizedTest(name = "capability combination {0}")
    @MethodSource("combinations")
    void startupSelectsExactlyOneMountAndOnlyAvailableHooksAcrossLifecycle(int bits) throws Exception {
        boolean modern = (bits & 1) != 0;
        boolean legacy = (bits & 2) != 0;
        boolean pick = (bits & 4) != 0;
        boolean sign = (bits & 8) != 0;
        boolean equipment = (bits & 16) != 0;
        doReturn(modern ? EntityMountEvent.class : null).when(module).eventClass(UltiLogin.MODERN_MOUNT_EVENT);
        doReturn(legacy ? org.spigotmc.event.entity.EntityMountEvent.class : null)
                .when(module).eventClass(UltiLogin.LEGACY_MOUNT_EVENT);
        doReturn(pick ? PlayerPickItemEvent.class : null).when(module).eventClass(UltiLogin.PICK_ITEM_EVENT);
        doReturn(sign ? PlayerOpenSignEvent.class : null).when(module).eventClass(UltiLogin.OPEN_SIGN_EVENT);
        when(module.equipmentSwapEventAvailable()).thenReturn(equipment);
        if (!modern) doThrow(new AssertionError("absent modern factory")).when(module).createMountListener();
        if (modern || !legacy) doThrow(new AssertionError("unselected legacy factory")).when(module).createLegacyMountListener();
        if (!pick) doThrow(new AssertionError("absent pick factory")).when(module).createPickItemListener();
        if (!sign) doThrow(new AssertionError("absent sign factory")).when(module).createOpenSignListener();
        if (!equipment) doThrow(new AssertionError("absent equipment factory")).when(module).createEquipmentSwapListener();

        Listener recipe = new LoginProtectionPaperListener(service);
        Bukkit.getPluginManager().registerEvents(core, owner);
        Bukkit.getPluginManager().registerEvents(recipe, owner);
        when(module.registerSelf()).thenCallRealMethod();
        assertThat(module.registerSelf()).isTrue();
        module.registerVersionedProtections();
        module.registerEquipmentSwapProtection();
        Event recipeEvent = paperEvent(PlayerRecipeBookClickEvent.class, player);
        assertOnlyListener(recipeEvent, recipe);
        Bukkit.getPluginManager().callEvent(recipeEvent);
        verify((Cancellable) recipeEvent).setCancelled(true);

        Map<Class<? extends Event>, Listener> selected = new LinkedHashMap<>();
        assertThat(EntityMountEvent.getHandlerList().getRegisteredListeners()).hasSize(modern ? 1 : 0);
        assertThat(org.spigotmc.event.entity.EntityMountEvent.getHandlerList().getRegisteredListeners())
                .hasSize(!modern && legacy ? 1 : 0);
        if (modern || legacy) {
            Event event = mountEvent(modern, player);
            selected.put(event.getClass(), assertOneOwnedHook(event));
            Bukkit.getPluginManager().callEvent(event);
            assertThat(((Cancellable) event).isCancelled()).isTrue();
            verify(core, times(1)).protectMount(player, (Cancellable) event);
        }
        for (Class<? extends Event> type : Arrays.<Class<? extends Event>>asList(
                PlayerPickItemEvent.class, PlayerOpenSignEvent.class, PlayerSwapWithEquipmentSlotEvent.class)) {
            boolean present = type == PlayerPickItemEvent.class ? pick : type == PlayerOpenSignEvent.class ? sign : equipment;
            Event event = paperEvent(type, player);
            assertThat(event.getHandlers().getRegisteredListeners()).hasSize(present ? 1 : 0);
            if (present) {
                selected.put(type, assertOneOwnedHook(event));
                Bukkit.getPluginManager().callEvent(event);
                verify((Cancellable) event, times(1)).setCancelled(true);
            }
        }
        doCallRealMethod().when(module).onReload();
        module.onReload();
        assertWarnings(modern || legacy, pick, sign, equipment, 1);
        verify(module, times(modern ? 1 : 0)).createMountListener();
        verify(module, times(!modern && legacy ? 1 : 0)).createLegacyMountListener();
        verify(module, times(pick ? 1 : 0)).createPickItemListener();
        verify(module, times(sign ? 1 : 0)).createOpenSignListener();
        verify(module, times(equipment ? 1 : 0)).createEquipmentSwapListener();
        if (modern) verify(module, never()).eventClass(UltiLogin.LEGACY_MOUNT_EVENT);

        Map<Class<? extends Event>, Listener> others = new LinkedHashMap<>();
        for (Class<? extends Event> type : selected.keySet()) {
            Listener other = new Listener() { };
            others.put(type, other);
            Bukkit.getPluginManager().registerEvent(type, other, EventPriority.LOWEST,
                    (listener, event) -> { }, owner, false);
        }
        doCallRealMethod().when(module).onUnregister();
        module.onUnregister();
        module.onUnregister();
        for (Class<? extends Event> type : selected.keySet()) {
            assertThat(handlers(type).getRegisteredListeners()).extracting(RegisteredListener::getListener)
                    .containsExactly(others.get(type));
        }
        assertOnlyListener(recipeEvent, recipe);
        assertThat(org.bukkit.event.block.BlockBreakEvent.getHandlerList().getRegisteredListeners())
                .extracting(RegisteredListener::getListener).containsExactly(core);
        assertThat(module.registerSelf()).isTrue();
        for (Class<? extends Event> type : selected.keySet()) {
            assertThat(handlers(type).getRegisteredListeners()).extracting(RegisteredListener::getListener)
                    .hasSize(2).contains(others.get(type)).doesNotContain(selected.get(type));
        }
        assertWarnings(modern || legacy, pick, sign, equipment, 2);
    }

    @Test
    void legacyRegisterEventExecutorUsesTheActualTypeOwnerPriorityAndCore() throws Exception {
        PluginManager manager = mock(PluginManager.class);
        when(manager.getPlugin("UltiTools")).thenReturn(owner);
        when(module.eventClass(UltiLogin.MODERN_MOUNT_EVENT)).thenReturn(null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            module.registerVersionedProtections();
        }
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        ArgumentCaptor<Listener> marker = ArgumentCaptor.forClass(Listener.class);
        verify(manager).registerEvent(eq(org.spigotmc.event.entity.EntityMountEvent.class), marker.capture(),
                eq(EventPriority.LOWEST), executor.capture(), same(owner), eq(false));
        assertThat(marker.getValue().getClass().getAnnotations()).isEmpty();
        assertThat(handlerEvents(marker.getValue().getClass())).isEmpty();
        Event wrong = new EntityMountEvent(player, mock(Entity.class));
        executor.getValue().execute(marker.getValue(), wrong);
        verify(core, never()).protectMount(any(), any());
        Event event = mountEvent(false, player);
        executor.getValue().execute(marker.getValue(), event);
        verify(core, times(1)).protectMount(player, (Cancellable) event);
        assertThat(((Cancellable) event).isCancelled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectedMountSharesJoinTickQuitAndAuthenticationStateWithTheExistingBean(boolean modern) {
        doReturn(modern ? EntityMountEvent.class : null).when(module).eventClass(UltiLogin.MODERN_MOUNT_EVENT);
        module.registerVersionedProtections();
        when(player.isOnline()).thenReturn(true);
        when(player.isInsideVehicle()).thenReturn(true);
        core.onPlayerJoin(new PlayerJoinEvent(player, "join"));
        Event restored = mountEvent(modern, player);
        Bukkit.getPluginManager().callEvent(restored);
        assertThat(((Cancellable) restored).isCancelled()).as("join-tick restore must not delete the vehicle").isFalse();
        org.mockbukkit.mockbukkit.MockBukkit.getMock().getScheduler().performOneTick();
        verify(player).leaveVehicle();
        Event later = mountEvent(modern, player);
        Bukkit.getPluginManager().callEvent(later);
        assertThat(((Cancellable) later).isCancelled()).isTrue();
        when(service.isLoggedIn(player.getUniqueId())).thenReturn(true);
        Event loggedIn = mountEvent(modern, player);
        Bukkit.getPluginManager().callEvent(loggedIn);
        assertThat(((Cancellable) loggedIn).isCancelled()).isFalse();
        when(service.isLoggedIn(player.getUniqueId())).thenReturn(false);
        when(player.hasMetadata("NPC")).thenReturn(true);
        Event npc = mountEvent(modern, player);
        Bukkit.getPluginManager().callEvent(npc);
        assertThat(((Cancellable) npc).isCancelled()).isFalse();
        Event mob = mountEvent(modern, mock(Entity.class));
        Bukkit.getPluginManager().callEvent(mob);
        assertThat(((Cancellable) mob).isCancelled()).isFalse();
        when(player.hasMetadata("NPC")).thenReturn(false);
        core.onPlayerJoin(new PlayerJoinEvent(player, "join again"));
        core.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
        Event afterQuit = mountEvent(modern, player);
        Bukkit.getPluginManager().callEvent(afterQuit);
        assertThat(((Cancellable) afterQuit).isCancelled()).as("quit clears the same join-tick set").isTrue();
    }

    @Test
    void aNewEnableReprobesMissingCapabilitiesAndResetsWarnings() {
        when(module.eventClass(anyString())).thenReturn(null);
        module.registerVersionedProtections();
        module.registerVersionedProtections();
        assertWarnings(false, false, false, true, 1);
        module.unregisterVersionedProtections();
        when(module.eventClass(anyString())).thenCallRealMethod();
        module.registerVersionedProtections();
        assertThat(EntityMountEvent.getHandlerList().getRegisteredListeners()).hasSize(1);
        assertThat(PlayerPickItemEvent.getHandlerList().getRegisteredListeners()).hasSize(1);
        assertThat(PlayerOpenSignEvent.getHandlerList().getRegisteredListeners()).hasSize(1);
        assertWarnings(false, false, false, true, 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unloadKeepsPasswordFilterCleanupEvenIfListenerCleanupFails(boolean versioned) {
        RuntimeException failure = new IllegalStateException("cleanup failure");
        if (versioned) doThrow(failure).when(module).unregisterVersionedProtections();
        else doThrow(failure).when(module).unregisterEquipmentSwapProtection();
        doCallRealMethod().when(module).onUnregister();
        try (MockedStatic<PasswordLogFilterInstaller> installer = mockStatic(PasswordLogFilterInstaller.class)) {
            assertThatThrownBy(module::onUnregister).isSameAs(failure);
            installer.verify(PasswordLogFilterInstaller::uninstall);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allPaperHooksPreserveAuthenticationNpcNullAndAlreadyCancelledBehavior(boolean loggedIn) throws Exception {
        Bukkit.getPluginManager().registerEvents(new LoginProtectionPaperListener(service), owner);
        module.registerVersionedProtections();
        module.registerEquipmentSwapProtection();
        when(service.isLoggedIn(player.getUniqueId())).thenReturn(loggedIn);
        for (Class<? extends Event> type : Arrays.<Class<? extends Event>>asList(
                PlayerRecipeBookClickEvent.class, PlayerPickItemEvent.class,
                PlayerOpenSignEvent.class, PlayerSwapWithEquipmentSlotEvent.class)) {
            Event event = paperEvent(type, player);
            when(((Cancellable) event).isCancelled()).thenReturn(true);
            Bukkit.getPluginManager().callEvent(event);
            verify((Cancellable) event, times(loggedIn ? 0 : 1)).setCancelled(true);
            when(player.hasMetadata("NPC")).thenReturn(true);
            Event npc = paperEvent(type, player);
            Bukkit.getPluginManager().callEvent(npc);
            verify((Cancellable) npc, never()).setCancelled(anyBoolean());
            when(player.hasMetadata("NPC")).thenReturn(false);
            Event noPlayer = paperEvent(type, null);
            Bukkit.getPluginManager().callEvent(noPlayer);
            verify((Cancellable) noPlayer).setCancelled(true);
        }
    }

    private void assertWarnings(boolean mount, boolean pick, boolean sign, boolean equipment, int enables) {
        verify(logger, times(mount ? 0 : enables)).warn(CatalogueText.text("en", "log_mount_hook_unavailable"));
        verify(logger, times(pick ? 0 : enables)).warn(CatalogueText.text("en", "log_pick_item_hook_unavailable"));
        verify(logger, times(sign ? 0 : enables)).warn(CatalogueText.text("en", "log_open_sign_hook_unavailable"));
        verify(logger, times(equipment ? 0 : enables)).warn(CatalogueText.text("en", "log_equipment_swap_hook_unavailable"));
        verify(logger, times(enables * ((mount ? 0 : 1) + (pick ? 0 : 1) + (sign ? 0 : 1) + (equipment ? 0 : 1))))
                .warn(anyString());
    }

    private Listener assertOneOwnedHook(Event event) {
        RegisteredListener[] registered = event.getHandlers().getRegisteredListeners();
        assertThat(registered).hasSize(1);
        assertThat(registered[0].getPlugin()).isSameAs(owner);
        assertThat(registered[0].getPriority()).isEqualTo(EventPriority.LOWEST);
        assertThat(registered[0].isIgnoringCancelled()).isFalse();
        return registered[0].getListener();
    }

    private void assertOnlyListener(Event event, Listener listener) {
        assertThat(event.getHandlers().getRegisteredListeners()).extracting(RegisteredListener::getListener)
                .containsExactly(listener);
    }

    private static Event mountEvent(boolean modern, Entity passenger) {
        Entity vehicle = mock(Entity.class);
        return modern ? new EntityMountEvent(passenger, vehicle)
                : new org.spigotmc.event.entity.EntityMountEvent(passenger, vehicle);
    }

    private static Event paperEvent(Class<? extends Event> type, Player player) throws Exception {
        Event event = mock(type);
        when(((PlayerEvent) event).getPlayer()).thenReturn(player);
        when(event.getHandlers()).thenReturn(handlers(type));
        return event;
    }

    private static HandlerList handlers(Class<? extends Event> type) throws Exception {
        return (HandlerList) type.getMethod("getHandlerList").invoke(null);
    }

    private static Set<String> handlerEvents(Class<?> listener) {
        Set<String> events = new TreeSet<>();
        Arrays.stream(listener.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(EventHandler.class))
                .forEach(m -> events.add(m.getParameterTypes()[0].getName()));
        return events;
    }

    private static class MissingEventLoader extends URLClassLoader {
        private final Set<String> missing;
        boolean optionalRequested;
        MissingEventLoader(String... missing) {
            super(new URL[] {UltiLogin.class.getProtectionDomain().getCodeSource().getLocation()},
                    UltiLogin.class.getClassLoader());
            this.missing = new TreeSet<>(Arrays.asList(missing));
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (missing.contains(name)) throw new ClassNotFoundException("test hides " + name);
            if (PAPER_LISTENER.equals(name) || CORE_LISTENER.equals(name) || UltiLogin.class.getName().equals(name)
                    || name.startsWith("com.ultikits.plugins.logincompat.")) {
                if (name.startsWith("com.ultikits.plugins.logincompat.")) optionalRequested = true;
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
            return super.loadClass(name, resolve);
        }
    }

    private static class InitializationState { static boolean initialized; }
    public static class InitializationProbeEvent extends Event {
        static { InitializationState.initialized = true; }
        @Override public HandlerList getHandlers() { throw new UnsupportedOperationException(); }
    }
}
