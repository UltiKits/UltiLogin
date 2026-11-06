package com.ultikits.plugins.login;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.listener.LoginProtectionPaperListener;
import com.ultikits.plugins.login.logging.PasswordLogFilterInstaller;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.plugins.logincompat.EquipmentSwapProtectionListener;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperProtectionCompatibilityTest {
    private static final String SWAP_EVENT =
            "io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent";
    private static final String PAPER_LISTENER =
            "com.ultikits.plugins.login.listener.LoginProtectionPaperListener";
    private static final String OPTIONAL_LISTENER = EquipmentSwapProtectionListener.class.getName();
    private UltiLogin module;
    private LoginService service;
    private Player player;
    private PluginLogger logger;
    private Plugin owner;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.bootstrapLiveServer();
        module = mock(UltiLogin.class);
        service = mock(LoginService.class);
        logger = mock(PluginLogger.class);
        owner = Bukkit.getPluginManager().getPlugin("UltiTools");
        assertThat(owner).isNotNull();
        when(module.getLogger()).thenReturn(logger);
        when(module.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        SimpleContainer context = mock(SimpleContainer.class);
        when(module.getContext()).thenReturn(context);
        when(context.getBean(LoginService.class)).thenReturn(service);
        doCallRealMethod().when(module).registerEquipmentSwapProtection();
        doCallRealMethod().when(module).unregisterEquipmentSwapProtection();
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
        try (MissingEventLoader loader = withoutSwapEvent()) {
            Class<?> listener = Class.forName(PAPER_LISTENER, false, loader);
            assertThatCode(listener::getDeclaredMethods)
                    .as("a missing optional equipment event must not disable the other three handlers")
                    .doesNotThrowAnyException();
            Set<String> events = new TreeSet<String>();
            Arrays.stream(listener.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(EventHandler.class))
                    .forEach(m -> events.add(m.getParameterTypes()[0].getName()));
            assertThat(events).containsExactlyInAnyOrder(
                    "io.papermc.paper.event.player.PlayerPickItemEvent",
                    "com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent",
                    "io.papermc.paper.event.player.PlayerOpenSignEvent");
        }
    }

    @Test
    void probeUsesProvidedLoaderAndDoesNotInitializeOrRequestOptionalListener() throws Exception {
        assertThat(UltiLogin.equipmentSwapEventAvailable(UltiLogin.class.getClassLoader())).isTrue();
        when(module.equipmentSwapEventAvailable()).thenCallRealMethod();
        assertThat(module.equipmentSwapEventAvailable()).isTrue();
        try (MissingEventLoader loader = withoutSwapEvent()) {
            assertThat(UltiLogin.equipmentSwapEventAvailable(loader)).isFalse();
            Class<?> startup = Class.forName(UltiLogin.class.getName(), false, loader);
            assertThatCode(startup::getDeclaredMethods).doesNotThrowAnyException();
            assertThatCode(startup::getDeclaredFields).doesNotThrowAnyException();
            assertThat(loader.optionalRequested).isFalse();
            // Control: the loader would detect an accidentally scanned optional listener.
            Class<?> optional = Class.forName(OPTIONAL_LISTENER, false, loader);
            assertThatThrownBy(optional::getDeclaredMethods).isInstanceOf(NoClassDefFoundError.class);
            assertThat(loader.optionalRequested).isTrue();
        }
    }

    @Test
    void unresolvableEventAlsoDisablesOnlyTheDedicatedHook() {
        ClassLoader broken = new ClassLoader(UltiLogin.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (SWAP_EVENT.equals(name)) {
                    throw new NoClassDefFoundError("event dependency missing");
                }
                return super.loadClass(name, resolve);
            }
        };
        assertThat(UltiLogin.equipmentSwapEventAvailable(broken)).isFalse();
    }

    @Test
    void optionalListenerIsOutsideEveryDeclaredScanRootAndNotAnIocComponent() throws Exception {
        for (String root : UltiLogin.class.getAnnotation(UltiToolsModule.class).scanBasePackages()) {
            assertThat(OPTIONAL_LISTENER).doesNotStartWith(root + ".");
        }
        assertThat(EquipmentSwapProtectionListener.class.getAnnotations()).isEmpty();
        EventHandler handler = EquipmentSwapProtectionListener.class
                .getDeclaredMethod("onPlayerSwapWithEquipmentSlot", PlayerSwapWithEquipmentSlotEvent.class)
                .getAnnotation(EventHandler.class);
        assertThat(handler.priority()).isEqualTo(EventPriority.LOWEST);
        assertThat(handler.ignoreCancelled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void startupRegistersAndDispatchesAllAvailablePaperProtections(boolean present) {
        when(module.equipmentSwapEventAvailable()).thenReturn(present);
        if (!present) {
            doThrow(new AssertionError("absent event must never load the factory")).when(module)
                    .createEquipmentSwapListener();
        }
        LoginProtectionPaperListener retained = new LoginProtectionPaperListener(service);
        Bukkit.getPluginManager().registerEvents(retained, owner);
        when(module.registerSelf()).thenCallRealMethod();
        assertThat(module.registerSelf()).isTrue();
        module.registerEquipmentSwapProtection(); // idempotent within this enable
        assertRetainedRegisteredAndCancelled(retained);
        assertThat(PlayerSwapWithEquipmentSlotEvent.getHandlerList().getRegisteredListeners())
                .hasSize(present ? 1 : 0);
        if (present) {
            RegisteredListener registered = PlayerSwapWithEquipmentSlotEvent.getHandlerList().getRegisteredListeners()[0];
            assertThat(registered.getPlugin()).isSameAs(owner);
            assertThat(registered.getPriority()).isEqualTo(EventPriority.LOWEST);
            assertThat(registered.isIgnoringCancelled()).isFalse();
            PlayerSwapWithEquipmentSlotEvent swap = swapEvent();
            Bukkit.getPluginManager().callEvent(swap);
            verify(swap).setCancelled(true);
            verify(module, times(1)).createEquipmentSwapListener();
            verify(logger, never()).warn(anyString());
        } else {
            verify(module, never()).createEquipmentSwapListener();
            verify(logger, times(1)).warn(CatalogueText.text("en", "log_equipment_swap_hook_unavailable"));
        }
        doCallRealMethod().when(module).onReload();
        module.onReload();
        verify(logger, times(present ? 0 : 1)).warn(anyString());
    }

    @Test
    void unloadRemovesOnlyOwnedOptionalListenerAndReenableRegistersOneFreshInstance() {
        when(module.equipmentSwapEventAvailable()).thenReturn(true);
        Listener other = new EquipmentSwapProtectionListener(service);
        Bukkit.getPluginManager().registerEvents(other, owner);
        module.registerEquipmentSwapProtection();
        RegisteredListener[] before = PlayerSwapWithEquipmentSlotEvent.getHandlerList().getRegisteredListeners();
        assertThat(before).hasSize(2);
        Listener first = Arrays.stream(before).map(RegisteredListener::getListener)
                .filter(l -> l != other).findFirst().get();
        doCallRealMethod().when(module).onUnregister();
        module.onUnregister();
        module.onUnregister();
        assertThat(PlayerSwapWithEquipmentSlotEvent.getHandlerList().getRegisteredListeners())
                .extracting(RegisteredListener::getListener).containsExactly(other);
        module.registerEquipmentSwapProtection();
        assertThat(PlayerSwapWithEquipmentSlotEvent.getHandlerList().getRegisteredListeners())
                .extracting(RegisteredListener::getListener).hasSize(2).contains(other).doesNotContain(first);
        verify(module, times(2)).createEquipmentSwapListener();
    }

    @Test
    void unloadKeepsPasswordFilterCleanupEvenIfListenerCleanupFails() {
        RuntimeException failure = new IllegalStateException("cleanup failure");
        doThrow(failure).when(module).unregisterEquipmentSwapProtection();
        doCallRealMethod().when(module).onUnregister();
        try (org.mockito.MockedStatic<PasswordLogFilterInstaller> installer = mockStatic(PasswordLogFilterInstaller.class)) {
            assertThatThrownBy(module::onUnregister).isSameAs(failure);
            installer.verify(PasswordLogFilterInstaller::uninstall);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void equipmentSwapPreservesAuthenticationBehavior(boolean loggedIn) {
        when(service.isLoggedIn(player.getUniqueId())).thenReturn(loggedIn);
        PlayerSwapWithEquipmentSlotEvent event = swapEvent();
        new EquipmentSwapProtectionListener(service).onPlayerSwapWithEquipmentSlot(event);
        if (loggedIn) {
            verify(event, never()).setCancelled(anyBoolean());
        } else {
            verify(event).setCancelled(true);
        }
    }

    @Test
    void equipmentSwapPreservesNpcExemptionAndNullFailClosed() {
        when(player.hasMetadata("NPC")).thenReturn(true);
        PlayerSwapWithEquipmentSlotEvent npc = swapEvent();
        EquipmentSwapProtectionListener listener = new EquipmentSwapProtectionListener(service);
        listener.onPlayerSwapWithEquipmentSlot(npc);
        verify(npc, never()).setCancelled(anyBoolean());
        verifyNoInteractions(service);
        PlayerSwapWithEquipmentSlotEvent noPlayer = mock(PlayerSwapWithEquipmentSlotEvent.class);
        listener.onPlayerSwapWithEquipmentSlot(noPlayer);
        verify(noPlayer).setCancelled(true);
    }

    @Test
    void alreadyCancelledEquipmentEventStillReachesProtection() {
        when(module.equipmentSwapEventAvailable()).thenReturn(true);
        module.registerEquipmentSwapProtection();
        PlayerSwapWithEquipmentSlotEvent event = swapEvent();
        when(event.isCancelled()).thenReturn(true);
        Bukkit.getPluginManager().callEvent(event);
        verify(event).setCancelled(true);
    }

    private PlayerSwapWithEquipmentSlotEvent swapEvent() {
        PlayerSwapWithEquipmentSlotEvent event = mock(PlayerSwapWithEquipmentSlotEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHandlers()).thenReturn(PlayerSwapWithEquipmentSlotEvent.getHandlerList());
        return event;
    }

    private void assertRetainedRegisteredAndCancelled(Listener retained) {
        PlayerPickItemEvent pick = mock(PlayerPickItemEvent.class);
        when(pick.getPlayer()).thenReturn(player);
        when(pick.getHandlers()).thenReturn(PlayerPickItemEvent.getHandlerList());
        PlayerRecipeBookClickEvent recipe = mock(PlayerRecipeBookClickEvent.class);
        when(recipe.getPlayer()).thenReturn(player);
        when(recipe.getHandlers()).thenReturn(PlayerRecipeBookClickEvent.getHandlerList());
        PlayerOpenSignEvent sign = mock(PlayerOpenSignEvent.class);
        when(sign.getPlayer()).thenReturn(player);
        when(sign.getHandlers()).thenReturn(PlayerOpenSignEvent.getHandlerList());
        for (Event event : Arrays.<Event>asList(pick, recipe, sign)) {
            assertThat(event.getHandlers().getRegisteredListeners())
                    .extracting(RegisteredListener::getListener).containsExactly(retained);
            Bukkit.getPluginManager().callEvent(event);
            verify((Cancellable) event).setCancelled(true);
        }
    }

    private static MissingEventLoader withoutSwapEvent() {
        return new MissingEventLoader();
    }

    private static class MissingEventLoader extends URLClassLoader {
        boolean optionalRequested;
        MissingEventLoader() {
            super(new URL[] {UltiLogin.class.getProtectionDomain().getCodeSource().getLocation()},
                    UltiLogin.class.getClassLoader());
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (SWAP_EVENT.equals(name)) {
                throw new ClassNotFoundException("test hides only " + name);
            }
            if (PAPER_LISTENER.equals(name) || OPTIONAL_LISTENER.equals(name) || UltiLogin.class.getName().equals(name)) {
                if (OPTIONAL_LISTENER.equals(name)) optionalRequested = true;
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
            return super.loadClass(name, resolve);
        }
    }
}
