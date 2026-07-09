package de.dlr.shepard.plugins.unhide.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * UH1a — response body for
 * {@code POST /v2/admin/unhide/harvest-key/rotate}.
 *
 * <p>Carries the freshly-minted harvest API key in plaintext —
 * <b>returned exactly once</b>. The body is not captured in
 * {@code :Activity}: PROV1a's {@code ProvenanceCaptureFilter}
 * records request path + method + status only, never response
 * bodies, so the plaintext never enters the audit trail.
 *
 * <p>The {@link #warning} field reminds the operator that the key
 * cannot be re-fetched once this response is dismissed; a CLI run
 * or `curl` invocation should pipe it to a secret-manager
 * immediately.
 *
 * <p>{@link #fingerprint} echoes the first 8 hex chars of the
 * SHA-256 so the operator can independently confirm the
 * later-returned masked fingerprint on {@code GET .../config}
 * matches what they just received.
 */
@Schema(name = "HarvestKeyMintedIO", description = "Response body for POST /v2/admin/unhide/harvest-key/rotate — newly minted harvest API key (shown once).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HarvestKeyMintedIO(
  String harvestApiKey,
  String fingerprint,
  Date mintedAt,
  String warning
) {
  /** Default warning message — surfaced verbatim in CLI human output. */
  public static final String WARNING =
    "This is the only time this harvest API key is shown. Save it now; " +
    "if lost, use POST /v2/admin/unhide/harvest-key/rotate to mint a new one.";
}
