package de.dlr.shepard.plugins.minter.epic.cli;

/**
 * KIP1c — central definition of the admin REST paths the
 * {@code minters epic} subcommand group hits.
 */
final class EpicAdminPaths {

  /** {@code GET / PATCH /v2/admin/minters/epic/config}. */
  static final String CONFIG = "/v2/admin/minters/epic/config";

  /** {@code POST / DELETE /v2/admin/minters/epic/credential}. */
  static final String CREDENTIAL = "/v2/admin/minters/epic/credential";

  /** {@code POST /v2/admin/minters/epic/test-connection}. */
  static final String TEST_CONNECTION = "/v2/admin/minters/epic/test-connection";

  private EpicAdminPaths() {}
}
