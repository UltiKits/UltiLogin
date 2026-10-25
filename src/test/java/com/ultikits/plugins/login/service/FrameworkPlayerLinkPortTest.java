package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.UltiLoginTestHelper;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkCreation;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkFailure;
import com.ultikits.plugins.login.service.PlayerLinkPort.LinkProof;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.websocket.CloudPlayerLinks;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The adapter between the module's {@link PlayerLinkPort} and the framework's {@link CloudPlayerLinks}: the
 * create carries only the player's UUID, name, the two game-auth facts and the offered proofs, and every framework
 * outcome maps to exactly one {@link LinkCreation} without ever throwing to the caller.
 * <p>
 * A real {@link CloudPlayerLinks} runs over a mocked {@link CloudPlayerLinks.Transport}, so the framework's own
 * argument checks are part of what is exercised.
 */
@DisplayName("FrameworkPlayerLinkPort")
class FrameworkPlayerLinkPortTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant EXPIRES = Instant.parse("2026-10-26T12:10:00Z");

    private MockedStatic<UltiTools> ultiTools;
    private UltiTools framework;
    private CloudPlayerLinks.Transport transport;
    private UltiToolsPlugin module;
    private FrameworkPlayerLinkPort port;

    @BeforeEach
    void setUp() throws Exception {
        UltiLoginTestHelper.setUp();
        module = UltiLoginTestHelper.getMockPlugin();
        transport = mock(CloudPlayerLinks.Transport.class);
        framework = mock(UltiTools.class);
        when(framework.getCloudPlayerLinks()).thenReturn(new CloudPlayerLinks(transport));
        ultiTools = mockStatic(UltiTools.class);
        ultiTools.when(UltiTools::getInstance).thenReturn(framework);
        port = new FrameworkPlayerLinkPort(module);
    }

    @AfterEach
    void tearDown() throws Exception {
        ultiTools.close();
        UltiLoginTestHelper.tearDown();
    }

    private void answerCreate(CompletableFuture<CloudPlayerLinks.LinkCreation> answer) {
        when(transport.create(any(), any(), anyString(), any(), any())).thenReturn(answer);
    }

    private LinkCreation createNow(boolean authenticated, boolean accountExists, Set<LinkProof> proofs)
            throws Exception {
        return port.create(PLAYER, "Steve", authenticated, accountExists, proofs).get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("success: the framework create receives the module, UUID, name, auth-plugin facts and proofs; the link fields are copied")
    void successCopiesTheLinkAndSendsOnlyTheFacts() throws Exception {
        answerCreate(CompletableFuture.completedFuture(CloudPlayerLinks.LinkCreation.created("L1",
                "https://panel.example/link/L1", EXPIRES,
                Arrays.asList(CloudPlayerLinks.LinkProof.IN_GAME_SESSION, CloudPlayerLinks.LinkProof.WEB_REGISTRATION))));

        LinkCreation creation = createNow(true, false,
                EnumSet.of(LinkProof.IN_GAME_SESSION, LinkProof.LINKED_WEB_ACCOUNT, LinkProof.WEB_REGISTRATION));

        assertThat(creation.isSuccess()).isTrue();
        assertThat(creation.linkId()).isEqualTo("L1");
        assertThat(creation.url()).isEqualTo("https://panel.example/link/L1");
        assertThat(creation.expiresAt()).isEqualTo(EXPIRES);
        assertThat(creation.acceptedProofs()).containsExactlyInAnyOrder(LinkProof.IN_GAME_SESSION,
                LinkProof.WEB_REGISTRATION);
        assertThat(creation.failure()).isNull();

        ArgumentCaptor<CloudPlayerLinks.GameAuth> auth = ArgumentCaptor.forClass(CloudPlayerLinks.GameAuth.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<CloudPlayerLinks.LinkProof>> proofs = ArgumentCaptor.forClass(Set.class);
        verify(transport).create(eq(module), eq(PLAYER), eq("Steve"), auth.capture(), proofs.capture());
        assertThat(auth.getValue().isAuthPlugin()).as("this module is the server's login plugin").isTrue();
        assertThat(auth.getValue().authenticated()).isTrue();
        assertThat(auth.getValue().accountExists()).isFalse();
        assertThat(proofs.getValue()).containsExactlyInAnyOrder(CloudPlayerLinks.LinkProof.IN_GAME_SESSION,
                CloudPlayerLinks.LinkProof.LINKED_WEB_ACCOUNT, CloudPlayerLinks.LinkProof.WEB_REGISTRATION);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "NOT_CONNECTED, NOT_CONNECTED",
            "RATE_LIMITED, RATE_LIMITED",
            "LINK_NOT_PERMITTED, NOT_PERMITTED",
            "TIMED_OUT, TIMED_OUT",
            "INTERNAL_ERROR, OTHER"
    })
    @DisplayName("each framework failure maps to its own module failure, with the framework's safe message as the reason")
    void failuresMap(CloudPlayerLinks.Failure frameworkFailure, LinkFailure expected) throws Exception {
        answerCreate(CompletableFuture.completedFuture(
                CloudPlayerLinks.LinkCreation.failed(frameworkFailure, "reason " + frameworkFailure)));

        LinkCreation creation = createNow(false, true, EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT));

        assertThat(creation.isSuccess()).isFalse();
        assertThat(creation.failure()).isEqualTo(expected);
        assertThat(creation.reason()).contains(frameworkFailure.name());
        assertThat(creation.linkId()).isNull();
        assertThat(creation.url()).isNull();
    }

    @Test
    @DisplayName("a framework future that completes exceptionally maps to OTHER and never throws to the caller")
    void exceptionalFutureMapsToOther() throws Exception {
        CompletableFuture<CloudPlayerLinks.LinkCreation> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("boom"));
        answerCreate(failed);

        LinkCreation creation = createNow(false, true, EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT));

        assertThat(creation.failure()).isEqualTo(LinkFailure.OTHER);
        assertThat(creation.reason()).contains("boom");
    }

    @Test
    @DisplayName("a create call that throws (the framework refusing its arguments) maps to OTHER")
    void throwingCreateMapsToOther() throws Exception {
        when(transport.create(any(), any(), anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException("refused"));

        LinkCreation creation = createNow(false, true, EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT));

        assertThat(creation.failure()).isEqualTo(LinkFailure.OTHER);
    }

    @Test
    @DisplayName("an empty proof set is refused by the framework and maps to OTHER, not to an exception")
    void emptyProofsMapToOther() throws Exception {
        LinkCreation creation = createNow(false, true, Collections.<LinkProof>emptySet());

        assertThat(creation.failure()).isEqualTo(LinkFailure.OTHER);
        verify(transport, never()).create(any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("a null answer future and a null answer both map to OTHER")
    void nullAnswersMapToOther() throws Exception {
        answerCreate(null);
        assertThat(createNow(false, true, EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT)).failure())
                .isEqualTo(LinkFailure.OTHER);

        answerCreate(CompletableFuture.completedFuture(null));
        assertThat(createNow(false, true, EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT)).failure())
                .isEqualTo(LinkFailure.OTHER);
    }

    @Test
    @DisplayName("before the framework has enabled (no instance, or no player-link API) the create is NOT_CONNECTED")
    void noFrameworkIsNotConnected() throws Exception {
        when(framework.getCloudPlayerLinks()).thenReturn(null);
        assertThat(createNow(true, true, EnumSet.of(LinkProof.IN_GAME_SESSION)).failure())
                .isEqualTo(LinkFailure.NOT_CONNECTED);

        ultiTools.when(UltiTools::getInstance).thenReturn(null);
        assertThat(createNow(true, true, EnumSet.of(LinkProof.IN_GAME_SESSION)).failure())
                .isEqualTo(LinkFailure.NOT_CONNECTED);
    }

    @Test
    @DisplayName("the module's result types refuse inconsistent values")
    void resultTypesAreConsistent() {
        LinkCreation created = LinkCreation.created("L2", "https://x", EXPIRES, EnumSet.of(LinkProof.LINKED_WEB_ACCOUNT));
        assertThat(created.toString()).contains("L2").doesNotContain("reason");
        LinkCreation failed = LinkCreation.failed(LinkFailure.RATE_LIMITED, "slow down");
        assertThat(failed.acceptedProofs()).isEmpty();
        assertThat(failed.expiresAt()).isNull();
        assertThat(failed.toString()).contains("RATE_LIMITED");
    }
}
