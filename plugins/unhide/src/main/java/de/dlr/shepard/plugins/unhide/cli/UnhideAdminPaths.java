package de.dlr.shepard.plugins.unhide.cli;

/**
 * UH1a — central definition of the admin REST paths the
 * {@code unhide} subcommand group hits. Lifted into a shared class
 * so the seven command classes don't repeat the literal and a
 * future endpoint move only touches one file.
 *
 * <p>PM1d — relocated to {@code plugins/unhide/} alongside the rest
 * of the {@code unhide} CLI subcommand group, contributed to
 * {@code shepard-admin} via the {@code AdminCliCommandProvider} SPI.
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
