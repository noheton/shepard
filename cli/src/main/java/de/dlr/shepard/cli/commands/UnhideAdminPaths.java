package de.dlr.shepard.cli.commands;

/**
 * UH1a — central definition of the admin REST paths the
 * {@code unhide} subcommand group hits. Lifted into a shared class
 * so the seven command classes don't repeat the literal and a
 * future endpoint move only touches one file.
 */
final class UnhideAdminPaths {

  /** {@code GET / PATCH /v2/admin/unhide/config}. */
  static final String CONFIG = "/v2/admin/unhide/config";

  /** {@code POST /v2/admin/unhide/harvest-key/rotate}. */
  static final String ROTATE_HARVEST_KEY = "/v2/admin/unhide/harvest-key/rotate";

  /** {@code POST /v2/admin/unhide/harvest-key/revoke}. */
  static final String REVOKE_HARVEST_KEY = "/v2/admin/unhide/harvest-key/revoke";

  private UnhideAdminPaths() {}
}
