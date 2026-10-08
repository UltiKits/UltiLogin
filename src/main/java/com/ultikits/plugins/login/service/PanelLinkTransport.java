package com.ultikits.plugins.login.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.utils.SimpleHttpClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The HTTP seam between {@code /panel} and the UltiCloud Worker: one place that sends the magic-link
 * create request and the completion poll, so the transport can change without touching the outcome
 * handling in {@link LoginService}.
 * <p>
 * Every implementation returns the raw HTTP exchange; {@link LoginService} interprets the status code
 * and the body.
 */
interface PanelLinkTransport {

    /**
     * Send {@code POST /auth/magic-link} with {@code body} as its JSON payload.
     *
     * @param body the create request body (camelCase keys)
     * @return the HTTP response
     * @throws NotConfiguredException when the UltiCloud API address is not configured
     */
    SimpleHttpClient.Response create(JsonObject body);

    /**
     * Poll the Worker once for the outcome of a link.
     *
     * @param requestId  the plugin's own id of the request being polled
     * @param playerUuid the player the request was made for
     * @return the HTTP response
     * @throws NotConfiguredException when the UltiCloud API address is not configured
     */
    SimpleHttpClient.Response poll(String requestId, UUID playerUuid);

    /**
     * Whether a link this transport created carries the server owner's UltiCloud credential, so the
     * Worker honoured the facts {@code /panel} reported and a {@code proof} in its poll is trusted
     * (Phase 18 magic-link contract, sections 2 and 14). The anonymous transport never is.
     *
     * @return {@code true} only for the transport that goes through the framework's credential
     */
    default boolean credentialed() {
        return false;
    }

    /**
     * The UltiCloud API address ({@code api-url} in the framework's environment) cannot be read, so no
     * request was sent.
     */
    final class NotConfiguredException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotConfiguredException(Throwable cause) {
            super("API URL not configured", cause);
        }
    }

    /**
     * The server is not logged in to UltiCloud, so the credentialed transport made no request. This is
     * the only signal on which {@code /panel} falls back to the anonymous request (contract section 13).
     */
    final class NotConnectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotConnectedException() {
            super("Server not logged in to UltiCloud");
        }
    }

    /**
     * The Worker answered the credentialed create with 401 or 403: the server's credential is invalid
     * or does not own this server. Never retried anonymously, so a broken credential surfaces instead
     * of hiding behind the old-plugin track (contract sections 2 and 13).
     */
    final class CredentialRefusedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int status;

        CredentialRefusedException(int status) {
            super("Server credential refused (HTTP " + status + ")");
            this.status = status;
        }

        int getStatus() {
            return status;
        }
    }

    /**
     * The credentialed transport could not complete the exchange (the helper reported an I/O error or
     * refused the path). Shown as a failure and never retried anonymously (contract section 13).
     */
    final class LinkFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        LinkFailedException(String reason) {
            super("Panel link request failed: " + reason);
        }
    }

    /**
     * The request {@code /panel} has always made: no server credential attached, and the legacy poll
     * keyed by player UUID, which reports only {@code pending} and {@code completed}.
     */
    final class Anonymous implements PanelLinkTransport {

        private final Gson gson = new Gson();

        @Override
        public SimpleHttpClient.Response create(JsonObject body) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            return SimpleHttpClient.post(apiUrl() + "/auth/magic-link", headers, gson.toJson(body));
        }

        @Override
        public SimpleHttpClient.Response poll(String requestId, UUID playerUuid) {
            return SimpleHttpClient.get(apiUrl() + "/auth/magic-link/poll?playerUuid=" + playerUuid);
        }

        private static String apiUrl() {
            try {
                return UltiTools.getEnv().getString("api-url");
            } catch (Exception e) {
                throw new NotConfiguredException(e);
            }
        }
    }
}
