package de.dlr.shepard.context.references.git.adapters;

import java.util.Objects;

/**
 * Result of a {@link GitAdapter#getFile} call. Immutable record-style
 * carrier; safe to hand to a Quarkus cache (used as a {@code @CacheResult}
 * return type by {@code GitArtifactCache}).
 *
 * @param sha       SHA of the commit that resolved {@code ref} (mode-(b)
 *                  records this as {@code resolvedSha}; pinned-snapshot
 *                  mode-(c) verifies it matches).
 * @param content   Raw file bytes. {@code null} when the file is larger than
 *                  {@code shepard.git.preview.max-bytes} and the adapter
 *                  short-circuits; service layer surfaces this as
 *                  {@code contentTruncated: true}.
 * @param mimeType  Best-guess MIME ({@code text/markdown}, {@code text/x-java-source},
 *                  {@code application/octet-stream} fallback).
 * @param byteSize  Total byte size of the file as reported by the host
 *                  ({@code Content-Length}). May be {@code null} when the
 *                  host omitted the header (streaming response).
 */
public record FileResolution(String sha, byte[] content, String mimeType, Long byteSize) {
  public FileResolution {
    Objects.requireNonNull(sha, "sha");
    Objects.requireNonNull(mimeType, "mimeType");
    // content may be null (truncated). byteSize may be null (no Content-Length).
  }
}
