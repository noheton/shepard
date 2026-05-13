package de.dlr.shepard.plugins.minter.datacite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import de.dlr.shepard.cli.support.StubBackend.RecordedRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KIP1d — end-to-end CLI surface tests for
 * {@code shepard-admin minters datacite <verb>}. Each test wires the
 * Picocli command against an in-process {@link StubBackend}.
 */
class DataciteCommandsTest {

  private static final String STATUS_BODY = """
      {
        "enabled":true,
        "apiBaseUrl":"https://api.test.datacite.org",
        "handlePrefix":"10.5072",
        "repositoryId":"DLR.SHEPARD",
        "passwordSet":true,
        "passwordFingerprint":"deadbeef",
        "publisher":"DLR e.V.",
        "landingPageBase":"https://shepard.example.org/v2",
        "defaultState":"draft",
        "updatedAt":"2026-05-13T10:00:00.000+00:00",
        "updatedBy":"alice"
      }
      """;

  private StubBackend backend;

  @BeforeEach
  void setUp() throws IOException {
    backend = StubBackend.start();
  }

  @AfterEach
  void tearDown() {
    backend.close();
  }

  // ─── status ────────────────────────────────────────────────────────────

  @Test
  void status_humanOutput_rendersTable() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteStatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
    assertThat(cap.stdout()).contains("enabled");
    assertThat(cap.stdout()).contains("true");
    assertThat(cap.stdout()).contains("10.5072");
    assertThat(cap.stdout()).contains("deadbeef");
    assertThat(cap.stdout()).contains("DLR e.V.");
  }

  @Test
  void status_jsonOutput_emitsParseableJson() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteStatusCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"enabled\"");
    assertThat(cap.stdout()).contains("\"apiBaseUrl\"");
  }

  @Test
  void status_humanOutput_showsUnsetForBlankCredential() {
    String body = STATUS_BODY.replace("\"passwordSet\":true", "\"passwordSet\":false")
      .replace("\"passwordFingerprint\":\"deadbeef\"", "\"passwordFingerprint\":null");
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> body);

    Captured cap = CliRunner.run(new DataciteStatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("(no credential)");
  }

  // ─── enable / disable ──────────────────────────────────────────────────

  @Test
  void enable_sendsPatchWithEnabledTrue() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteEnableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    List<RecordedRequest> rqs = backend.requests();
    assertThat(rqs).hasSize(1);
    assertThat(rqs.get(0).method()).isEqualTo("PATCH");
    assertThat(rqs.get(0).body()).contains("\"enabled\":true");
    assertThat(cap.stdout()).contains("DataCite minter enabled");
  }

  @Test
  void enable_warnsWhenPasswordMissing() {
    String body = STATUS_BODY.replace("\"passwordSet\":true", "\"passwordSet\":false");
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> body);

    Captured cap = CliRunner.run(new DataciteEnableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stderr()).contains("passwordSet=false");
  }

  @Test
  void disable_sendsPatchWithEnabledFalse() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteDisableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"enabled\":false");
    assertThat(cap.stdout()).contains("disabled");
  }

  // ─── set-* commands ────────────────────────────────────────────────────

  @Test
  void setApiUrl_sendsPatch() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(
      new DataciteSetApiUrlCommand(),
      backend.baseUrl(),
      "test-key",
      "https://api.datacite.org"
    );

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"apiBaseUrl\":\"https://api.datacite.org\"");
    assertThat(cap.stdout()).contains("apiBaseUrl =");
  }

  @Test
  void setApiUrl_blankArgClearsField() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteSetApiUrlCommand(), backend.baseUrl(), "test-key", "");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"apiBaseUrl\":null");
  }

  @Test
  void setPrefix_sendsPatch() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteSetPrefixCommand(), backend.baseUrl(), "test-key", "10.1234");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"handlePrefix\":\"10.1234\"");
  }

  @Test
  void setRepositoryId_sendsPatch() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteSetRepositoryIdCommand(), backend.baseUrl(), "test-key", "DLR.X");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"repositoryId\":\"DLR.X\"");
  }

  @Test
  void setPublisher_sendsPatch() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteSetPublisherCommand(), backend.baseUrl(), "test-key", "Some Institute");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"publisher\":\"Some Institute\"");
  }

  @Test
  void setLandingPageBase_sendsPatch() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(
      new DataciteSetLandingPageBaseCommand(),
      backend.baseUrl(),
      "test-key",
      "https://example.org/v2"
    );

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"landingPageBase\":\"https://example.org/v2\"");
  }

  @Test
  void setState_sendsPatchWithValue() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new DataciteSetStateCommand(), backend.baseUrl(), "test-key", "registered");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"defaultState\":\"registered\"");
  }

  @Test
  void setState_serverRejectionFlowsThroughAsError() {
    // Server returns 400 problem+json — CLI exits non-zero.
    backend.route(
      "/v2/admin/minters/datacite/config",
      400,
      rr -> "{\"type\":\"/problems/minters.datacite.config.bad-state\",\"title\":\"Invalid patch value\",\"status\":400,\"detail\":\"bad state\"}"
    );

    Captured cap = CliRunner.run(new DataciteSetStateCommand(), backend.baseUrl(), "test-key", "nonsense");

    assertThat(cap.exit()).isNotEqualTo(0);
    assertThat(cap.stderr()).contains("error");
  }

  // ─── password ──────────────────────────────────────────────────────────

  @Test
  void setPassword_readsFromStdinAndPostsCredential() {
    backend.route(
      "/v2/admin/minters/datacite/credential",
      200,
      rr -> "{\"passwordSet\":true,\"fingerprint\":\"abcd1234\"}"
    );

    DataciteSetPasswordCommand cmd = new DataciteSetPasswordCommand() {
      @Override
      String readPassword() {
        return "the-super-secret";
      }
    };

    Captured cap = CliRunner.run(cmd, backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests()).hasSize(1);
    assertThat(backend.requests().get(0).method()).isEqualTo("POST");
    assertThat(backend.requests().get(0).body()).contains("\"password\":\"the-super-secret\"");
    assertThat(cap.stdout()).contains("abcd1234");
    // CRITICAL: plaintext does NOT appear in stdout / stderr.
    assertThat(cap.stdout()).doesNotContain("the-super-secret");
    assertThat(cap.stderr()).doesNotContain("the-super-secret");
  }

  @Test
  void setPassword_readsFromActualStdinForNonTtyInvocation() {
    backend.route(
      "/v2/admin/minters/datacite/credential",
      200,
      rr -> "{\"passwordSet\":true,\"fingerprint\":\"feedface\"}"
    );

    InputStream originalIn = System.in;
    try {
      System.setIn(new ByteArrayInputStream("piped-password\n".getBytes(StandardCharsets.UTF_8)));
      Captured cap = CliRunner.run(new DataciteSetPasswordCommand(), backend.baseUrl(), "test-key");

      assertThat(cap.exit()).isEqualTo(0);
      assertThat(backend.requests().get(0).body()).contains("\"password\":\"piped-password\"");
    } finally {
      System.setIn(originalIn);
    }
  }

  @Test
  void setPassword_refusesBlankInput() {
    DataciteSetPasswordCommand cmd = new DataciteSetPasswordCommand() {
      @Override
      String readPassword() {
        return "";
      }
    };

    Captured cap = CliRunner.run(cmd, backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isNotEqualTo(0);
    assertThat(cap.stderr()).contains("blank password");
    assertThat(backend.requests()).isEmpty();
  }

  @Test
  void clearPassword_sendsDeleteAndPrintsConfirmation() {
    backend.route("/v2/admin/minters/datacite/credential", 200, rr ->
      "{\"enabled\":false,\"apiBaseUrl\":\"https://x\",\"passwordSet\":false}"
    );

    Captured cap = CliRunner.run(new DataciteClearPasswordCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).method()).isEqualTo("DELETE");
    assertThat(cap.stdout()).contains("cleared");
    assertThat(cap.stdout()).contains("passwordSet=false");
  }

  // ─── test-connection ───────────────────────────────────────────────────

  @Test
  void testConnection_reachableReturnsZeroExit() {
    backend.route("/v2/admin/minters/datacite/test-connection", 200, rr ->
      "{\"reachable\":true,\"statusCode\":200,\"latencyMs\":42,\"apiBaseUrl\":\"https://api.test.datacite.org\"}"
    );

    Captured cap = CliRunner.run(new DataciteTestConnectionCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("reachable");
    assertThat(cap.stdout()).contains("true");
  }

  @Test
  void testConnection_unreachableReturnsNonZeroExit() {
    backend.route("/v2/admin/minters/datacite/test-connection", 200, rr ->
      "{\"reachable\":false,\"statusCode\":0,\"latencyMs\":5,\"apiBaseUrl\":\"https://api.datacite.org\",\"detail\":\"network down\"}"
    );

    Captured cap = CliRunner.run(new DataciteTestConnectionCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(1);
    assertThat(cap.stdout()).contains("network down");
  }

  @Test
  void testConnection_jsonOutputAlsoEncodesExitCode() {
    backend.route("/v2/admin/minters/datacite/test-connection", 200, rr ->
      "{\"reachable\":true,\"statusCode\":200,\"latencyMs\":1}"
    );

    Captured cap = CliRunner.run(new DataciteTestConnectionCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"reachable\"");
  }

  @Test
  void apiKeyHeaderForwarded() {
    backend.route("/v2/admin/minters/datacite/config", 200, rr -> STATUS_BODY);

    CliRunner.run(new DataciteStatusCommand(), backend.baseUrl(), "secret-api-key");

    assertThat(backend.requests().get(0).apiKeyHeader()).isEqualTo("secret-api-key");
  }
}
