package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import org.junit.jupiter.api.Test;

/**
 * Tests {@code shepard-admin migrations status} — both the list
 * variant (no positional arg) and the by-container variant. Wire
 * shape must match {@code MigrationProgressIO}; if the backend
 * adds a field, these tests still pass thanks to
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} on the
 * CLI's {@code MigrationProgress} mirror.
 */
final class MigrationsStatusCommandTest {

  private static final String PROGRESS_LIST_JSON =
    """
    [
      {
        "containerId": 42,
        "rowsTotal": 1000,
        "rowsMigrated": 750,
        "rowsFailed": 0,
        "lastBatchIndex": 7,
        "status": "RUNNING",
        "startedAt": "2026-05-12T10:00:00Z",
        "lastUpdateAt": "2026-05-12T10:05:30Z",
        "errors": null,
        "estimatedRemainingSeconds": 90
      },
      {
        "containerId": 99,
        "rowsTotal": 50,
        "rowsMigrated": 50,
        "rowsFailed": 0,
        "lastBatchIndex": 1,
        "status": "COMPLETED",
        "startedAt": "2026-05-11T09:00:00Z",
        "lastUpdateAt": "2026-05-11T09:00:10Z",
        "errors": null,
        "estimatedRemainingSeconds": null
      }
    ]
    """;

  private static final String SINGLE_PROGRESS_JSON =
    """
    {
      "containerId": 13,
      "rowsTotal": 500,
      "rowsMigrated": 100,
      "rowsFailed": 7,
      "lastBatchIndex": 2,
      "status": "FAILED",
      "startedAt": "2026-05-12T08:00:00Z",
      "lastUpdateAt": "2026-05-12T08:01:00Z",
      "errors": "schema column missing on batch 3",
      "estimatedRemainingSeconds": null
    }
    """;

  @Test
  void listsAllMigrationsAsTable() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(MigrationsStatusCommand.PATH_ALL, 200, PROGRESS_LIST_JSON);

      Captured result = CliRunner.run(new MigrationsStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("CONTAINER")
        .contains("STATUS")
        .contains("MIGRATED")
        .contains("42")
        .contains("RUNNING")
        .contains("750")
        .contains("99")
        .contains("COMPLETED");
    }
  }

  @Test
  void emptyListPrintsFriendlyMessage() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(MigrationsStatusCommand.PATH_ALL, 200, "[]");

      Captured result = CliRunner.run(new MigrationsStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout()).contains("No migrations recorded.");
    }
  }

  @Test
  void byIdHitsTheSingleContainerEndpoint() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(MigrationsStatusCommand.PATH_BY_ID_PREFIX + "13", 200, SINGLE_PROGRESS_JSON);

      Captured result = CliRunner.run(
        new MigrationsStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "13"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("13")
        .contains("FAILED")
        .contains("schema column missing on batch 3");
      assertThat(backend.requests())
        .anyMatch(r -> r.path().endsWith("/13"));
    }
  }

  @Test
  void byIdNotFoundReturnsExitOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(
        MigrationsStatusCommand.PATH_BY_ID_PREFIX + "404",
        404,
        "{\"error\":\"no migration progress\"}"
      );

      Captured result = CliRunner.run(
        new MigrationsStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "404"
      );

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("404");
    }
  }

  @Test
  void jsonOutputForListIsAnArray() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(MigrationsStatusCommand.PATH_ALL, 200, PROGRESS_LIST_JSON);

      Captured result = CliRunner.run(
        new MigrationsStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "--output",
        "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout().trim()).startsWith("[");
      assertThat(result.stdout()).contains("\"containerId\"").contains("42");
    }
  }

  @Test
  void jsonOutputForByIdIsAnObject() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(MigrationsStatusCommand.PATH_BY_ID_PREFIX + "13", 200, SINGLE_PROGRESS_JSON);

      Captured result = CliRunner.run(
        new MigrationsStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "--output",
        "json",
        "13"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout().trim()).startsWith("{");
      assertThat(result.stdout()).contains("\"containerId\"").contains("13");
    }
  }
}
