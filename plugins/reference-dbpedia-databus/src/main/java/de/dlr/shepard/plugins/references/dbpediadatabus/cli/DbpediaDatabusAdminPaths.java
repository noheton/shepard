package de.dlr.shepard.plugins.references.dbpediadatabus.cli;

/**
 * REF1c — central definition of the admin REST paths the
 * {@code references dbpedia-databus} subcommand group hits.
 */
final class DbpediaDatabusAdminPaths {

  /** {@code GET / PATCH /v2/admin/references/dbpedia-databus/config}. */
  static final String CONFIG = "/v2/admin/references/dbpedia-databus/config";

  /** {@code POST /v2/admin/references/dbpedia-databus/test-connection}. */
  static final String TEST_CONNECTION = "/v2/admin/references/dbpedia-databus/test-connection";

  private DbpediaDatabusAdminPaths() {}
}
