package de.dlr.shepard.plugins.storage.s3.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * FS1b — request body for
 * {@code POST /v2/admin/storage/s3/credential}.
 *
 * <p>The plaintext secretKey is sent in the body — operators run
 * this over HTTPS only. The {@code ProvenanceCaptureFilter} captures
 * only request method + path + status, NOT the body, so the plaintext
 * never enters the {@code :Activity} audit trail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record S3CredentialIO(String accessKeyId, String secretKey) {}
