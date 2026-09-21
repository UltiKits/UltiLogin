package com.ultikits.plugins.login.listener;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.service.LoginService;

import org.bukkit.Server;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The declared event-coverage contract of {@link LoginProtectionListener}, and the structural
 * checks that keep it honest (UltiKits/UltiLogin#24).
 *
 * <h2>Why a structural test, and not just two more handler tests</h2>
 *
 * #24 reported that an unauthenticated player could still equip an item onto an armor stand even
 * though {@link org.bukkit.event.player.PlayerInteractEntityEvent} was already handled. That is not
 * a missing-handler oversight, it is a dispatch rule that makes coverage invisible by reading the
 * listener: Bukkit registers and delivers an event on the {@code HandlerList} of its
 * <em>registration class</em> — the nearest class, starting at the event's own class and walking up,
 * that <em>declares</em> a static {@code getHandlerList()}. A subclass that declares its own
 * {@code HandlerList} therefore has a completely separate dispatch list, and a handler registered
 * for the superclass never receives it.
 *
 * <p>Measured in the {@code paper-api} version this module compiles against (1.21.11-R0.1-SNAPSHOT,
 * via {@code javap -p}): {@code PlayerInteractEntityEvent}, {@code PlayerInteractAtEntityEvent} and
 * {@code PlayerArmorStandManipulateEvent} each declare their own {@code HANDLER_LIST}, and the
 * latter two both extend {@code PlayerInteractEntityEvent} <em>directly</em> — they are siblings, not
 * a chain. So neither reached {@code onPlayerInteractEntity}, and covering only the interact-at event
 * (which #24's suggested fix names) would still not have covered the armor-stand event itself.
 *
 * <p>Because that gap is invisible in the source, the coverage set is declared here as data and
 * asserted against the listener, rather than being read off the listener. {@link
 * ListenerCoversEveryRequiredEvent} fails when a required handler is removed, and {@link
 * NoHandledEventHasASilentlyDivertedSubclass} fails when a future {@code paper-api} adds a new
 * diverted subclass of an event this listener handles — the mechanical form of #24 itself.
 *
 * <h2>Enumeration criterion for the required set</h2>
 *
 * An event is required when it is the <em>first</em> server-side event carrying an unauthenticated
 * player's intent to mutate world, entity or inventory state for that interaction — one entry per
 * distinct client intent, with no other required event necessarily firing (and being cancelled)
 * first. Consequence events are therefore deliberately absent: cancelling {@code
 * PlayerInteractEvent} already stops the bucket, consume, fish, shear, harvest and lectern events
 * that can only follow it, and cancelling {@code InventoryOpenEvent} already stops every container
 * event that needs an open container ({@code TradeSelectEvent}, anvil rename, and so on).
 *
 * <p><strong>Two entries are in the set defensively, not because a bypass was observed.</strong>
 * {@code PlayerEditBookEvent} and {@code SignChangeEvent} each arrive on their own client packet
 * with their own {@code HandlerList}. Whether an unauthenticated player can reach either depends on
 * whether the client opens those editors only after a server packet that itself follows an
 * uncancelled {@code PlayerInteractEvent} — a chain that could not be measured here, only reasoned
 * about. Rather than ship the reasoning, both are covered: if the chain holds, two handlers are
 * unreachable code in a security net and cost nothing; if it does not, an unauthenticated player
 * edits a book or a sign, which is the bypass class #24 exists to close. Do not read their presence
 * here as evidence that either bypass exists.
 *
 * <h2>Events this listener deliberately does not cover</h2>
 *
 * See {@link #DELIBERATELY_UNCOVERED}. Each entry carries the reason, and {@link
 * EveryDeliberateExclusionIsRealAndExplained} asserts the entry is a genuinely diverted subclass of a
 * handled event, so the map cannot quietly accumulate stale or invented entries.
 */
@DisplayName("LoginProtectionListener event-coverage contract (#24)")
class LoginProtectionEventCoverageTest {

    /**
     * Events through which an unauthenticated player would otherwise mutate world, entity or
     * inventory state. This is the set UltiKits/UltiLogin#24 belongs to; a missing entry is an
     * authentication bypass, not a cosmetic gap.
     */
    private static final Set<String> REQUIRED_STATE_MUTATION_EVENTS =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                    "org.bukkit.event.block.BlockBreakEvent",
                    "org.bukkit.event.block.BlockPlaceEvent",
                    "org.bukkit.event.player.PlayerInteractEvent",
                    "org.bukkit.event.player.PlayerInteractEntityEvent",
                    "org.bukkit.event.player.PlayerInteractAtEntityEvent",
                    "org.bukkit.event.player.PlayerArmorStandManipulateEvent",
                    "org.bukkit.event.player.PlayerDropItemEvent",
                    "org.bukkit.event.entity.EntityPickupItemEvent",
                    "org.bukkit.event.inventory.InventoryClickEvent",
                    "org.bukkit.event.inventory.InventoryDragEvent",
                    "org.bukkit.event.inventory.InventoryOpenEvent",
                    "org.bukkit.event.player.PlayerSwapHandItemsEvent",
                    "org.bukkit.event.player.PlayerEditBookEvent",
                    "org.bukkit.event.block.SignChangeEvent",
                    "org.bukkit.event.entity.EntityDamageByEntityEvent")));

    /**
     * The rest of the protection surface — not state mutation, so not part of #24's defect class,
     * but held in the same net so a later change cannot silently drop one of them either. These are
     * not all plain cancels: movement is reverted with {@code setTo}, chat and commands re-send the
     * credential prompt, and {@code EntityDamageEvent} protects the unauthenticated player rather
     * than restraining them.
     */
    private static final Set<String> REQUIRED_OTHER_PROTECTION_EVENTS =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                    "org.bukkit.event.player.PlayerMoveEvent",
                    "org.bukkit.event.player.AsyncPlayerChatEvent",
                    "org.bukkit.event.player.PlayerCommandPreprocessEvent",
                    "org.bukkit.event.entity.EntityDamageEvent")));

    /**
     * Subclasses of a handled event that declare their own {@code HandlerList} — so they are NOT
     * delivered to that handler — and are nevertheless left uncovered on purpose, with the reason.
     */
    private static final Map<String, String> DELIBERATELY_UNCOVERED;

    static {
        Map<String, String> uncovered = new LinkedHashMap<String, String>();
        uncovered.put("org.bukkit.event.player.PlayerTeleportEvent",
                "This module teleports unauthenticated players itself: LoginService"
                        + "#applyNoSessionProtections sends them to the configured spawn while they are"
                        + " still unauthenticated, and records the original location to restore on"
                        + " login. Cancelling teleports for unauthenticated players would break the"
                        + " module's own spawn protection, and would also stop an operator rescuing a"
                        + " stuck player with /tp. A teleport is not player-initiated world mutation,"
                        + " and onPlayerMove already freezes walking.");
        uncovered.put("org.bukkit.event.player.PlayerPortalEvent",
                "A subclass of PlayerTeleportEvent, excluded for the same reason, plus one of its"
                        + " own: with movement frozen an unauthenticated player cannot walk into a"
                        + " portal, so the only way to reach this event is having quit inside a portal"
                        + " block — where cancelling it would trap the player there until they log in"
                        + " from inside the portal.");
        uncovered.put("org.bukkit.event.player.AsyncPlayerChatPreviewEvent",
                "A chat preview renders text and mutates no world or inventory state, so it is"
                        + " outside the required set's criterion. Chat itself is already cancelled by"
                        + " onPlayerChat. The class is also @Deprecated in paper-api 1.21.11.");
        DELIBERATELY_UNCOVERED = Collections.unmodifiableMap(uncovered);
    }

    // ---------------------------------------------------------------------------------------
    // Bukkit's own dispatch rules, reimplemented here so the tests assert delivery rather than
    // the mere presence of a method with a plausible name.
    // ---------------------------------------------------------------------------------------

    /**
     * The class whose {@code HandlerList} Bukkit registers and dispatches {@code eventType} on:
     * the nearest class, from {@code eventType} upwards, that declares a static {@code
     * getHandlerList()}. Mirrors {@code SimplePluginManager#getRegistrationClass}.
     */
    private static Class<?> registrationClass(Class<?> eventType) {
        Class<?> current = eventType;
        while (current != null && Event.class.isAssignableFrom(current)) {
            try {
                current.getDeclaredMethod("getHandlerList");
                return current;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** Every event type the listener declares an {@code @EventHandler} for. */
    private static Set<Class<?>> handledEventTypes() {
        Set<Class<?>> handled = new LinkedHashSet<Class<?>>();
        for (Method method : LoginProtectionListener.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class)) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && Event.class.isAssignableFrom(parameters[0])) {
                handled.add(parameters[0]);
            }
        }
        return handled;
    }

    private static Set<String> handledEventTypeNames() {
        Set<String> names = new TreeSet<String>();
        for (Class<?> handled : handledEventTypes()) {
            names.add(handled.getName());
        }
        return names;
    }

    /**
     * The listener methods that would actually receive {@code eventType} on a live server: declared
     * with {@code @EventHandler}, registered on the same {@code HandlerList}, and with a parameter
     * type the fired event is assignable to (the second check is what Bukkit's generated
     * {@code EventExecutor} performs before invoking the method).
     */
    private static List<Method> deliveredHandlersFor(Class<?> eventType) {
        List<Method> delivered = new ArrayList<Method>();
        Class<?> eventList = registrationClass(eventType);
        for (Method method : LoginProtectionListener.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class)) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1 || !Event.class.isAssignableFrom(parameters[0])) {
                continue;
            }
            if (registrationClass(parameters[0]) == eventList
                    && parameters[0].isAssignableFrom(eventType)) {
                delivered.add(method);
            }
        }
        return delivered;
    }

    /**
     * Load every {@code org.bukkit.event} class from the {@code paper-api} jar on the test
     * classpath, without initialising any of them.
     *
     * <p>Loading failures are collected and returned to the caller rather than skipped: a scan that
     * silently shrinks would turn {@link NoHandledEventHasASilentlyDivertedSubclass} into a test
     * that passes because it looked at nothing.
     */
    private static ScanResult scanBukkitEventClasses() throws Exception {
        URL location = Event.class.getProtectionDomain().getCodeSource().getLocation();
        assertThat(location)
                .as("the paper-api code source must be resolvable, or this scan proves nothing")
                .isNotNull();
        ScanResult result = new ScanResult();
        result.source = location.toString();
        JarFile jar = new JarFile(new File(location.toURI()));
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (!entry.startsWith("org/bukkit/event/") || !entry.endsWith(".class")) {
                    continue;
                }
                String name = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
                if (name.indexOf('$') >= 0 || name.endsWith(".package-info")) {
                    continue;
                }
                try {
                    Class<?> loaded = Class.forName(
                            name, false, LoginProtectionListener.class.getClassLoader());
                    if (Event.class.isAssignableFrom(loaded) && loaded != Event.class) {
                        result.eventClasses.add(loaded);
                    }
                } catch (Throwable failure) {
                    result.failures.add(name + " -> " + failure);
                }
            }
        } finally {
            jar.close();
        }
        return result;
    }

    private static final class ScanResult {
        private String source;
        private final List<Class<?>> eventClasses = new ArrayList<Class<?>>();
        private final List<String> failures = new ArrayList<String>();
    }

    // ---------------------------------------------------------------------------------------
    // Structural checks
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("the listener declares a handler for every event in the required set")
    class ListenerCoversEveryRequiredEvent {

        @Test
        @DisplayName("Should declare an @EventHandler for every state-mutation event an "
                + "unauthenticated player could otherwise act through")
        void coversEveryStateMutationEvent() {
            assertThat(handledEventTypeNames())
                    .as("LoginProtectionListener must declare an @EventHandler taking exactly this "
                            + "event type. A handler on a superclass is not enough: an event class "
                            + "that declares its own HandlerList is dispatched on a separate list "
                            + "and never reaches the superclass handler -- that is the bug "
                            + "UltiKits/UltiLogin#24 reported for armor-stand equipping.")
                    .containsAll(REQUIRED_STATE_MUTATION_EVENTS);
        }

        @Test
        @DisplayName("Should declare an @EventHandler for every other event the protection "
                + "feature is built on")
        void coversEveryOtherProtectionEvent() {
            assertThat(handledEventTypeNames())
                    .as("the rest of the protection surface must stay wired up too")
                    .containsAll(REQUIRED_OTHER_PROTECTION_EVENTS);
        }

        @Test
        @DisplayName("Should reach a handler that will actually be invoked for each required "
                + "state-mutation event, by Bukkit's own registration-class rule")
        void everyRequiredEventResolvesToADeliveredHandler() throws Exception {
            List<String> undelivered = new ArrayList<String>();
            for (String name : REQUIRED_STATE_MUTATION_EVENTS) {
                Class<?> eventType = Class.forName(name);
                if (deliveredHandlersFor(eventType).isEmpty()) {
                    undelivered.add(name + " (dispatched on "
                            + registrationClass(eventType).getName() + "'s HandlerList)");
                }
            }
            assertThat(undelivered)
                    .as("each required event must resolve to a listener method Bukkit would "
                            + "actually invoke for it")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("no subclass of a handled event is silently diverted onto its own HandlerList")
    class NoHandledEventHasASilentlyDivertedSubclass {

        @Test
        @DisplayName("Should find the paper-api event hierarchy it is supposed to scan "
                + "(control for the two assertions below)")
        void theScanFindsTheHierarchy() throws Exception {
            ScanResult scan = scanBukkitEventClasses();

            assertThat(scan.failures)
                    .as("every org.bukkit.event class must load, or the sweep below is scanning "
                            + "a silently shrunken set")
                    .isEmpty();
            assertThat(scan.eventClasses)
                    .as("paper-api's org.bukkit.event package holds hundreds of event classes; a "
                            + "near-empty scan means the jar was not read (source: " + scan.source + ")")
                    .hasSizeGreaterThan(200);
            assertThat(classNames(scan.eventClasses))
                    .as("positive control: the scan must find both the class #24 was reported "
                            + "against and a class this listener already handled before #24")
                    .contains("org.bukkit.event.player.PlayerArmorStandManipulateEvent",
                            "org.bukkit.event.player.PlayerInteractEntityEvent");
            assertThat(divertedSubclassesOfHandledEvents(scan))
                    .as("positive control: the divert-detecting sweep itself must be able to find "
                            + "something -- if it returns nothing at all it would pass vacuously")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("Should leave no diverted subclass of a handled event untriaged -- it is "
                + "either handled too, or listed as deliberately uncovered with a reason")
        void everyDivertedSubclassIsHandledOrDeliberatelyExcluded() throws Exception {
            ScanResult scan = scanBukkitEventClasses();
            Set<String> handled = handledEventTypeNames();

            List<String> untriaged = new ArrayList<String>();
            for (String diverted : divertedSubclassesOfHandledEvents(scan)) {
                if (!handled.contains(diverted) && !DELIBERATELY_UNCOVERED.containsKey(diverted)) {
                    untriaged.add(diverted);
                }
            }

            assertThat(untriaged)
                    .as("each of these is a subclass of an event LoginProtectionListener handles, "
                            + "but declares its own HandlerList, so the existing handler never "
                            + "receives it -- exactly the shape of UltiKits/UltiLogin#24. Add an "
                            + "@EventHandler for it, or add it to DELIBERATELY_UNCOVERED with the "
                            + "reason it is safe to leave open.")
                    .isEmpty();
        }

        @Test
        @DisplayName("Should keep the deliberately-uncovered list honest: every entry is a real "
                + "diverted subclass of a handled event, and carries a reason")
        void everyDeliberateExclusionIsRealAndExplained() throws Exception {
            ScanResult scan = scanBukkitEventClasses();
            Set<String> diverted = new LinkedHashSet<String>(divertedSubclassesOfHandledEvents(scan));

            for (Map.Entry<String, String> exclusion : DELIBERATELY_UNCOVERED.entrySet()) {
                assertThat(diverted)
                        .as(exclusion.getKey() + " is listed as deliberately uncovered, but it is "
                                + "not a diverted subclass of any handled event -- the entry is "
                                + "stale and must be removed")
                        .contains(exclusion.getKey());
                assertThat(exclusion.getValue())
                        .as("every exclusion must say why " + exclusion.getKey() + " is safe to "
                                + "leave uncovered")
                        .isNotBlank();
                assertThat(handledEventTypeNames())
                        .as(exclusion.getKey() + " is both handled and listed as deliberately "
                                + "uncovered; the two statements contradict each other")
                        .doesNotContain(exclusion.getKey());
            }
        }

        /**
         * Every scanned event class that is a strict subclass of an event the listener handles, yet
         * is dispatched on a different {@code HandlerList} than that handler is registered on.
         */
        private List<String> divertedSubclassesOfHandledEvents(ScanResult scan) {
            List<String> diverted = new ArrayList<String>();
            Set<Class<?>> handled = handledEventTypes();
            for (Class<?> candidate : scan.eventClasses) {
                for (Class<?> handledType : handled) {
                    if (candidate != handledType
                            && handledType.isAssignableFrom(candidate)
                            && registrationClass(candidate) != registrationClass(handledType)) {
                        diverted.add(candidate.getName());
                        break;
                    }
                }
            }
            Collections.sort(diverted);
            return diverted;
        }
    }

    private static Set<String> classNames(List<Class<?>> classes) {
        Set<String> names = new TreeSet<String>();
        for (Class<?> type : classes) {
            names.add(type.getName());
        }
        return names;
    }

    // ---------------------------------------------------------------------------------------
    // Behavioural checks for the events #24's sweep added: cancelled while unauthenticated,
    // untouched once authenticated.
    //
    // Each event is pushed through deliveredHandlersFor(...) rather than a direct method call, so
    // these tests fail with "no handler" (rather than failing to compile) while the gap is open,
    // and so they assert the event is really delivered under Bukkit's registration-class rule --
    // the half of #24 that a direct method call cannot see.
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("the events #24's sweep added are cancelled for an unauthenticated player")
    class AddedEventsAreCancelled {

        private LoginProtectionListener listener;
        private LoginService loginService;
        private Player player;
        private UUID playerUuid;

        @BeforeEach
        void setUp() throws Exception {
            UltiLoginTestHelper.setUp();
            loginService = mock(LoginService.class);
            LoginConfig config = UltiLoginTestHelper.createDefaultConfig();
            lenient().when(loginService.getConfig()).thenReturn(config);

            java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            Server mockServer = mock(Server.class);
            org.bukkit.plugin.PluginManager mockPluginManager =
                    mock(org.bukkit.plugin.PluginManager.class);
            org.bukkit.plugin.Plugin mockBukkitPlugin = mock(org.bukkit.plugin.Plugin.class);
            lenient().when(mockBukkitPlugin.isEnabled()).thenReturn(true);
            lenient().when(mockServer.getPluginManager()).thenReturn(mockPluginManager);
            lenient().when(mockPluginManager.getPlugin("UltiTools")).thenReturn(mockBukkitPlugin);
            serverField.set(null, mockServer);

            listener = new LoginProtectionListener(UltiLoginTestHelper.getMockPlugin(), loginService);
            playerUuid = UUID.randomUUID();
            player = UltiLoginTestHelper.createMockPlayer("TestPlayer", playerUuid);
        }

        @AfterEach
        void tearDown() throws Exception {
            UltiLoginTestHelper.tearDown();
            UltiLoginTestHelper.clearBukkitServer();
        }

        @Test
        @DisplayName("Should cancel armor-stand manipulation for an unauthenticated player "
                + "(the interaction UltiKits/UltiLogin#24 reported)")
        void cancelsArmorStandManipulation() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            PlayerArmorStandManipulateEvent event = mock(PlayerArmorStandManipulateEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(PlayerArmorStandManipulateEvent.class, event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel armor-stand manipulation once the player is authenticated")
        void allowsArmorStandManipulationWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            PlayerArmorStandManipulateEvent event = mock(PlayerArmorStandManipulateEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(PlayerArmorStandManipulateEvent.class, event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("Should cancel precise-position entity interaction for an unauthenticated player")
        void cancelsInteractAtEntity() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(PlayerInteractAtEntityEvent.class, event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel precise-position entity interaction once the player is "
                + "authenticated")
        void allowsInteractAtEntityWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(PlayerInteractAtEntityEvent.class, event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("Should cancel an inventory drag for an unauthenticated player")
        void cancelsInventoryDrag() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getWhoClicked()).thenReturn(player);

            dispatch(InventoryDragEvent.class, event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel an inventory drag once the player is authenticated")
        void allowsInventoryDragWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getWhoClicked()).thenReturn(player);

            dispatch(InventoryDragEvent.class, event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("Should ignore an inventory drag whose dragger is not a player")
        void ignoresNonPlayerInventoryDrag() {
            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getWhoClicked()).thenReturn(mock(HumanEntity.class));

            dispatch(InventoryDragEvent.class, event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("Should cancel an off-hand swap for an unauthenticated player")
        void cancelsSwapHandItems() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            PlayerSwapHandItemsEvent event = mock(PlayerSwapHandItemsEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(PlayerSwapHandItemsEvent.class, event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel an off-hand swap once the player is authenticated")
        void allowsSwapHandItemsWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            PlayerSwapHandItemsEvent event = mock(PlayerSwapHandItemsEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(PlayerSwapHandItemsEvent.class, event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("Should cancel signing or editing a book for an unauthenticated player "
                + "(covered defensively -- see this class's javadoc)")
        void cancelsBookEdit() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            PlayerEditBookEvent event = mock(PlayerEditBookEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(PlayerEditBookEvent.class, event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel editing a book once the player is authenticated")
        void allowsBookEditWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            PlayerEditBookEvent event = mock(PlayerEditBookEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(PlayerEditBookEvent.class, event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("Should cancel writing a sign for an unauthenticated player "
                + "(covered defensively -- see this class's javadoc)")
        void cancelsSignChange() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(false);
            SignChangeEvent event = mock(SignChangeEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(SignChangeEvent.class, event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel writing a sign once the player is authenticated")
        void allowsSignChangeWhenLoggedIn() {
            when(loginService.isLoggedIn(playerUuid)).thenReturn(true);
            SignChangeEvent event = mock(SignChangeEvent.class);
            when(event.getPlayer()).thenReturn(player);

            dispatch(SignChangeEvent.class, event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        /**
         * Invoke every listener method Bukkit would deliver {@code event} to, failing the test when
         * there is none — which is what an uncovered event class looks like from the outside.
         */
        private void dispatch(Class<? extends Cancellable> eventType, Object event) {
            List<Method> handlers = deliveredHandlersFor(eventType);
            if (handlers.isEmpty()) {
                fail("LoginProtectionListener declares no @EventHandler that Bukkit would deliver "
                        + eventType.getName() + " to. It is dispatched on "
                        + registrationClass(eventType).getName() + "'s HandlerList, so a handler "
                        + "for a superclass on a different HandlerList does not receive it.");
            }
            for (Method handler : handlers) {
                try {
                    handler.setAccessible(true);
                    handler.invoke(listener, event);
                } catch (InvocationTargetException invocationFailure) {
                    throw new IllegalStateException(
                            handler.getName() + " threw", invocationFailure.getCause());
                } catch (IllegalAccessException accessFailure) {
                    throw new IllegalStateException(accessFailure);
                }
            }
        }
    }
}
