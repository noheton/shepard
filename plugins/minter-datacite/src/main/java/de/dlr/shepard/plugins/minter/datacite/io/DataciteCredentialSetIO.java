package de.dlr.shepard.plugins.minter.datacite.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * KIP1d — response body for
 * {@code POST /v2/admin/minters/datacite/credential}.
 *
 * <p>Returns only {@code passwordSet} + a masked fingerprint. The
 * plaintext is intentionally not echoed (the operator already had
 * it; sending it back would just widen the leak surface).
 */
@Schema(name = "DataciteCredentialSetIO", description = "Response body for POST /v2/admin/minters/datacite/credential — confirms password was stored.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataciteCredentialSetIO(boolean passwordSet, String fingerprint) {}
