package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import org.junit.jupiter.api.Test;

/**
 * FS1a — exercises {@code shepard-admin storage status}. Reads the
 * existing readiness probe and surfaces the MongoDB-side health
 * signal as the proxy for "is the default GridFS storage adapter
 * up?" Full provider-listing detail is FS1d territory.
 */
final class StorageStatusCommandTest {

  private static final String READY_MONGO_UP =
    """
    {
      "status":"UP",
      "checks":[
        {"name":"neo4j","status":"UP"},
        {"name":"MongoDB Health Check","status":"UP","data":{"latency-ms":4}}
      ]
    }
    """;

  private static final String READY_MONGO_DOWN =
    """
    {
      "status":"DOWN",
      "checks":[
        {"name":"neo4j","status":"UP"},
        {"name":"MongoDB Health Check","status":"DOWN","data":{"error":"connection refused"}}
      ]
    }
    """;

  private static final String READY_NO_MONGO =
    """
    {
      "status":"UP",
      "checks":[
        {"name":"neo4j","status":"UP"}
      ]
    }
    """;

  @Test
  void mongoUpReturnsExitZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(StorageStatusCommand.READINESS_PATH, 200, READY_MONGO_UP);

      Captured result = CliRunner.run(new StorageStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("STORAGE")
        .contains("active provider")
        .contains("gridfs connection")
        .contains("UP")
        .contains("FS1d")
        .contains("FS1b");
    }
  }

  @Test
  void mongoDownReturnsExitOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      // Quarkus returns 503 when readiness is DOWN — the CLI's
      // fallback parses the body anyway.
      backend.route(StorageStatusCommand.READINESS_PATH, 503, READY_MONGO_DOWN);

      Captured result = CliRunner.run(new StorageStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout()).contains("DOWN");
    }
  }

  @Test
  void missingMongoCheckReportsUnknown() throws Exception {
    // Defensive: if the readiness probe doesn't include a mongo
    // entry (no GridFS-using deployment, future split, …), the
    // command reports UNKNOWN rather than crash.
    try (StubBackend backend = StubBackend.start()) {
      backend.route(StorageStatusCommand.READINESS_PATH, 200, READY_NO_MONGO);

      Captured result = CliRunner.run(new StorageStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout()).contains("UNKNOWN");
    }
  }

  @Test
  void jsonOutputSurfacesGridfsConnection() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(StorageStatusCommand.READINESS_PATH, 200, READY_MONGO_UP);

      Captured result = CliRunner.run(
        new StorageStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "--output",
        "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("\"gridfsConnection\"")
        .contains("\"UP\"")
        .contains("\"gridfsConnectionUp\"")
        .contains("activeProviderHint");
    }
  }
}
