package com.ultikits.plugins.login.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The module's seam over the framework's UltiCloud player-link API: {@code /panel} asks for a one-time link through
 * this interface only, so {@link LoginService} never names a framework type and its tests use a fake.
 * <p>
 * Production uses {@link FrameworkPlayerLinkPort}. A create carries the player's UUID and name, the two facts this
 * login plugin knows about the player, and the identity proofs this server can support; it never carries a password
 * or a password digest.
 * <p>
 * Link outcomes arrive as events at the one {@link LinkEventHandler} the module installs with
 * {@link #registerHandler}: every {@code completed} (at least once, redelivered after a reconnect or a restart until
 * it is answered) and every {@code expired} (at most once). The handler is called off the server's main thread.
 */
interface PlayerLinkPort {

    /**
     * Ask for a one-time link for one player, without blocking.
     *
     * @param playerUuid    the player's Minecraft UUID
     * @param playerName    the player's name
     * @param authenticated whether the player is logged in to UltiLogin on this connection
     * @param accountExists whether UltiLogin holds an account for this player
     * @param offeredProofs the identity proofs this server supports for the link
     * @return a future that always completes normally, with a link or a failure; never null
     */
    CompletableFuture<LinkCreation> create(UUID playerUuid, String playerName, boolean authenticated,
                                           boolean accountExists, Set<LinkProof> offeredProofs);

    /**
     * Install the module's handler for the completions and expiries of this server's links, replacing a handler this
     * module installed before. Never throws.
     *
     * @param handler the handler
     * @return {@code false} when the framework cannot take it (not enabled, or another module owns the handler)
     */
    boolean registerHandler(LinkEventHandler handler);

    /** Remove the handler this module installed; harmless when none is installed. Never throws. */
    void unregisterHandler();

    /**
     * Receives the outcomes of this server's links. Both methods may be called on any thread except the main thread.
     */
    interface LinkEventHandler {

        /**
         * A web user completed a link. Called at least once per completion: the same completion (same
         * {@link LinkCompletion#completionId()}) may arrive again until its result is recorded.
         *
         * @param completion what the gateway reported
         * @return the result, completed once the module has acted; a future that fails sends no result, so the
         *         gateway asks again later
         */
        CompletableFuture<CompletionResult> onCompleted(LinkCompletion completion);

        /**
         * A link stopped being usable without completing; at most once per link, and never for a link whose expiry
         * time simply passed.
         *
         * @param expiry the link, its player and the reason
         */
        void onExpired(LinkExpiry expiry);
    }

    /**
     * What the module did about a game account for a completed link: the contract's two success values
     * ({@code created}, {@code not_requested}) and its three error codes.
     */
    enum CompletionResult {
        /** A game account was created for the player. */
        CREATED,
        /** No game account was requested, or none was needed. */
        NOT_REQUESTED,
        /** An account was requested, but the player already has one; it was left unchanged. */
        ACCOUNT_EXISTS,
        /** This server's own rules refused the account, such as {@code max-register-per-ip}. */
        REGISTRATION_REJECTED,
        /** The login plugin cannot create the account now: integration disabled, or the database write failed. */
        AUTH_PLUGIN_UNAVAILABLE
    }

    /** One completed link: how the web user proved the game identity, and whether to create a game account. */
    final class LinkCompletion {
        private final String completionId;
        private final String linkId;
        private final LinkProof proof;
        private final UUID playerUuid;
        private final String playerName;
        private final boolean provisionGameAccount;
        private final String webUsername;

        /**
         * @param completionId         the completion's own envelope id, the same on every redelivery
         * @param linkId               the id of the link that completed, as its create answered
         * @param proof                the proof the web user gave
         * @param playerUuid           the player's UUID
         * @param playerName           the player's name
         * @param provisionGameAccount whether the module must create a game account
         * @param webUsername          the UltiKits username for a web registration, otherwise {@code null}
         */
        LinkCompletion(String completionId, String linkId, LinkProof proof, UUID playerUuid, String playerName,
                       boolean provisionGameAccount, String webUsername) {
            this.completionId = Objects.requireNonNull(completionId, "completionId");
            this.linkId = Objects.requireNonNull(linkId, "linkId");
            this.proof = Objects.requireNonNull(proof, "proof");
            this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
            this.playerName = Objects.requireNonNull(playerName, "playerName");
            this.provisionGameAccount = provisionGameAccount;
            this.webUsername = webUsername;
        }

        String completionId() {
            return completionId;
        }

        String linkId() {
            return linkId;
        }

        LinkProof proof() {
            return proof;
        }

        UUID playerUuid() {
            return playerUuid;
        }

        String playerName() {
            return playerName;
        }

        boolean provisionGameAccount() {
            return provisionGameAccount;
        }

        String webUsername() {
            return webUsername;
        }

        @Override
        public String toString() {
            return "LinkCompletion{completionId=" + completionId + ", linkId=" + linkId + ", proof=" + proof
                    + ", playerUuid=" + playerUuid + ", provisionGameAccount=" + provisionGameAccount + "}";
        }
    }

