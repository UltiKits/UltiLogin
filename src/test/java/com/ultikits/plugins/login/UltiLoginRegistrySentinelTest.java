package com.ultikits.plugins.login;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard for the test-time server bootstrap.
 * <p>
 * Every assertion here depends on a live server, not merely on a registry constant being
 * resolvable from the classpath alone (mockbukkit-v1.21 registers its {@code RegistryAccess}
 * mock via {@code ServiceLoader}, so a bare constant read would stay green even if every
 * {@code MockBukkit.mock()} call were deleted). If this class goes red, the bootstrap has been
 * silently removed.
 */
class UltiLoginRegistrySentinelTest {

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
