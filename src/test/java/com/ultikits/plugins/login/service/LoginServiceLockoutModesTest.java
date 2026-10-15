package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.config.LoginConfig;
import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

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
                .digest((salt + "password123").getBytes(StandardCharsets.UTF_8));
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
    }
}
