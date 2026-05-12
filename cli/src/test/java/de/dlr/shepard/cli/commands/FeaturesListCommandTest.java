package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for {@code shepard-admin features list}. Uses a
 * loopback {@link StubBackend} to stand in for the shepard REST
 * surface so the real {@link java.net.http.HttpClient} +
 * Jackson decoding path is exercised end-to-end.
 */
final class FeaturesListCommandTest {

  private static final String FEATURES_JSON =
    """
    [
      {"name":"spatial","enabled":true,"description":"Enable spatial payloads."},
      {"name":"versioning","enabled":false,"description":"Enable versioned writes."}
    ]
    """;

  @Test
  void rendersHumanTable() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(FeaturesListCommand.PATH, 200, FEATURES_JSON);

      Captured result = CliRunner.run(new FeaturesListCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("NAME")
        .contains("ENABLED")
        .contains("DESCRIPTION")
        .contains("spatial")
        .contains("true")
        .contains("versioning")
        .contains("false")
        .contains("Enable spatial payloads.");
      assertThat(result.stderr()).isEmpty();
    }
  }

  @Test
  void rendersJsonWhenOutputFlagIsJson() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(FeaturesListCommand.PATH, 200, FEATURES_JSON);

      Captured result = CliRunner.run(
        new FeaturesListCommand(),
        backend.baseUrl(),
        "test-key",
        "--output",
        "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      // Pretty-printed JSON — assert on the structural keys, not on
      // exact whitespace.
      assertThat(result.stdout())
        .contains("\"name\"")
        .contains("\"spatial\"")
        .contains("\"enabled\"")
        .contains("true");
    }
  }

  @Test
  void sendsApiKeyHeader() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(FeaturesListCommand.PATH, 200, "[]");

      CliRunner.run(new FeaturesListCommand(), backend.baseUrl(), "ci-bot-key");

      assertThat(backend.requests()).isNotEmpty();
      assertThat(backend.requests().get(0).apiKeyHeader()).isEqualTo("ci-bot-key");
    }
  }

  @Test
  void surfacesUnauthorizedAsExitOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(FeaturesListCommand.PATH, 401, "{\"error\":\"missing X-API-KEY\"}");

      Captured result = CliRunner.run(new FeaturesListCommand(), backend.baseUrl(), null);

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("401").contains("Unauthorized");
    }
  }

  @Test
  void connectFailureReportsHumanReadableMessage() {
    // Use a port that's almost certainly not listening — avoids the
    // 1ms race of starting/stopping a stub server.
    Captured result = CliRunner.run(
      new FeaturesListCommand(),
      "http://127.0.0.1:1",
      "test-key"
    );

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.stderr().toLowerCase())
      .containsAnyOf("cannot connect", "network error", "refused");
  }

  @Test
  void emptyListPrintsTableHeader() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(FeaturesListCommand.PATH, 200, "[]");

      Captured result = CliRunner.run(new FeaturesListCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout()).contains("NAME");
    }
  }
}
