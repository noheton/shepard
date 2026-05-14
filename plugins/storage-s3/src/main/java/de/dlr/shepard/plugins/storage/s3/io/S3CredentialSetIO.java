package de.dlr.shepard.plugins.storage.s3.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * FS1b — response body for a successful
 * {@code POST /v2/admin/storage/s3/credential} call.
 *
 * <p>Returns only the fingerprint (first 8 hex of the SHA-256 of
 * the secret access key) and a boolean indicating the credential is
 * now set. The plaintext is never echoed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record S3CredentialSetIO(boolean secretKeySet, String secretKeyFingerprint) {}
