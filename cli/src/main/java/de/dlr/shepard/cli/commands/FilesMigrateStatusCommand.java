package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.FileMigrationState;
import picocli.CommandLine.Command;

/**
 * FS1e1 — {@code shepard-admin files migrate-status}.
 *
 * <p>Polls {@code GET /v2/admin/files/migrate/status} and prints
 * the current migration state. Useful in a watch loop:
 * {@code watch -n5 shepard-admin files migrate-status}.
 *
 * <p>Exit codes: 0 when status is IDLE or DONE; 1 when RUNNING
 * (so a CI-style {@code until; do sleep 5; done} loop can wait
 * on it); 2 when FAILED.
 */
@Command(
  name = "migrate-status",
  mixinStandardHelpOptions = true,
  description = "Show the current file-storage migration status."
)
public final class FilesMigrateStatusCommand extends AbstractCommand {

  static final String STATUS_PATH = "/v2/admin/files/migrate/status";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    FileMigrationState state;
    try {
      state = client.getJson(STATUS_PATH, new TypeReference<FileMigrationState>() {});
    } catch (Exception ex) {
      throw new AdminCliException("Could not read migration status: " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(state));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise migration state to JSON: " + e.getMessage(), e);
      }
    } else {
      FilesMigrateCommand.printState(state, out());
    }

    String status = state.getStatus();
    if ("FAILED".equalsIgnoreCase(status)) return 2;
    if ("RUNNING".equalsIgnoreCase(status)) return 1;
    return 0;
  }
}
