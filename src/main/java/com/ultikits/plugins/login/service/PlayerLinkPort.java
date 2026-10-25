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
