package de.dlr.shepard.plugins.minter.epic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * KIP1c — response body for
 * {@code POST /v2/admin/minters/epic/credential}.
 *
 * <p>Returns only {@code credentialSet} + a masked fingerprint. The
 * plaintext is intentionally not echoed (the operator already had
 * it; sending it back would just widen the leak surface).
 */
@Schema(name = "EpicCredentialSetIO", description = "Response body for POST /v2/admin/minters/epic/credential — confirms credential was stored.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EpicCredentialSetIO(boolean credentialSet, String fingerprint) {}
