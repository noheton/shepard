package de.dlr.shepard.plugins.minter.epic.cli;

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
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KIP1c — end-to-end CLI surface tests for
 * {@code shepard-admin minters epic <verb>}. Each test wires the
 * Picocli command against an in-process {@link StubBackend}.
 */
class EpicCommandsTest {

  private static final String STATUS_BODY = """
      {
        "enabled":true,
        "apiBaseUrl":"https://handle.argo.grnet.gr/api",
        "handlePrefix":"21.T11148",
        "credentialSet":true,
        "credentialFingerprint":"deadbeef",
        "updatedAt":"2026-05-17T10:00:00.000+00:00",
        "updatedBy":"alice"
      }
      """;

  private static final String CREDENTIAL_SET_BODY = """
      {"credentialSet":true,"fingerprint":"cafebabe"}
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
    backend.route("/v2/admin/minters/epic/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new EpicStatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
    assertThat(cap.stdout()).contains("enabled");
    assertThat(cap.stdout()).contains("true");
    assertThat(cap.stdout()).contains("21.T11148");
    assertThat(cap.stdout()).contains("deadbeef");
  }

  @Test
  void status_jsonOutput_emitsParseableJson() {
    backend.route("/v2/admin/minters/epic/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new EpicStatusCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"enabled\"");
    assertThat(cap.stdout()).contains("\"apiBaseUrl\"");
  }

  // ─── enable / disable ──────────────────────────────────────────────────

  @Test
  void enable_patchesEnabledTrue() {
    backend.route("/v2/admin/minters/epic/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new EpicEnableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
    List<RecordedRequest> requests = backend.requests().stream()
      .filter(r -> r.path().equals("/v2/admin/minters/epic/config"))
      .collect(Collectors.toList());
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).method()).isEqualTo("PATCH");
    assertThat(requests.get(0).body()).contains("\"enabled\"");
    assertThat(requests.get(0).body()).contains("true");
  }

  @Test
  void disable_patchesEnabledFalse() {
    backend.route("/v2/admin/minters/epic/config", 200, rr -> STATUS_BODY.replace("\"enabled\":true", "\"enabled\":false"));

    Captured cap = CliRunner.run(new EpicDisableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
    List<RecordedRequest> requests = backend.requests().stream()
      .filter(r -> r.path().equals("/v2/admin/minters/epic/config"))
      .collect(Collectors.toList());
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).body()).contains("false");
  }

  // ─── set-api-url ───────────────────────────────────────────────────────

  @Test
  void setApiUrl_patchesApiBaseUrl() {
    backend.route("/v2/admin/minters/epic/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(
      new EpicSetApiUrlCommand(),
      backend.baseUrl(),
      "test-key",
      "https://epic.example.org/api"
    );

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
    List<RecordedRequest> requests = backend.requests().stream()
      .filter(r -> r.path().equals("/v2/admin/minters/epic/config"))
      .collect(Collectors.toList());
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).body()).contains("apiBaseUrl");
    assertThat(requests.get(0).body()).contains("epic.example.org");
  }

  // ─── set-prefix ────────────────────────────────────────────────────────

  @Test
  void setPrefix_patchesHandlePrefix() {
    backend.route("/v2/admin/minters/epic/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(
      new EpicSetPrefixCommand(),
      backend.baseUrl(),
      "test-key",
      "21.XXXXX"
    );

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
    List<RecordedRequest> requests = backend.requests().stream()
      .filter(r -> r.path().equals("/v2/admin/minters/epic/config"))
      .collect(Collectors.toList());
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).body()).contains("handlePrefix");
    assertThat(requests.get(0).body()).contains("21.XXXXX");
  }

  // ─── set-credential ────────────────────────────────────────────────────

  @Test
  void setCredential_postsCredential_fromStdin() {
    backend.route("/v2/admin/minters/epic/credential", 200, rr -> CREDENTIAL_SET_BODY);

    InputStream originalStdin = System.in;
    try {
      System.setIn(new ByteArrayInputStream("user:s3cret\n".getBytes(StandardCharsets.UTF_8)));
      Captured cap = CliRunner.run(new EpicSetCredentialCommand(), backend.baseUrl(), "test-key");
      assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
      assertThat(cap.stdout()).contains("fingerprint");
    } finally {
      System.setIn(originalStdin);
    }
  }

  // ─── clear-credential ──────────────────────────────────────────────────

  @Test
  void clearCredential_sendsDelete() {
    String clearedBody = STATUS_BODY
      .replace("\"credentialSet\":true", "\"credentialSet\":false")
      .replace("\"credentialFingerprint\":\"deadbeef\"", "\"credentialFingerprint\":null");
    backend.route("/v2/admin/minters/epic/credential", 200, rr -> clearedBody);

    Captured cap = CliRunner.run(new EpicClearCredentialCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
    List<RecordedRequest> requests = backend.requests().stream()
      .filter(r -> r.path().equals("/v2/admin/minters/epic/credential"))
      .collect(Collectors.toList());
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).method()).isEqualTo("DELETE");
  }

  // ─── test-connection ───────────────────────────────────────────────────

  @Test
  void testConnection_exits0OnReachable() {
    backend.route(
      "/v2/admin/minters/epic/test-connection",
      200,
      rr -> "{\"reachable\":true,\"statusCode\":200,\"latencyMs\":45,\"apiBaseUrl\":\"https://handle.argo.grnet.gr/api\"}"
    );

    Captured cap = CliRunner.run(new EpicTestConnectionCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("reachable");
    assertThat(cap.stdout()).contains("true");
  }

  @Test
  void testConnection_exits1OnUnreachable() {
    backend.route(
      "/v2/admin/minters/epic/test-connection",
      200,
      rr -> "{\"reachable\":false,\"statusCode\":0,\"latencyMs\":0,\"detail\":\"network error\"}"
    );

    Captured cap = CliRunner.run(new EpicTestConnectionCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(1);
  }

  @Test
  void testConnection_jsonOutput_emitsParseableJson() {
    backend.route(
      "/v2/admin/minters/epic/test-connection",
      200,
      rr -> "{\"reachable\":true,\"statusCode\":200,\"latencyMs\":10,\"apiBaseUrl\":\"https://x\"}"
    );

    Captured cap = CliRunner.run(
      new EpicTestConnectionCommand(),
      backend.baseUrl(),
      "test-key",
      "--output=json"
    );

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"reachable\"");
    assertThat(cap.stdout()).contains("\"statusCode\"");
  }
}
