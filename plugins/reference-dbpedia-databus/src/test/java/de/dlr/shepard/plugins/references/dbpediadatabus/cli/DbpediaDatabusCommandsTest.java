package de.dlr.shepard.plugins.references.dbpediadatabus.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import de.dlr.shepard.cli.support.StubBackend.RecordedRequest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * REF1c — end-to-end CLI surface tests for
 * {@code references dbpedia-databus <verb>}.
 *
 * <p>Each test wires the Picocli command against an in-process
 * {@link StubBackend} so we exercise real HttpClient + Jackson +
 * Picocli wiring without booting Quarkus.
 */
class DbpediaDatabusCommandsTest {

  private static final String CONFIG_JSON =
    "{\"enabled\":true,\"defaultEndpoint\":\"https://databus.dbpedia.org\"," +
    "\"allowedHosts\":[\"databus.dbpedia.org\"],\"cacheTtlSeconds\":86400," +
    "\"authMode\":\"none\",\"oauthClientSecretSet\":false}";

  private StubBackend backend;

  @BeforeEach
  void setUp() throws IOException {
    backend = StubBackend.start();
  }

  @AfterEach
  void tearDown() {
    backend.close();
  }

  // ─── status ──────────────────────────────────────────────────────────────

  @Test
  void status_humanOutput_rendersTable() {
    backend.route(DbpediaDatabusAdminPaths.CONFIG, 200, rr -> CONFIG_JSON);

    Captured cap = CliRunner.run(new DbpediaDatabusStatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isZero();
    assertThat(cap.stdout()).contains("enabled");
    assertThat(cap.stdout()).contains("true");
    assertThat(cap.stdout()).contains("databus.dbpedia.org");
  }

  @Test
  void status_jsonOutput_emitsParseableJson() {
    backend.route(DbpediaDatabusAdminPaths.CONFIG, 200, rr -> CONFIG_JSON);

    Captured cap = CliRunner.run(new DbpediaDatabusStatusCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isZero();
    assertThat(cap.stdout()).contains("\"enabled\"");
    assertThat(cap.stdout()).contains("true");
  }

  // ─── enable / disable ────────────────────────────────────────────────────

  @Test
  void enable_sendsPatchWithEnabledTrue() {
    backend.route(DbpediaDatabusAdminPaths.CONFIG, 200, rr -> CONFIG_JSON);

    Captured cap = CliRunner.run(new DbpediaDatabusEnableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isZero();
    List<RecordedRequest> requests = backend.requests();
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).method()).isEqualTo("PATCH");
    assertThat(requests.get(0).body()).contains("\"enabled\":true");
    assertThat(cap.stdout()).contains("enabled");
  }

  @Test
  void disable_sendsPatchWithEnabledFalse() {
    String disabledJson = CONFIG_JSON.replace("\"enabled\":true", "\"enabled\":false");
    backend.route(DbpediaDatabusAdminPaths.CONFIG, 200, rr -> disabledJson);

    Captured cap = CliRunner.run(new DbpediaDatabusDisableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isZero();
    List<RecordedRequest> requests = backend.requests();
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).method()).isEqualTo("PATCH");
    assertThat(requests.get(0).body()).contains("\"enabled\":false");
    assertThat(cap.stdout()).contains("disabled");
  }

  // ─── set-base-url ────────────────────────────────────────────────────────

  @Test
  void setBaseUrl_sendsPatchWithDefaultEndpoint() {
    String updatedJson = CONFIG_JSON.replace("databus.dbpedia.org", "mybus.example.org");
    backend.route(DbpediaDatabusAdminPaths.CONFIG, 200, rr -> updatedJson);

    Captured cap = CliRunner.run(
      new DbpediaDatabusSetBaseUrlCommand(),
      backend.baseUrl(),
      "test-key",
      "https://mybus.example.org"
    );

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isZero();
    List<RecordedRequest> requests = backend.requests();
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).body()).contains("\"defaultEndpoint\"");
    assertThat(requests.get(0).body()).contains("mybus.example.org");
    assertThat(cap.stdout()).contains("mybus.example.org");
  }

  // ─── test-connection ─────────────────────────────────────────────────────

  @Test
  void testConnection_reachable_exitsZero() {
    backend.route(
      DbpediaDatabusAdminPaths.TEST_CONNECTION,
      200,
      rr -> "{\"reachable\":true,\"statusCode\":200,\"latencyMs\":42}"
    );

    Captured cap = CliRunner.run(new DbpediaDatabusTestConnectionCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isZero();
    assertThat(cap.stdout()).contains("true");
    assertThat(cap.stdout()).contains("42");
  }

  @Test
  void testConnection_notReachable_exitsNonZero() {
    backend.route(
      DbpediaDatabusAdminPaths.TEST_CONNECTION,
      200,
      rr -> "{\"reachable\":false,\"statusCode\":null,\"latencyMs\":1,\"reason\":\"connect-timeout\"}"
    );

    Captured cap = CliRunner.run(new DbpediaDatabusTestConnectionCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isGreaterThan(0);
    assertThat(cap.stdout()).contains("false");
  }

  @Test
  void testConnection_jsonOutput_emitsJson() {
    backend.route(
      DbpediaDatabusAdminPaths.TEST_CONNECTION,
      200,
      rr -> "{\"reachable\":true,\"statusCode\":200,\"latencyMs\":5}"
    );

    Captured cap = CliRunner.run(
      new DbpediaDatabusTestConnectionCommand(),
      backend.baseUrl(),
      "test-key",
      "--output=json"
    );

    assertThat(cap.exit()).isZero();
    assertThat(cap.stdout()).contains("\"reachable\"");
  }

  // ─── auth-header propagation ─────────────────────────────────────────────

  @Test
  void allCommands_sendXApiKeyHeader() {
    backend.route(DbpediaDatabusAdminPaths.CONFIG, 200, rr -> CONFIG_JSON);

    CliRunner.run(new DbpediaDatabusStatusCommand(), backend.baseUrl(), "my-operator-key");

    List<RecordedRequest> requests = backend.requests();
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).apiKeyHeader()).isEqualTo("my-operator-key");
  }

  // ─── ServiceLoader wiring ────────────────────────────────────────────────

  @Test
  void shepardAdminTopLevel_wiresDbpediaDatabusSubcommand_viaServiceLoader() {
    picocli.CommandLine cmd = de.dlr.shepard.cli.ShepardAdmin.commandLine();
    // The AdminCliCommandProvider contributes DbpediaDatabusCommand which is
    // registered under its @Command name "dbpedia-databus". The parent
    // command name depends on how the CLI bootstrap nests it.
    // At minimum, the command must be discoverable somewhere in the tree.
    boolean found = findSubcommand(cmd, "dbpedia-databus");
    assertThat(found)
      .as("shepard-admin must surface the dbpedia-databus subcommand group via ServiceLoader")
      .isTrue();
  }

  private static boolean findSubcommand(picocli.CommandLine cmd, String name) {
    if (cmd.getSubcommands().containsKey(name)) return true;
    for (picocli.CommandLine sub : cmd.getSubcommands().values()) {
      if (findSubcommand(sub, name)) return true;
    }
    return false;
  }
}
