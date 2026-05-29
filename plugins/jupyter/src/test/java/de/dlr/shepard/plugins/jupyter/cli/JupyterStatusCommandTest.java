package de.dlr.shepard.plugins.jupyter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import org.junit.jupiter.api.Test;

/**
 * J1e — exercises {@code shepard-admin jupyter status}. Reads
 * {@code GET /v2/admin/jupyter/config} and surfaces the
 * {@code enabled} / {@code hubUrl} pair, plus a derived
 * "affordanceVisible" boolean that decides the exit code (0 when
 * visible, 1 when suppressed).
 */
final class JupyterStatusCommandTest {

  private static final String AFFORDANCE_VISIBLE =
    """
    {
      "enabled": true,
      "hubUrl": "https://hub.example.org"
    }
    """;

  private static final String AFFORDANCE_HIDDEN_DISABLED =
    """
    {
      "enabled": false,
      "hubUrl": "https://hub.example.org"
    }
    """;

  private static final String AFFORDANCE_HIDDEN_NO_URL =
    """
    {
      "enabled": true,
      "hubUrl": null
    }
    """;

  @Test
  void enabledAndUrlSet_returnsExitZero_andTableShowsVisible() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(JupyterStatusCommand.CONFIG_PATH, 200, AFFORDANCE_VISIBLE);

      Captured result = CliRunner.run(new JupyterStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("hub.example.org")
        .contains("affordanceVisible")
        .contains("true");
      assertThat(result.stderr()).isEmpty();
    }
  }

  @Test
  void disabled_returnsExitOne_andEmitsEnableHint() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(JupyterStatusCommand.CONFIG_PATH, 200, AFFORDANCE_HIDDEN_DISABLED);

      Captured result = CliRunner.run(new JupyterStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout()).contains("jupyter enable");
    }
  }

  @Test
  void noHubUrl_returnsExitOne_andEmitsSetHubUrlHint() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(JupyterStatusCommand.CONFIG_PATH, 200, AFFORDANCE_HIDDEN_NO_URL);

      Captured result = CliRunner.run(new JupyterStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout())
        .contains("(not set)")
        .contains("set-hub-url");
    }
  }

  @Test
  void jsonOutput_emitsRawBody() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(JupyterStatusCommand.CONFIG_PATH, 200, AFFORDANCE_VISIBLE);

      Captured result = CliRunner.run(
        new JupyterStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "--output", "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("enabled")
        .contains("hub.example.org");
    }
  }
}
