package de.dlr.shepard.v2.file.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PV1a — wire shape for one {@link de.dlr.shepard.data.file.entities.PayloadVersion}
 * record returned by
 * {@code GET /v2/file-containers/{containerAppId}/files/{originalName}/versions}.
 *
 * @param appId         UUID v7 identifier of this version node.
 * @param versionNumber Monotonically-increasing counter scoped to
 *                      {@code (containerAppId, originalName)}.
 *                      Version 1 is the first upload.
 * @param fileOid       GridFS ObjectId hex of the stored bytes.
 *                      {@code null} for files uploaded via presigned URL path.
 * @param sha256        SHA-256 upper-case hex digest of the uploaded bytes.
 *                      {@code null} when the digest was not computed (legacy
 *                      or presigned-URL path).
 * @param sizeBytes     Byte count of the uploaded payload; {@code null} when
 *                      the storage backend could not determine the size.
 * @param uploadedBy    Username of the caller who triggered the upload.
 * @param uploadedAt    ISO-8601 UTC timestamp of the upload, e.g.
 *                      {@code "2026-05-17T12:34:56Z"}.
 */
@Schema(description = "One byte-level version entry for a file stored in a FileContainer.")
public record PayloadVersionIO(
  @Schema(description = "UUID v7 identifier of this version node.", example = "018f4e2a-1b2c-7d3e-8f4a-5b6c7d8e9f0a")
  String appId,

  @Schema(description = "Monotonically-increasing version counter (1 = first upload).", example = "1")
  long versionNumber,

  @Schema(
    description = "GridFS ObjectId hex of the stored bytes. Null for presigned-URL uploads.",
    example = "60b73212cfa45d2d5baa795d",
    nullable = true
  )
  String fileOid,

  @Schema(
    description = "SHA-256 upper-case hex digest of the uploaded bytes. Null for legacy or presigned-URL uploads.",
    example = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
    nullable = true
  )
  String sha256,

  @Schema(description = "Byte count of the uploaded payload.", example = "4096", nullable = true)
  Long sizeBytes,

  @Schema(description = "Username of the caller who triggered the upload.", example = "jane.doe")
  String uploadedBy,

  @Schema(
    description = "ISO-8601 UTC timestamp of the upload.",
    example = "2026-05-17T12:34:56Z"
  )
  String uploadedAt
) {}
