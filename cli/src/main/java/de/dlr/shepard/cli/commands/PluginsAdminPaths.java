package de.dlr.shepard.cli.commands;

/**
 * PM1b — central definition of the admin REST paths the
 * {@code plugins} subcommand group hits. Lifted into a shared class
 * so the list / enable / disable command classes don't repeat the
 * literal and a future endpoint move only touches one file. Same
 * pattern as {@link UnhideAdminPaths}.
 */
final class PluginsAdminPaths {

  /** {@code GET /v2/admin/plugins}. */
  static final String LIST = "/v2/admin/plugins";

  /** {@code PATCH /v2/admin/plugins/<id>} — {@code id} is appended. */
  static String forId(String id) {
    return LIST + "/" + id;
  }

  private PluginsAdminPaths() {}
}
