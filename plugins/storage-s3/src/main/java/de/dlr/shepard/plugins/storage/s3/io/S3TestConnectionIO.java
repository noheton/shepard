package de.dlr.shepard.plugins.storage.s3.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * FS1b — response body for
 * {@code POST /v2/admin/storage/s3/test-connection}.
 *
 * <p>Reports whether the configured S3 endpoint and bucket are
 * reachable via a {@code HeadBucketRequest} probe.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record S3TestConnectionIO(
  boolean reachable,
  int statusCode,
  long latencyMs,
  String endpoint,
  String bucket,
  String detail
) {}
