package com.ultikits.plugins.login;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
 * That claim is load-bearing, so each assertion is chosen to fail under a bare
 * {@code mock(Server.class)} — the stub three sibling classes in this module install — and not
 * merely under no server at all: {@code Bukkit.getUnsafe()} and {@code Bukkit.createProfile(...)}
 * both return {@code null} against that stub, and {@code new ItemStack(Material.DIAMOND)} throws
 * from {@code RegistryMock.loadIfEmpty}. {@code liveServerIsBootstrapped} asserts the concrete
 * {@code ServerMock} type for the same reason: {@code assertNotNull(Bukkit.getServer())} would
 * distinguish only "some server" from "none", and the stub would pass it.
 * <p>
 * {@code frameworkPluginIsLoadedUnderTheNameProductionLooksUp} guards the other half of the shared
 * bootstrap — the plugin the module's production code looks up by name. It fails under a bare
 * {@code mock(Server.class)}, whose {@code getPluginManager()} is {@code null}, and its
 * {@code getName()} assertion additionally fails under the fully-configured sibling stub, whose
 * {@code mock(Plugin.class)} answers {@code getName()} with {@code null}.
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
        // assertNotNull alone would not have earned this class's javadoc claim: it distinguishes
        // "some server" from "none", not "live" from "stub", and passes just as happily under the
        // bare mock(Server.class) that three sibling classes in this module install. Asserting the
        // concrete ServerMock type is what discriminates. Mockito's spy in bootstrapLiveServer()
        // does not defeat it -- the spy is a ServerMock instance, so instanceof still holds.
        assertInstanceOf(ServerMock.class, Bukkit.getServer(),
                "live server bootstrap must install a MockBukkit ServerMock, not a bare Server stub");
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

    @Test
    void frameworkPluginIsLoadedUnderTheNameProductionLooksUp() {
        // Six production sites in this module resolve their scheduler owner with
        // Bukkit.getPluginManager().getPlugin("UltiTools"). A ServerMock alone has no plugins
        // loaded and returns null there, so bootstrapLiveServer() loads one; this pins that it
        // still does. The name is asserted literally because the name is the whole contract --
        // UltiKits/UltiEssentials#15 is the same shape as a live production defect, where a lookup
        // for "UltiTools-API" never matches plugin.yml's `name: UltiTools` and always yields null.
        Plugin framework = Bukkit.getPluginManager().getPlugin("UltiTools");

        assertNotNull(framework,
                "live server bootstrap must load a plugin named UltiTools, or every production "
                        + "site that resolves its scheduler owner by that name holds null");
        assertEquals("UltiTools", framework.getName());
    }
}
