package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import org.junit.jupiter.api.Test;

/**
 * ROR1 — exercises {@code shepard-admin instance ror status}.
 * Reads {@code GET /v2/admin/instance/ror} and surfaces the ROR
 * identity fields with human-readable fallbacks when not set.
 */
final class InstanceRorStatusCommandTest {

  private static final String ROR_CONFIGURED =
    """
    {
      "rorId": "04cvxnb49",
      "organizationName": "DLR e.V.",
      "rorUrl": "https://ror.org/04cvxnb49"
    }
    """;

  private static final String ROR_NOT_SET =
    """
    {}
    """;

  @Test
  void configured_rorId_returnsExitZero_andHumanTableContainsId() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorStatusCommand.ROR_PATH, 200, ROR_CONFIGURED);

      Captured result = CliRunner.run(new InstanceRorStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("04cvxnb49")
        .contains("DLR e.V.")
        .contains("https://ror.org/04cvxnb49");
      assertThat(result.stderr()).isEmpty();
    }
  }

  @Test
  void rorId_null_returnsExitOne_andHumanTableContainsNotSet() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorStatusCommand.ROR_PATH, 200, ROR_NOT_SET);

      Captured result = CliRunner.run(new InstanceRorStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout()).contains("(not set)");
    }
  }

  @Test
  void jsonOutput_emitsRawBody_exitZeroWhenConfigured() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorStatusCommand.ROR_PATH, 200, ROR_CONFIGURED);

      Captured result = CliRunner.run(
        new InstanceRorStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "--output", "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("04cvxnb49")
        .contains("organizationName");
    }
  }

  @Test
  void jsonOutput_exitOneWhenRorIdNotSet() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorStatusCommand.ROR_PATH, 200, ROR_NOT_SET);

      Captured result = CliRunner.run(
        new InstanceRorStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "--output", "json"
      );

      assertThat(result.exit()).isEqualTo(1);
    }
  }

  @Test
  void unauthorized_returnsExitOne_andStderrContains401() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorStatusCommand.ROR_PATH, 401, "{\"error\":\"missing X-API-KEY\"}");

      Captured result = CliRunner.run(new InstanceRorStatusCommand(), backend.baseUrl(), null);

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("401");
    }
  }
}
