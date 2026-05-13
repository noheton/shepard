package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import de.dlr.shepard.cli.support.StubBackend.RecordedRequest;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for {@code shepard-admin semantic refresh-ontologies}.
 * Uses a loopback {@link StubBackend} for the real {@code HttpClient}
 * + Jackson decoding path.
 */
final class SemanticRefreshOntologiesCommandTest {

  private static final String HAPPY_JSON =
    """
    {
      "requested": 9,
      "refreshed": 7,
      "alreadyCurrent": 2,
      "errors": []
    }
    """;

  private static final String MIXED_JSON =
    """
    {
      "requested": 3,
      "refreshed": 1,
      "alreadyCurrent": 1,
      "errors": [
        {"bundle": "qudt", "reason": "Could not fetch http://qudt.org/2.1/vocab/unit.ttl: connection reset"}
      ]
    }
    """;

  private static final String FILTERED_QUDT_JSON =
    """
    {
      "requested": 1,
      "refreshed": 1,
      "alreadyCurrent": 0,
      "errors": []
    }
    """;

  @Test
  void happyPathRendersHumanSummary_andExitsZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 200, HAPPY_JSON);

      Captured result = CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        "test-key"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("Refresh complete")
        .contains("requested=9")
        .contains("refreshed=7")
        .contains("alreadyCurrent=2")
        .contains("errors=0");
      assertThat(result.stderr()).isEmpty();
    }
  }

  @Test
  void postsJsonBody_withEmptyBundlesAndForceFalse_byDefault() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 200, HAPPY_JSON);

      CliRunner.run(new SemanticRefreshOntologiesCommand(), backend.baseUrl(), "test-key");

      assertThat(backend.requests()).hasSize(1);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.method()).isEqualTo("POST");
      assertThat(req.apiKeyHeader()).isEqualTo("test-key");
      assertThat(req.body()).contains("\"bundles\"").contains("[]");
      assertThat(req.body()).contains("\"force\"").contains("false");
    }
  }

  @Test
  void bundlesFlagSerialisesAsList() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 200, FILTERED_QUDT_JSON);

      Captured result = CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        "test-key",
        "--bundles=qudt"
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.body()).contains("\"qudt\"");
    }
  }

  @Test
  void multipleBundlesCsvSplitsCorrectly() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 200, HAPPY_JSON);

      CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        "test-key",
        "--bundles=prov-o,qudt,obo-relations"
      );

      RecordedRequest req = backend.requests().get(0);
      assertThat(req.body()).contains("\"prov-o\"").contains("\"qudt\"").contains("\"obo-relations\"");
    }
  }

  @Test
  void forceFlagSerialisesAsBooleanTrue() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 200, HAPPY_JSON);

      CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        "test-key",
        "--force"
      );

      RecordedRequest req = backend.requests().get(0);
      assertThat(req.body()).contains("\"force\"").contains("true");
    }
  }

  @Test
  void jsonOutput_emitsParseableResultEnvelope() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 200, HAPPY_JSON);

      Captured result = CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        "test-key",
        "--output",
        "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("\"requested\"")
        .contains("\"refreshed\"")
        .contains("\"alreadyCurrent\"")
        .contains("\"errors\"");
    }
  }

  @Test
  void partialFailureExitsOne_andSurfacesReasonInTable() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 200, MIXED_JSON);

      Captured result = CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        "test-key"
      );

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout())
        .contains("errors=1")
        .contains("BUNDLE")
        .contains("REASON")
        .contains("qudt")
        .contains("connection reset");
    }
  }

  @Test
  void partialFailureWithJsonOutput_alsoExitsOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 200, MIXED_JSON);

      Captured result = CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        "test-key",
        "--output",
        "json"
      );

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout()).contains("\"bundle\"").contains("\"qudt\"");
    }
  }

  @Test
  void unauthorizedReturnsExitOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 401, "{\"error\":\"missing X-API-KEY\"}");

      Captured result = CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        null
      );

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("401").contains("Unauthorized");
    }
  }

  @Test
  void forbiddenReturnsExitOne_withInstanceAdminHint() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticRefreshOntologiesCommand.PATH, 403, "{\"error\":\"role missing\"}");

      Captured result = CliRunner.run(
        new SemanticRefreshOntologiesCommand(),
        backend.baseUrl(),
        "non-admin-key"
      );

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("403").contains("instance-admin");
    }
  }

  @Test
  void connectFailureReportsHumanReadableMessage() {
    Captured result = CliRunner.run(
      new SemanticRefreshOntologiesCommand(),
      "http://127.0.0.1:1",
      "test-key"
    );

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.stderr().toLowerCase())
      .containsAnyOf("cannot connect", "network error", "refused");
  }
}
