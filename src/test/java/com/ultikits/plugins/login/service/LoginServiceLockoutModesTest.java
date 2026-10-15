package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Lockout targets and successful-login isolation")
class LoginServiceLockoutModesTest {
    private LoginService service;
    private LoginConfig config;
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final Map<UUID, AccountData> accounts = new HashMap<>();
    @SuppressWarnings("unchecked")
    private final DataOperator<AccountData> data = mock(DataOperator.class);
    @SuppressWarnings("unchecked")
    private final Query<AccountData> query = mock(Query.class, RETURNS_SELF);
    private UUID queriedUuid;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.bootstrapLiveServer();
        UltiLoginTestHelper.setUp();
        config = UltiLoginTestHelper.createDefaultConfig();
        when(config.getLockoutType()).thenReturn("BOTH");
        when(config.getMaxLoginAttempts()).thenReturn(3);
        when(config.getLockoutDuration()).thenReturn(10);
        when(config.isSessionEnabled()).thenReturn(false);
        when(config.getAccountLocked()).thenReturn("LOCKED {TIME}");
        when(config.getAttemptsRemaining()).thenReturn("REMAINING {COUNT}");
        when(UltiLoginTestHelper.getMockPlugin().getDataOperator(AccountData.class)).thenReturn(data);
        when(data.query()).thenReturn(query);
        when(query.eq(any())).thenAnswer(invocation -> {
            queriedUuid = UUID.fromString(invocation.getArgument(0).toString());
            return query;
        });
        when(query.list()).thenAnswer(invocation -> accounts.containsKey(queriedUuid)
                ? Collections.singletonList(accounts.get(queriedUuid)) : Collections.emptyList());
        when(data.updateCounted(any(AccountData.class))).thenReturn(1);
        service = new LoginService(UltiLoginTestHelper.getMockPlugin(), config);
        service.setClock(now::get);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiLoginTestHelper.tearDown();
        UltiLoginTestHelper.tearDownLiveServer();
    }

    private Player player(UUID uuid, String ip) throws Exception {
        Player player = UltiLoginTestHelper.createMockPlayer("Player" + accounts.size(), uuid);
        when(player.getAddress()).thenReturn(new InetSocketAddress(ip, 25565));
        String salt = "lockout-salt";
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(("password123" + salt).getBytes(StandardCharsets.UTF_8));
        accounts.putIfAbsent(uuid, UltiLoginTestHelper.createSampleAccount(uuid, player.getName(),
                Base64.getEncoder().encodeToString(hash), salt));
        return player;
    }

    private Player player(String ip) throws Exception {
        return player(UUID.randomUUID(), ip);
    }

    private void fail(Player player, int count) {
        for (int i = 0; i < count; i++) {
            assertThat(service.login(player, "wrong").isSuccess()).isFalse();
        }
    }

    @Test
    @DisplayName("Positive control: a fresh account accepts its correct password")
    void freshAccountCanLogIn() throws Exception {
        assertThat(service.login(player("10.0.0.1"), "password123").isSuccess()).isTrue();
    }

    @Nested
    @DisplayName("BOTH pair isolation")
    class BothMode {
        @Test
        @DisplayName("A pair lock does not follow an account to another address")
        void bothPairLockDoesNotFollowTheAccountToAnotherAddress() throws Exception {
            UUID uuid = UUID.randomUUID();
            Player first = player(uuid, "10.0.0.1");
            Player elsewhere = player(uuid, "10.0.0.2");
            fail(first, 3);
            assertThat(service.isLocked(first)).isTrue();
            assertThat(service.login(first, "password123").isSuccess()).isFalse();
            assertThat(service.getRemainingAttempts(elsewhere)).isEqualTo(3);
            assertThat(service.login(elsewhere, "password123").isSuccess()).isTrue();
        }

        @Test
        @DisplayName("A pair lock does not refuse another account at the same address")
        void bothPairLockDoesNotRefuseAnotherAccountAtThatAddress() throws Exception {
            Player first = player("10.0.0.1");
            Player other = player("10.0.0.1");
            fail(first, 3);
            assertThat(service.isLocked(first)).isTrue();
            assertThat(service.login(other, "password123").isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Three distinct failing accounts lock the address, including a fourth correct-password account")
        void distinctAccountsLockTheWholeAddress() throws Exception {
            for (int i = 0; i < 3; i++) { fail(player("10.0.0.1"), 1); }
            Player fourth = player("10.0.0.1");
            assertThat(service.isLocked(fourth)).isTrue();
            assertThat(service.getRemainingLockoutTime(fourth)).isEqualTo(10);
            assertThat(service.login(fourth, "password123").getMessage()).isEqualTo("LOCKED 10");
        }

        @Test
        @DisplayName("An intervening success does not erase distinct-account failures")
        void successDoesNotClearAddressMemberships() throws Exception {
            fail(player("10.0.0.1"), 1);
            fail(player("10.0.0.1"), 1);
            assertThat(service.login(player("10.0.0.1"), "password123").isSuccess()).isTrue();
            fail(player("10.0.0.1"), 1);
            assertThat(service.isLocked(player("10.0.0.1"))).isTrue();
        }

        @Test
        @DisplayName("Success clears only its pair and keeps its distinct-account membership")
        void successClearsOwnPairButKeepsAddressMembership() throws Exception {
            Player first = player("10.0.0.1");
            Player second = player("10.0.0.1");
            fail(first, 2);
            fail(second, 1);
            assertThat(service.login(first, "password123").isSuccess()).isTrue();
            assertThat(service.getRemainingAttempts(first)).isEqualTo(3);
            assertThat(service.getRemainingAttempts(second)).isEqualTo(2);
            fail(player("10.0.0.1"), 1);
            assertThat(service.isLocked(first)).isTrue();
        }
    }

    @Nested
    @DisplayName("IP success never erases the shared count")
    class IpMode {
        @Test
        @DisplayName("Another account's success cannot bypass address lockout")
        void anotherAccountsSuccessCannotClearAddressFailures() throws Exception {
            when(config.getLockoutType()).thenReturn("IP");
            Player first = player("10.0.0.1");
            fail(first, 2);
            assertThat(service.login(player("10.0.0.1"), "password123").isSuccess()).isTrue();
            fail(first, 1);
            assertThat(service.isLocked(first)).isTrue();
        }
    }

    @Nested
    @DisplayName("UUID success clears only its account")
    class UuidMode {
        @Test
        @DisplayName("Success clears its own UUID count, not a neighbour's count")
        void successClearsOnlyOwnUuid() throws Exception {
            when(config.getLockoutType()).thenReturn("UUID");
            Player first = player("10.0.0.1");
            Player second = player("10.0.0.1");
            fail(first, 2);
            fail(second, 1);
            assertThat(service.login(first, "password123").isSuccess()).isTrue();
            assertThat(service.getRemainingAttempts(first)).isEqualTo(3);
            assertThat(service.getRemainingAttempts(second)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Clock-driven count and lock expiry")
    class Expiry {
        @Test
        @DisplayName("IP failures expire from the latest failure, including read-only access")
        void ipCountExpiresFromLatestFailure() throws Exception {
            when(config.getLockoutType()).thenReturn("IP");
            Player first = player("10.0.0.1");
            fail(first, 1);
            now.addAndGet(9_000);
            fail(first, 1);
            now.addAndGet(1_000);
            assertThat(service.getRemainingAttempts(first)).isEqualTo(1);
            now.addAndGet(9_000);
            assertThat(service.getRemainingAttempts(first)).isEqualTo(3);
            fail(first, 1);
            assertThat(service.getRemainingAttempts(first)).isEqualTo(2);
        }

        @Test
        @DisplayName("Each distinct-account entry expires from its own latest failure")
        void distinctMembershipsExpireIndividually() throws Exception {
            Player first = player("10.0.0.1");
            fail(first, 1);
            now.addAndGet(5_000);
            Player second = player("10.0.0.1");
            fail(second, 1);
            now.addAndGet(4_000);
            fail(first, 1);
            now.addAndGet(6_000);
            fail(player("10.0.0.1"), 1);
            assertThat(service.isLocked(first)).isFalse();
            fail(player("10.0.0.1"), 1);
            assertThat(service.isLocked(first)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"UUID", "BOTH"})
        @DisplayName("Unlocked pair and UUID counters do not expire with time")
        void unlockedAccountCountersDoNotExpire(String mode) throws Exception {
            when(config.getLockoutType()).thenReturn(mode);
            Player first = player("10.0.0.1");
            fail(first, 2);
            now.addAndGet(100_000);
            assertThat(service.getRemainingAttempts(first)).isEqualTo(1);
            fail(first, 1);
            assertThat(service.isLocked(first)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"IP", "UUID", "BOTH"})
        @DisplayName("Lock expiry resets its count even when remaining attempts is read first")
        void expiredLockResetsItsCount(String mode) throws Exception {
            when(config.getLockoutType()).thenReturn(mode);
            Player first = player("10.0.0.1");
            fail(first, 3);
            now.addAndGet(10_000);
            assertThat(service.getRemainingAttempts(first)).isEqualTo(3);
            assertThat(service.isLocked(first)).isFalse();
            fail(first, 1);
            assertThat(service.getRemainingAttempts(first)).isEqualTo(2);
        }

        @Test
        @DisplayName("Expired address locks reset their distinct-account collection")
        void expiredAddressLockResetsDistinctAccounts() throws Exception {
            for (int i = 0; i < 3; i++) { fail(player("10.0.0.1"), 1); }
            assertThat(service.isLocked(player("10.0.0.1"))).isTrue();
            now.addAndGet(10_000);
            fail(player("10.0.0.1"), 1);
            assertThat(service.isLocked(player("10.0.0.1"))).isFalse();
        }
    }

    @Test
    @DisplayName("Panel and other completed logins use the same mode-specific success reset")
    void completedLoginClearsOnlyOwnPair() throws Exception {
        Player first = player("10.0.0.1");
        fail(first, 2);
        service.completeLogin(first);
        assertThat(service.getRemainingAttempts(first)).isEqualTo(3);
        fail(player("10.0.0.1"), 1);
        fail(player("10.0.0.1"), 1);
        assertThat(service.isLocked(first)).isTrue();
    }

    @Nested
    @DisplayName("Mode-independent invariants")
    class Invariants {
        @ParameterizedTest
        @ValueSource(strings = {"IP", "UUID", "BOTH"})
        @DisplayName("Another account's success never lifts or shortens an existing lock")
        void anotherSuccessDoesNotShortenLock(String mode) throws Exception {
            when(config.getLockoutType()).thenReturn(mode);
            Player first = player("10.0.0.1");
            fail(first, 3);
            now.addAndGet(2_000);
            Player other = player("IP".equals(mode) ? "10.0.0.2" : "10.0.0.1");
            assertThat(service.login(other, "password123").isSuccess()).isTrue();
            assertThat(service.isLocked(first)).isTrue();
            assertThat(service.getRemainingLockoutTime(first)).isEqualTo(8);
            now.addAndGet(8_000);
            assertThat(service.isLocked(first)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"IP", "UUID", "BOTH"})
        @DisplayName("Unlimited attempts never count, lock, or emit a locked reply")
        void zeroMeansUnlimited(String mode) throws Exception {
            when(config.getLockoutType()).thenReturn(mode);
            when(config.getMaxLoginAttempts()).thenReturn(0);
            Player first = player("10.0.0.1");
            for (int i = 0; i < 25; i++) {
                assertThat(service.login(first, "wrong").getMessage()).doesNotContain("LOCKED");
            }
            assertThat(service.getRemainingAttempts(first)).isEqualTo(-1);
            assertThat(service.isLocked(first)).isFalse();
            when(config.getMaxLoginAttempts()).thenReturn(3);
            assertThat(service.getRemainingAttempts(first)).isEqualTo(3);
            assertThat(service.login(first, "password123").isSuccess()).isTrue();
        }
    }
}
