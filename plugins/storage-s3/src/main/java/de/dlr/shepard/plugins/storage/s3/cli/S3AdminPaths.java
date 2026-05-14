package de.dlr.shepard.plugins.storage.s3.cli;

/**
 * FS1b — central definition of the admin REST paths the
 * {@code storage s3} subcommand group hits.
 */
final class S3AdminPaths {

  /** {@code GET / PATCH /v2/admin/storage/s3/config}. */
  static final String CONFIG = "/v2/admin/storage/s3/config";

  /** {@code POST / DELETE /v2/admin/storage/s3/credential}. */
  static final String CREDENTIAL = "/v2/admin/storage/s3/credential";

  /** {@code POST /v2/admin/storage/s3/test-connection}. */
  static final String TEST_CONNECTION = "/v2/admin/storage/s3/test-connection";

  private S3AdminPaths() {}
}
