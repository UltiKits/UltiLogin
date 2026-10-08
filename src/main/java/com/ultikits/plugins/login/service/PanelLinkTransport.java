package com.ultikits.plugins.login.service;

import com.google.gson.JsonObject;
import com.ultikits.ultitools.utils.SimpleHttpClient;

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
     */
    SimpleHttpClient.Response create(JsonObject body);

    /**
     * Poll the Worker once for the outcome of a link.
     *
     * @param requestId  the plugin's own id of the request being polled
     * @param playerUuid the player the request was made for
     * @return the HTTP response
     */
    SimpleHttpClient.Response poll(String requestId, UUID playerUuid);
}
