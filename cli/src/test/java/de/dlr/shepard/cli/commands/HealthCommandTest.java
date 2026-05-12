package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import org.junit.jupiter.api.Test;

/**
 * Tests {@code shepard-admin health} — both readiness and liveness
 * are probed; the exit code reflects the aggregate UP/DOWN state.
 */
final class HealthCommandTest {

  private static final String READY_UP =
    """
    {
      "status":"UP",
      "checks":[
        {"name":"neo4j","status":"UP","data":{"latency-ms":4}},
        {"name":"mongo","status":"UP"}
      ]
    }
    """;

  private static final String LIVE_UP =
    """
    {"status":"UP","checks":[{"name":"alive","status":"UP"}]}
    """;

  private static final String READY_DOWN =
    """
    {
      "status":"DOWN",
      "checks":[
        {"name":"neo4j","status":"DOWN","data":{"error":"connection refused"}},
        {"name":"mongo","status":"UP"}
      ]
    }
    """;

  @Test
  void allUpReturnsExitZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(HealthCommand.READINESS_PATH, 200, READY_UP);
      backend.route(HealthCommand.LIVENESS_PATH, 200, LIVE_UP);

      Captured result = CliRunner.run(new HealthCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("READINESS")
        .contains("LIVENESS")
        .contains("neo4j")
        .contains("mongo")
        .contains("readiness=UP")
        .contains("liveness=UP");
    }
  }

  @Test
  void readinessDownReturnsExitOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      // Quarkus returns 503 when readiness is DOWN — the CLI's
      // fallback parses the body anyway.
      backend.route(HealthCommand.READINESS_PATH, 503, READY_DOWN);
      backend.route(HealthCommand.LIVENESS_PATH, 200, LIVE_UP);

      Captured result = CliRunner.run(new HealthCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout())
        .contains("DOWN")
        .contains("readiness=DOWN")
        .contains("connection refused");
    }
  }

  @Test
  void jsonOutputContainsBothEnvelopes() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(HealthCommand.READINESS_PATH, 200, READY_UP);
      backend.route(HealthCommand.LIVENESS_PATH, 200, LIVE_UP);

      Captured result = CliRunner.run(
        new HealthCommand(),
        backend.baseUrl(),
        "test-key",
        "--output",
        "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("\"readiness\"")
        .contains("\"liveness\"")
        .contains("\"neo4j\"");
    }
  }
}
