package com.ultikits.plugins.login;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
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
 */
class UltiLoginRegistrySentinelTest {

    @BeforeEach
    void setUp() throws Exception {
        // Defensive cleanup first: sibling classes in this module (LoginProtectionListenerTest,
        // EmailVerificationServiceTest, PanelCommandTest) set Bukkit.server via reflection and
        // never clear it, so if one of them runs earlier in the same Surefire fork,
        // MockBukkit.mock() throws UnsupportedOperationException("Cannot redefine singleton
        // Server"). Null the field unconditionally first so this class's own bootstrap is never
        // order-dependent on what ran before it. Pattern per LoginServiceTest.setUp() — the
        // reopen-guard hazard is the same one that pattern was written to close, just tripped
        // here by a different sibling ordering (surefire's default runOrder=filesystem is not
        // stable across machines, so a class order that is safe locally can still be unsafe in
        // CI; see this task's ledger entry for the measured local repro).
        Field bukkitServerField = Bukkit.class.getDeclaredField("server");
        bukkitServerField.setAccessible(true);
        bukkitServerField.set(null, null);

        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
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
