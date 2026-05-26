package de.dlr.shepard.v2.importer.io;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * IMP2 — request body for {@code POST /v2/import/jobs}.
 *
 * <p>The {@code commitId} is the plan seal returned by
 * {@code POST /v2/import/validate}.  It is a deterministic
 * {@code "sha256:<hex>"} string bound to the collection state at
 * validation time.
 *
 * @param commitId the plan seal to execute
 */
public record ImportJobRequestIO(
  @NotBlank
  @Schema(
    description = "Plan seal (commitId) returned by POST /v2/import/validate.",
    example = "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
  )
  String commitId
) {}
