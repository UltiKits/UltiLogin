package com.ultikits.plugins.login.listener;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.service.LoginService;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What an unauthenticated rider ends up as, read from real entities
 * (<a href="https://github.com/UltiKits/UltiLogin/issues/41">UltiLogin#41</a>).
 *
 * <h2>Why the join tick is special</h2>
 * A player who quit while riding a vehicle that carried no other player has that vehicle saved with
 * them and taken out of the world. On rejoin, Paper 1.21.11 re-creates it and mounts the player
 * after {@code PlayerJoinEvent} ({@code PrepareSpawnTask$Ready}); if that mount is refused,
 * {@code ServerPlayer#loadAndSpawnParentVehicle} logs "Couldn't reattach entity to player" and
 * discards the vehicle and everything riding it (read with {@code javap -c}: offsets 172-232) — a
 * saddled donkey with its chest, a chest boat, would be deleted. So the mount in the join tick is
 * allowed, the vehicle is back in the world, and one tick later the unauthenticated player is taken
 * off it (found reviewing this fix).
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * The player and the minecart are MockBukkit entities with real passenger state, the scheduler is
 * MockBukkit's, and every assertion reads {@code isInsideVehicle()}, the cart's passengers and
 * whether the cart is still in the world.
 */
@DisplayName("An unauthenticated rider is taken off the vehicle, and the vehicle stays (UltiLogin#41)")
class UnauthenticatedRiderStateTest {

    private ServerMock server;
    private World world;
    private LoginService loginService;
    private LoginProtectionListener listener;
    private PlayerMock rider;
    private Minecart cart;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
        UltiLoginTestHelper.bootstrapLiveServer();
        server = MockBukkit.getMock();
        world = server.addSimpleWorld("world");

        loginService = mock(LoginService.class);
        LoginConfig config = UltiLoginTestHelper.createDefaultConfig();
        lenient().when(loginService.getConfig()).thenReturn(config);
        lenient().when(config.isGuiModeEnabled()).thenReturn(false);
        listener = new LoginProtectionListener(UltiLoginTestHelper.getMockPlugin(), loginService);

        rider = server.addPlayer("Rider");
        lenient().when(loginService.isLoggedIn(rider.getUniqueId())).thenReturn(false);
        cart = world.spawn(new Location(world, 10.5, 64, 10.5), Minecart.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    /** Paper's restore: the mount event, and the mount itself if the event was not cancelled. */
    private boolean restoreMount() {
        EntityMountEvent event = new EntityMountEvent(rider, cart);
        listener.onEntityMount(event);
        if (!event.isCancelled()) {
            cart.addPassenger(rider);
        }
        return !event.isCancelled();
    }

    @Test
    @DisplayName("The vehicle restored in the join tick is not refused, so Paper does not delete it")
    void restoreMountInTheJoinTickIsAllowed() {
        listener.onPlayerJoin(new PlayerJoinEvent(rider, "joined"));

        assertThat(restoreMount())
                .as("a refused restore mount makes Paper discard the vehicle and its riders")
                .isTrue();
        assertThat(rider.isInsideVehicle()).as("precondition: the restore seated the rider").isTrue();
    }

    @Test
    @DisplayName("One tick after the join the unauthenticated rider is off the vehicle, and the vehicle is still there")
    void oneTickLaterTheRiderIsOffAndTheVehicleStays() {
        listener.onPlayerJoin(new PlayerJoinEvent(rider, "joined"));
        restoreMount();

        server.getScheduler().performOneTick();

        assertThat(rider.isInsideVehicle()).as("the unauthenticated rider was taken off").isFalse();
        assertThat(cart.getPassengers()).as("the cart carries nobody").isEmpty();
        assertThat(cart.isValid()).as("the cart is still in the world").isTrue();
    }

    @Test
    @DisplayName("After the join tick, mounting is refused")
    void afterTheJoinTickMountingIsRefused() {
        listener.onPlayerJoin(new PlayerJoinEvent(rider, "joined"));
        server.getScheduler().performOneTick();

        EntityMountEvent event = new EntityMountEvent(rider, cart);
        listener.onEntityMount(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("A player logged in by their session keeps the restored seat")
    void sessionLoginKeepsTheSeat() {
        when(loginService.isLoggedIn(rider.getUniqueId())).thenReturn(true);
        listener.onPlayerJoin(new PlayerJoinEvent(rider, "joined"));
        restoreMount();

        server.getScheduler().performOneTick();

        assertThat(rider.isInsideVehicle()).isTrue();
    }

    @Test
    @DisplayName("A moving cart drops its unauthenticated passenger and carries on with a logged-in one")
    void movingCartDropsOnlyTheUnauthenticatedPassenger() {
        PlayerMock driver = server.addPlayer("Driver");
        lenient().when(loginService.isLoggedIn(driver.getUniqueId())).thenReturn(true);
        cart.addPassenger(driver);
        cart.addPassenger(rider);

        listener.onVehicleMove(new VehicleMoveEvent(cart, cart.getLocation(), cart.getLocation().add(1, 0, 0)));

        assertThat(rider.isInsideVehicle()).as("the unauthenticated passenger is off").isFalse();
        assertThat(driver.isInsideVehicle()).as("the logged-in passenger rides on").isTrue();
        assertThat(cart.getPassengers()).containsExactly(driver);
    }
}
