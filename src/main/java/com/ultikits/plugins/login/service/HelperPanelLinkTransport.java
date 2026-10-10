package com.ultikits.plugins.login.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ultikits.ultitools.utils.SimpleHttpClient;
import com.ultikits.ultitools.utils.UltiCloudRequests;

import java.util.Collections;
import java.util.UUID;

/**
 * {@code /panel} with the server's own UltiCloud credential, through the framework helper
 * {@link UltiCloudRequests} (Phase 18 magic-link contract, sections 12 and 13).
 * <p>
 * The framework attaches the server owner's bearer to the one connection it opens; this module never
 * sees, reads or stores the token. Create is {@code POST /auth/magic-link}; the poll is
 * {@code GET /auth/magic-link/poll?requestId=...}, which reports every outcome and the proof.
 * <p>
 * How the helper's results map:
 * <ul>
 *   <li>create, {@code OK} with 401 or 403 &rarr; {@link PanelLinkTransport.CredentialRefusedException};
 *       any other status is returned for {@link LoginService} to read;</li>
 *   <li>create, {@code NOT_CONNECTED} (the server is not logged in to UltiCloud) &rarr;
 *       {@link PanelLinkTransport.NotConnectedException}, the only case in which {@code /panel} falls
 *       back to the anonymous request;</li>
 *   <li>create, {@code IO_ERROR} or {@code PATH_NOT_ALLOWED} &rarr;
 *       {@link PanelLinkTransport.LinkFailedException}, shown as a failure, never retried anonymously;</li>
 *   <li>poll, anything but {@code OK} &rarr; a non-2xx response, so the poll goes on silently until the
 *       link's lifetime is up; a credentialed link is never switched to the legacy poll.</li>
 * </ul>
 * The helper refuses to run on the server's primary thread; {@code /panel}'s create and its poll both
 * run on asynchronous tasks.
 */
final class HelperPanelLinkTransport implements PanelLinkTransport {

    static final String CREATE_PATH = "/auth/magic-link";
    static final String POLL_PATH = "/auth/magic-link/poll";

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    /** Status of a poll answer that carried no HTTP exchange; never 2xx, so the poll loop ignores it. */
    private static final int NO_EXCHANGE = -1;

    private final Gson gson = new Gson();

    @Override
    public boolean credentialed() {
        return true;
    }

    @Override
    public SimpleHttpClient.Response create(JsonObject body) {
        UltiCloudRequests.Result result = UltiCloudRequests.post(CREATE_PATH, gson.toJson(body));
        UltiCloudRequests.Outcome outcome = result.getOutcome();
        if (outcome == UltiCloudRequests.Outcome.OK) {
            int status = result.getStatusCode();
            if (status == UNAUTHORIZED || status == FORBIDDEN) {
                throw new PanelLinkTransport.CredentialRefusedException(status);
            }
            return new SimpleHttpClient.Response(status, result.getBody());
        }
        if (outcome == UltiCloudRequests.Outcome.NOT_CONNECTED) {
            throw new PanelLinkTransport.NotConnectedException();
        }
        throw new PanelLinkTransport.LinkFailedException(String.valueOf(outcome));
    }

    @Override
    public SimpleHttpClient.Response poll(String requestId, UUID playerUuid) {
        UltiCloudRequests.Result result =
                UltiCloudRequests.get(POLL_PATH, Collections.singletonMap("requestId", requestId));
        if (result.getOutcome() == UltiCloudRequests.Outcome.OK) {
            return new SimpleHttpClient.Response(result.getStatusCode(), result.getBody());
        }
        return new SimpleHttpClient.Response(NO_EXCHANGE, null);
    }
}
