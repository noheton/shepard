package de.dlr.shepard.plugins.minter.datacite.io;

import com.fasterxml.jackson.annotation.JsonInclude;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataciteCredentialIO(String password) {}
