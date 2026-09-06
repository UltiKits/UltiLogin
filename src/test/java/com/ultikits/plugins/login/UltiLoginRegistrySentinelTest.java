package com.ultikits.plugins.login;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard for the test-time server bootstrap.
 * <p>
 * Every assertion here depends on a live server, not merely on a registry constant being
 * resolvable from the classpath alone (mockbukkit-v1.21 registers its {@code RegistryAccess}
 * mock via {@code ServiceLoader}, so a bare constant read would stay green even if the live
 * bootstrap were deleted entirely). If this class goes red, the bootstrap has been silently
 * removed.
 * <p>
 * Bootstraps through {@link UltiLoginTestHelper#bootstrapLiveServer()} — the same shared entry
 * point {@code LoginServiceTest} uses — rather than calling {@code MockBukkit.mock()} itself.
 * Calling {@code MockBukkit.mock()} directly here would let this guard go green purely because
 * it created its own unrelated live server, even if {@code bootstrapLiveServer()} itself had been
 * silently gutted or reverted to a bare {@code mock(Server.class)}; that would defeat the point of
 * a reopen guard for the module's shared test-time wiring (PR #17 review finding: "the guard does
 * not guard the wiring"). Routing through the shared method means breaking it fails this class too.
 */
class UltiLoginRegistrySentinelTest {

    @BeforeEach
    void setUp() throws Exception {
        // UltiLoginTestHelper.bootstrapLiveServer() resets both MockBukkit's own static mock field
        // and Bukkit.server before installing the live server, so this class's own bootstrap is
        // never order-dependent on what a previous Surefire-fork class left behind (see that
        // method's javadoc for the two independent guards, and for why the reset lives there
        // instead of here now).
        UltiLoginTestHelper.bootstrapLiveServer();
    }

    @AfterEach
    void tearDown() {
        UltiLoginTestHelper.tearDownLiveServer();
    }

    @Test
    void liveServerIsBootstrapped() {
        assertNotNull(Bukkit.getServer(), "live server bootstrap must be present");
    }

    @Test
    void unsafeValuesResolves() {
        assertNotNull(Bukkit.getUnsafe(), "UnsafeValues must resolve on a live server");
    }

    @Test
    void createProfileDoesNotSilentlyReturnNull() {
        Object profile = Bukkit.createProfile(UUID.randomUUID(), "SentinelPlayer");
        assertNotNull(profile, "createProfile must not silently return null");
    }

    @Test
    void itemStackConstructionResolvesRegistry() {
        ItemStack stack = new ItemStack(Material.DIAMOND);
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}
