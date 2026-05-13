package de.dlr.shepard.plugins.minter.datacite.cli;

/**
 * KIP1d — central definition of the admin REST paths the
 * {@code minters datacite} subcommand group hits.
 */
final class DataciteAdminPaths {

  /** {@code GET / PATCH /v2/admin/minters/datacite/config}. */
  static final String CONFIG = "/v2/admin/minters/datacite/config";

  /** {@code POST / DELETE /v2/admin/minters/datacite/credential}. */
  static final String CREDENTIAL = "/v2/admin/minters/datacite/credential";

  /** {@code POST /v2/admin/minters/datacite/test-connection}. */
  static final String TEST_CONNECTION = "/v2/admin/minters/datacite/test-connection";

  private DataciteAdminPaths() {}
}
