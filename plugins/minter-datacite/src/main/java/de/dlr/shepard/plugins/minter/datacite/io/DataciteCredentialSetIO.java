package de.dlr.shepard.plugins.minter.datacite.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * KIP1d — response body for
 * {@code POST /v2/admin/minters/datacite/credential}.
 *
 * <p>Returns only {@code passwordSet} + a masked fingerprint. The
 * plaintext is intentionally not echoed (the operator already had
 * it; sending it back would just widen the leak surface).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataciteCredentialSetIO(boolean passwordSet, String fingerprint) {}
