package de.dlr.shepard.plugins.minter.datacite.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * KIP1d — request body for
 * {@code POST /v2/admin/minters/datacite/credential}.
 *
 * <p>The plaintext password is sent in the body — operators run
 * this over HTTPS only (the in-tree security gates flag plain HTTP
 * admin calls). The {@code ProvenanceCaptureFilter} captures only
 * request method + path + status, NOT the body, so the plaintext
 * never enters the {@code :Activity} audit trail.
 */
@Schema(name = "DataciteCredentialIO", description = "Request body for POST /v2/admin/minters/datacite/credential — sets the DataCite repository password.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataciteCredentialIO(String password) {}
