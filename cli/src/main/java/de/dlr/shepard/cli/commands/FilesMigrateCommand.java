package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.FileMigrationState;
import de.dlr.shepard.cli.output.TableFormatter;
import java.io.PrintWriter;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * FS1e1 — {@code shepard-admin files migrate <source> <target>}.
 *
 * <p>Triggers a big-bang migration via
 * {@code POST /v2/admin/files/migrate}. Returns immediately with the
 * initial migration state; poll with
 * {@code shepard-admin files migrate-status} to track progress.
 *
 * <p>Exit codes: 0 when migration was accepted (RUNNING); 1 on
 * validation error or transport failure.
 */
@Command(
  name = "migrate",
  mixinStandardHelpOptions = true,
  description = "Trigger a big-bang file-storage migration between two adapters."
)
public final class FilesMigrateCommand extends AbstractCommand {

  static final String MIGRATE_PATH = "/v2/admin/files/migrate";

  @Parameters(
    index = "0",
    paramLabel = "SOURCE",
    description = "Id of the storage adapter to migrate files away from (e.g. 'gridfs')."
  )
  String sourceProviderId;

  @Parameters(
    index = "1",
    paramLabel = "TARGET",
    description = "Id of the storage adapter to migrate files to (e.g. 's3')."
  )
  String targetProviderId;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    FileMigrationState state;
    try {
      state = client.postJson(
        MIGRATE_PATH,
        Map.of("sourceProviderId", sourceProviderId, "targetProviderId", targetProviderId),
        new TypeReference<FileMigrationState>() {}
      );
    } catch (Exception ex) {
      throw new AdminCliException("Could not trigger migration: " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(state));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise migration state to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("Migration triggered.");
    out().println();
    printState(state, out());
    out().println();
    out().println("Poll progress: shepard-admin files migrate-status");
    return 0;
  }

  static void printState(FileMigrationState state, PrintWriter out) {
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("status", nullToDash(state.getStatus()));
    table.addRow("source", nullToDash(state.getSourceProviderId()));
    table.addRow("target", nullToDash(state.getTargetProviderId()));
    table.addRow("files total", Long.toString(state.getFilesTotal()));
    table.addRow("files migrated", Long.toString(state.getFilesMigrated()));
    table.addRow("files failed", Long.toString(state.getFilesFailed()));
    if (state.getStartedAt() != null) {
      table.addRow("started at", state.getStartedAt().toString());
    }
    if (state.getUpdatedAt() != null) {
      table.addRow("updated at", state.getUpdatedAt().toString());
    }
    if (state.getErrorMessage() != null) {
      table.addRow("error", state.getErrorMessage());
    }
    out.print(table.render());
  }

  static String nullToDash(String s) {
    return (s == null || s.isBlank()) ? "-" : s;
  }
}