    /** One link that stopped being usable without completing. */
    final class LinkExpiry {
        /** Why the link stopped being usable. */
        enum Reason {
            /** Nobody completed it in time. */
            EXPIRED,
            /** A newer {@code /panel} replaced it, or the plugin or the player cancelled it. */
            CANCELLED,
            /** The web user refused the confirmation page. */
            DENIED,
            /** A reason this module does not know. */
            OTHER
        }

        private final String linkId;
        private final UUID playerUuid;
        private final Reason reason;

        /**
         * @param linkId     the id of the link that ended
         * @param playerUuid the player it was created for
         * @param reason     why it ended
         */
        LinkExpiry(String linkId, UUID playerUuid, Reason reason) {
            this.linkId = Objects.requireNonNull(linkId, "linkId");
            this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        String linkId() {
            return linkId;
        }

        UUID playerUuid() {
            return playerUuid;
        }

        Reason reason() {
            return reason;
        }

        @Override
        public String toString() {
            return "LinkExpiry{linkId=" + linkId + ", playerUuid=" + playerUuid + ", reason=" + reason + "}";
        }
    }

    /** An identity proof the server side can support for one link; the gateway decides which it accepts. */
    enum LinkProof {
        /** The player is logged in in game on the connection that asked for the link. */
        IN_GAME_SESSION,
        /** The web user authenticates to the UltiKits account already bound to this game identity. */
        LINKED_WEB_ACCOUNT,
        /** The web user registers a new UltiKits account for a name UltiLogin has no account for. */
        WEB_REGISTRATION
    }

    /** Why a link was not created. */
    enum LinkFailure {
        /** This server holds no usable UltiCloud connection; nothing was sent. */
        NOT_CONNECTED,
        /** The gateway rate-limited the request; a later {@code /panel} may succeed. */
        RATE_LIMITED,
        /** The gateway refuses to issue a link here: server suspended, links disabled, or identity banned. */
        NOT_PERMITTED,
        /** No answer arrived in time. */
        TIMED_OUT,
        /** Anything else, including a framework call that failed. */
        OTHER
    }

    /** The answer to one {@link #create}: a link, or a failure with a short reason for the log. */
    final class LinkCreation {
        private final String linkId;
        private final String url;
        private final Instant expiresAt;
        private final Set<LinkProof> acceptedProofs;
        private final LinkFailure failure;
        private final String reason;

        private LinkCreation(String linkId, String url, Instant expiresAt, Set<LinkProof> acceptedProofs,
                             LinkFailure failure, String reason) {
            this.linkId = linkId;
            this.url = url;
            this.expiresAt = expiresAt;
            this.acceptedProofs = acceptedProofs;
            this.failure = failure;
            this.reason = reason;
        }

        /**
         * A created link.
         *
         * @param linkId         the id later completion and expiry events carry for this link
         * @param url            the one-time URL to show the player
         * @param expiresAt      when the link stops being usable, on this server's clock
         * @param acceptedProofs the proofs the gateway accepted
         * @return the answer
         */
        static LinkCreation created(String linkId, String url, Instant expiresAt, Collection<LinkProof> acceptedProofs) {
            EnumSet<LinkProof> accepted = EnumSet.noneOf(LinkProof.class);
            accepted.addAll(acceptedProofs);
            return new LinkCreation(Objects.requireNonNull(linkId, "linkId"), Objects.requireNonNull(url, "url"),
                    Objects.requireNonNull(expiresAt, "expiresAt"), Collections.unmodifiableSet(accepted), null, null);
        }

        /**
         * A failed create.
         *
         * @param failure why it failed
         * @param reason  a short, credential-free reason for the console log
         * @return the answer
         */
        static LinkCreation failed(LinkFailure failure, String reason) {
            return new LinkCreation(null, null, null, Collections.<LinkProof>emptySet(),
                    Objects.requireNonNull(failure, "failure"), String.valueOf(reason));
        }

        boolean isSuccess() {
            return failure == null;
        }

        /** @return the link id, or {@code null} for a failure */
        String linkId() {
            return linkId;
        }

        /** @return the one-time URL, or {@code null} for a failure */
        String url() {
            return url;
        }

        /** @return the expiry, or {@code null} for a failure */
        Instant expiresAt() {
            return expiresAt;
        }

        /** @return the accepted proofs, empty for a failure */
        Set<LinkProof> acceptedProofs() {
            return acceptedProofs;
        }

        /** @return why the create failed, or {@code null} for a link */
        LinkFailure failure() {
            return failure;
        }

        /** @return the reason for the log, or {@code null} for a link */
        String reason() {
            return reason;
        }

        @Override
        public String toString() {
            return isSuccess()
                    ? "LinkCreation{linkId=" + linkId + ", expiresAt=" + expiresAt + ", acceptedProofs=" + acceptedProofs + "}"
                    : "LinkCreation{failure=" + failure + ", reason=" + reason + "}";
        }
    }
}
