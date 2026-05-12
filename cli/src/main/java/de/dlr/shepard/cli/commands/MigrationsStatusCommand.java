package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.MigrationProgress;
import de.dlr.shepard.cli.output.TableFormatter;
import java.time.Instant;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin migrations status [containerId]} — calls
 * {@code GET /shepard/api/temp/migrations/state} (or
 * {@code /shepard/api/temp/migrations/{containerId}} when an id is
 * given) and prints the progress catalogue.
 *
 * <p>Wire shape mirrors the backend's {@code MigrationProgressIO}
 * — see {@code aidocs/22-admin-cli-draft.md §4.4} and
 * {@code de.dlr.shepard.data.timeseries.migration.io.MigrationProgressIO}.
 * P3c locks this endpoint to the {@code instance-admin} role, so
 * the CLI must present an admin-role API key.
 *
 * <p>Exit codes: 0 on success (including the "no migrations" case);
 * 1 on any operator-readable error (connect failure, 401/403, 404
 * for the per-container variant, malformed body); 2 on unexpected
 * runtime exception.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Show migration progress for all containers, or for one when an id is given."
)
public final class MigrationsStatusCommand extends AbstractCommand {

  static final String PATH_ALL = "/shepard/api/temp/migrations/state";
  static final String PATH_BY_ID_PREFIX = "/shepard/api/temp/migrations/";

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "containerId",
    description = "Optional container id — when set, show only that container's progress."
  )
  Long containerId;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    List<MigrationProgress> progresses;
    if (containerId == null) {
      progresses = client.getJson(PATH_ALL, new TypeReference<List<MigrationProgress>>() {});
    } else {
      MigrationProgress single = client.getJson(
        PATH_BY_ID_PREFIX + containerId,
        new TypeReference<MigrationProgress>() {}
      );
      progresses = List.of(single);
    }

    if (wantsJson()) {
      try {
        // When the user asked for a single container, render the
        // singleton — operators piping into jq don't want to unwrap
        // an unexpected list.
        Object payload = (containerId == null) ? progresses : progresses.get(0);
        out().println(jsonMapper().writeValueAsString(payload));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise migrations to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    if (progresses.isEmpty()) {
      out().println("No migrations recorded.");
      return 0;
    }

    TableFormatter table = new TableFormatter(
      "CONTAINER",
      "STATUS",
      "MIGRATED",
      "TOTAL",
      "FAILED",
      "BATCH",
      "STARTED",
      "UPDATED",
      "ETA(s)"
    );
    for (MigrationProgress p : progresses) {
      table.addRow(
        Long.toString(p.getContainerId()),
        nullToDash(p.getStatus()),
        Long.toString(p.getRowsMigrated()),
        Long.toString(p.getRowsTotal()),
        Long.toString(p.getRowsFailed()),
        Integer.toString(p.getLastBatchIndex()),
        formatInstant(p.getStartedAt()),
        formatInstant(p.getLastUpdateAt()),
        p.getEstimatedRemainingSeconds() == null ? "-" : Long.toString(p.getEstimatedRemainingSeconds())
      );
    }
    out().print(table.render());

    // Surface the most recent error string under the table when any
    // container is in FAILED state. Keeps the table itself narrow
    // while still pointing the operator at the root cause.
    for (MigrationProgress p : progresses) {
      if (p.getErrors() != null && !p.getErrors().isBlank()) {
        out().println();
        out().println("container " + p.getContainerId() + " errors: " + p.getErrors());
      }
    }
    return 0;
  }

  private static String nullToDash(String s) {
    return (s == null || s.isBlank()) ? "-" : s;
  }

  private static String formatInstant(Instant instant) {
    return instant == null ? "-" : instant.toString();
  }
}
