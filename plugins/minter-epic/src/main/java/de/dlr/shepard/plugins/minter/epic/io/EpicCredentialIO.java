package de.dlr.shepard.plugins.minter.epic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * KIP1c — request body for
 * {@code POST /v2/admin/minters/epic/credential}.
 *
 * <p>The plaintext credential is sent in the body — operators run
 * this over HTTPS only. The {@code ProvenanceCaptureFilter} captures
 * only request method + path + status, NOT the body, so the plaintext
 * never enters the {@code :Activity} audit trail.
 */
@Schema(name = "EpicCredentialIO", description = "Request body for POST /v2/admin/minters/epic/credential — sets the ePIC handle-server credential.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EpicCredentialIO(String credential) {}
