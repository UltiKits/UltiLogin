package com.ultikits.plugins.login.service;

import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.websocket.CloudPlayerLinks;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The production {@link PlayerLinkPort}: the framework's player-link API, {@link CloudPlayerLinks} (UltiTools-API
 * 6.3.1), reached through {@link UltiTools#getCloudPlayerLinks()} at call time. The only class in this module that
 * names the framework's player-link types.
 * <p>
 * The game-auth facts are always sent in the auth-plugin form: this module is the server's login plugin. The
 * framework fills in the server's online mode itself. The conversion never blocks and never throws: a framework that
 * has not enabled yet answers {@link LinkFailure#NOT_CONNECTED}, and any exception, either thrown by the create call
 * or completing its future, answers {@link LinkFailure#OTHER}.
 * <p>
 * The module's {@link LinkEventHandler} is installed with {@link CloudPlayerLinks#setHandler} with this module as
 * owner, wrapped so the framework only ever sees its own types. A module handler that throws, or answers no future,
 * gives the framework a failed future, which sends no result: the gateway redelivers the completion later.
 */
final class FrameworkPlayerLinkPort implements PlayerLinkPort {

    private final UltiToolsPlugin module;

    /**
     * @param module this module, passed to the framework as the requester of every link
     */
    FrameworkPlayerLinkPort(UltiToolsPlugin module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public CompletableFuture<LinkCreation> create(UUID playerUuid, String playerName, boolean authenticated,
                                                  boolean accountExists, Set<LinkProof> offeredProofs) {
        try {
            CloudPlayerLinks links = links();
            if (links == null) {
                return CompletableFuture.completedFuture(
                        LinkCreation.failed(LinkFailure.NOT_CONNECTED, "UltiTools player links are not available"));
            }
            CompletableFuture<CloudPlayerLinks.LinkCreation> answer = links.create(module, playerUuid, playerName,
                    CloudPlayerLinks.GameAuth.authPlugin(authenticated, accountExists), toFramework(offeredProofs));
            if (answer == null) {
                return CompletableFuture.completedFuture(
                        LinkCreation.failed(LinkFailure.OTHER, "the framework returned no answer"));
            }
            return answer.handle((creation, error) -> error != null
                    ? LinkCreation.failed(LinkFailure.OTHER, describe(error))
                    : fromFramework(creation));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(LinkCreation.failed(LinkFailure.OTHER, describe(e)));
        }
    }

    @Override
    public boolean registerHandler(LinkEventHandler handler) {
        Objects.requireNonNull(handler, "handler");
        try {
            CloudPlayerLinks links = links();
            if (links == null) {
                return false;
            }
            links.setHandler(module, new Bridge(handler));
            return true;
        } catch (RuntimeException e) {
            // Another module owns the handler (IllegalStateException), or the framework refused it otherwise.
            return false;
        }
    }

    @Override
    public void unregisterHandler() {
        try {
            CloudPlayerLinks links = links();
            if (links != null) {
                links.removeHandler(module);
            }
        } catch (RuntimeException e) {
            // Unloading: the framework releases a module's handler on unload anyway.
        }
    }

    private static CloudPlayerLinks links() {
        UltiTools framework = UltiTools.getInstance();
        return framework == null ? null : framework.getCloudPlayerLinks();
    }

    /** The framework-side handler: translates each event into the module's types and the answer back. */
    private static final class Bridge implements CloudPlayerLinks.LinkEventHandler {
        private final LinkEventHandler handler;

        private Bridge(LinkEventHandler handler) {
            this.handler = handler;
        }

        @Override
        public CompletableFuture<CloudPlayerLinks.GameAccountResult> onCompleted(
                CloudPlayerLinks.LinkCompletion completion) {
            CompletableFuture<CompletionResult> answer;
            try {
                answer = handler.onCompleted(new LinkCompletion(completion.completionId(), completion.linkId(),
                        LinkProof.valueOf(completion.proof().name()), completion.playerUuid(),
                        completion.playerName(), completion.provisionGameAccount(), completion.webUsername()));
            } catch (RuntimeException e) {
                return failed(e);
            }
            if (answer == null) {
                return failed(new IllegalStateException("the module handler answered no future"));
            }
            return answer.thenApply(result -> CloudPlayerLinks.GameAccountResult.valueOf(result.name()));
        }

        @Override
        public void onExpired(CloudPlayerLinks.LinkExpiry expiry) {
            handler.onExpired(new LinkExpiry(expiry.linkId(), expiry.playerUuid(), reasonOf(expiry.reason())));
        }

        private static <T> CompletableFuture<T> failed(Throwable error) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(error);
            return future;
        }
    }

    private static LinkExpiry.Reason reasonOf(CloudPlayerLinks.ExpiryReason reason) {
        switch (reason) {
            case EXPIRED:
                return LinkExpiry.Reason.EXPIRED;
            case CANCELLED:
                return LinkExpiry.Reason.CANCELLED;
            case DENIED:
                return LinkExpiry.Reason.DENIED;
            default:
                return LinkExpiry.Reason.OTHER;
        }
    }

    private static Set<CloudPlayerLinks.LinkProof> toFramework(Set<LinkProof> proofs) {
        Set<CloudPlayerLinks.LinkProof> converted = EnumSet.noneOf(CloudPlayerLinks.LinkProof.class);
        for (LinkProof proof : proofs) {
            converted.add(CloudPlayerLinks.LinkProof.valueOf(proof.name()));
        }
        return converted;
    }

    private static LinkCreation fromFramework(CloudPlayerLinks.LinkCreation creation) {
        if (creation == null) {
            return LinkCreation.failed(LinkFailure.OTHER, "the framework answered nothing");
        }
        if (creation.failure() != null) {
            return LinkCreation.failed(failureOf(creation.failure()), creation.failure() + ": " + creation.message());
        }
        Set<LinkProof> accepted = EnumSet.noneOf(LinkProof.class);
        for (CloudPlayerLinks.LinkProof proof : creation.acceptedProofs()) {
            accepted.add(LinkProof.valueOf(proof.name()));
        }
        return LinkCreation.created(creation.linkId(), creation.url(), creation.expiresAt(), accepted);
    }

    private static LinkFailure failureOf(CloudPlayerLinks.Failure failure) {
        switch (failure) {
            case NOT_CONNECTED:
                return LinkFailure.NOT_CONNECTED;
            case RATE_LIMITED:
                return LinkFailure.RATE_LIMITED;
            case LINK_NOT_PERMITTED:
                return LinkFailure.NOT_PERMITTED;
            case TIMED_OUT:
                return LinkFailure.TIMED_OUT;
            default:
                return LinkFailure.OTHER;
        }
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
