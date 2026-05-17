package de.dlr.shepard.plugins.minter.epic.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * KIP1c — request body for
 * {@code POST /v2/admin/minters/epic/credential}.
 *
 * <p>The plaintext credential is sent in the body — operators run
 * this over HTTPS only. The {@code ProvenanceCaptureFilter} captures
 * only request method + path + status, NOT the body, so the plaintext
 * never enters the {@code :Activity} audit trail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EpicCredentialIO(String credential) {}
