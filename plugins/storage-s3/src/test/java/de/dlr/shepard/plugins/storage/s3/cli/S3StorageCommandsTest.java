package de.dlr.shepard.plugins.storage.s3.cli;

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
 * FS1b — end-to-end CLI surface tests for
 * {@code shepard-admin storage s3 <verb>}. Each test wires the
 * Picocli command against an in-process {@link StubBackend}.
 */
class S3StorageCommandsTest {

  private static final String STATUS_BODY = """
      {
        "enabled":true,
        "endpointUrl":"https://garage.example.org",
        "region":"eu-central-1",
        "bucket":"my-shepard-bucket",
        "bucketPrefix":"",
        "forcePathStyle":true,
        "accessKeyId":"AKIA123",
        "secretKeySet":true,
        "secretKeyFingerprint":"deadbeef",
        "sseAlgorithm":"",
        "multipartThresholdBytes":16777216,
        "connectionTimeoutSeconds":10,
        "requestTimeoutSeconds":30,
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
    backend.route("/v2/admin/storage/s3/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new S3StatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).withFailMessage(cap.stderr()).isEqualTo(0);
    assertThat(cap.stdout()).contains("enabled");
    assertThat(cap.stdout()).contains("true");
    assertThat(cap.stdout()).contains("my-shepard-bucket");
    assertThat(cap.stdout()).contains("deadbeef");
    assertThat(cap.stdout()).contains("AKIA123");
  }

  @Test
  void status_jsonOutput_emitsParseableJson() {
    backend.route("/v2/admin/storage/s3/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new S3StatusCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"enabled\"");
    assertThat(cap.stdout()).contains("\"bucket\"");
  }

  @Test
  void status_showsNoCredentialWhenSecretKeySetFalse() {
    String body = STATUS_BODY
      .replace("\"secretKeySet\":true", "\"secretKeySet\":false")
      .replace("\"secretKeyFingerprint\":\"deadbeef\"", "\"secretKeyFingerprint\":null");
    backend.route("/v2/admin/storage/s3/config", 200, rr -> body);

    Captured cap = CliRunner.run(new S3StatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("(no credential)");
  }

  // ─── enable / disable ──────────────────────────────────────────────────

  @Test
  void enable_sendsPatchWithEnabledTrue() {
    backend.route("/v2/admin/storage/s3/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new S3EnableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    List<RecordedRequest> rqs = backend.requests();
    assertThat(rqs).hasSize(1);
    assertThat(rqs.get(0).method()).isEqualTo("PATCH");
    assertThat(rqs.get(0).body()).contains("\"enabled\":true");
    assertThat(cap.stdout()).contains("enabled");
  }

  @Test
  void enable_warnsWhenSecretKeyMissing() {
    String body = STATUS_BODY.replace("\"secretKeySet\":true", "\"secretKeySet\":false");
    backend.route("/v2/admin/storage/s3/config", 200, rr -> body);

    Captured cap = CliRunner.run(new S3EnableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stderr()).contains("secretKeySet=false");
  }

  @Test
  void disable_sendsPatchWithEnabledFalse() {
    backend.route("/v2/admin/storage/s3/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new S3DisableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"enabled\":false");
    assertThat(cap.stdout()).contains("disabled");
  }

  // ─── set-* ────────────────────────────────────────────────────────────

  @Test
  void setEndpoint_sendsPatchWithEndpointUrl() {
    backend.route("/v2/admin/storage/s3/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(
      new S3SetEndpointCommand(),
      backend.baseUrl(),
      "test-key",
      "https://garage.example.org"
    );

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"endpointUrl\":\"https://garage.example.org\"");
  }

  @Test
  void setRegion_sendsPatchWithRegion() {
    backend.route("/v2/admin/storage/s3/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new S3SetRegionCommand(), backend.baseUrl(), "test-key", "eu-central-1");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"region\":\"eu-central-1\"");
    assertThat(cap.stdout()).contains("eu-central-1");
  }

  @Test
  void setBucket_sendsPatchWithBucket() {
    backend.route("/v2/admin/storage/s3/config", 200, rr -> STATUS_BODY);

    Captured cap = CliRunner.run(new S3SetBucketCommand(), backend.baseUrl(), "test-key", "new-bucket");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests().get(0).body()).contains("\"bucket\":\"new-bucket\"");
  }

  // ─── set-credentials (reads from stdin) ────────────────────────────────

  @Test
  void setCredentials_readsSecretFromStdinAndPostsCredential() {
    backend.route(
      "/v2/admin/storage/s3/credential",
      200,
      rr -> "{\"secretKeySet\":true,\"secretKeyFingerprint\":\"abcd1234\"}"
    );

    S3SetCredentialsCommand cmd = new S3SetCredentialsCommand() {
      @Override
      String readSecretKey() {
        return "the-super-secret-key";
      }
    };
    cmd.accessKeyId = "AKIA123";

    Captured cap = CliRunner.run(cmd, backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests()).hasSize(1);
    assertThat(backend.requests().get(0).method()).isEqualTo("POST");
    assertThat(backend.requests().get(0).body()).contains("\"accessKeyId\":\"AKIA123\"");
    assertThat(backend.requests().get(0).body()).contains("\"secretKey\":\"the-super-secret-key\"");
    assertThat(cap.stdout()).contains("abcd1234");
    // CRITICAL: plaintext must NOT appear in output.
    assertThat(cap.stdout()).doesNotContain("the-super-secret-key");
    assertThat(cap.stderr()).doesNotContain("the-super-secret-key");
  }

  @Test
  void setCredentials_readsFromActualStdinForNonTtyInvocation() {
    backend.route(
      "/v2/admin/storage/s3/credential",
      200,
      rr -> "{\"secretKeySet\":true,\"secretKeyFingerprint\":\"feedface\"}"
    );

    S3SetCredentialsCommand cmd = new S3SetCredentialsCommand();
    cmd.accessKeyId = "AKIA999";

    InputStream originalIn = System.in;
    try {
      System.setIn(new ByteArrayInputStream("piped-secret-key\n".getBytes(StandardCharsets.UTF_8)));
      Captured cap = CliRunner.run(cmd, backend.baseUrl(), "test-key");

      assertThat(cap.exit()).isEqualTo(0);
      assertThat(backend.requests().get(0).body()).contains("\"secretKey\":\"piped-secret-key\"");
    } finally {
      System.setIn(originalIn);
    }
  }

  @Test
  void setCredentials_refusesBlankSecretKey() {
    S3SetCredentialsCommand cmd = new S3SetCredentialsCommand() {
      @Override
      String readSecretKey() {
        return "";
      }
    };
    cmd.accessKeyId = "AKIA123";

    Captured cap = CliRunner.run(cmd, backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isNotEqualTo(0);
    assertThat(cap.stderr()).contains("blank");
    assertThat(backend.requests()).isEmpty();
  }

  // ─── clear-credentials ──────────────────────────────────────────────────

  @Test
  void clearCredentials_sendsDeleteAndPrintsConfirmation() {
    backend.route(
      "/v2/admin/storage/s3/credential",
      200,
      rr -> "{\"enabled\":true,\"bucket\":\"my-bucket\",\"secretKeySet\":false}"
    );

    Captured cap = CliRunner.run(new S3ClearCredentialsCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(backend.requests()).hasSize(1);
    assertThat(backend.requests().get(0).method()).isEqualTo("DELETE");
    assertThat(cap.stdout()).contains("cleared");
  }

  // ─── test-connection ────────────────────────────────────────────────────

  @Test
  void testConnection_reachable_exits0() {
    backend.route(
      "/v2/admin/storage/s3/test-connection",
      200,
      rr -> "{\"reachable\":true,\"statusCode\":200,\"latencyMs\":42,\"endpoint\":\"https://garage.example.org\",\"bucket\":\"my-bucket\",\"detail\":null}"
    );

    Captured cap = CliRunner.run(new S3TestConnectionCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("true");
    assertThat(cap.stdout()).contains("200");
    assertThat(cap.stdout()).contains("42");
  }

  @Test
  void testConnection_unreachable_exits1() {
    backend.route(
      "/v2/admin/storage/s3/test-connection",
      200,
      rr -> "{\"reachable\":false,\"statusCode\":0,\"latencyMs\":5000,\"endpoint\":\"https://garage.example.org\",\"bucket\":\"my-bucket\",\"detail\":\"connection refused\"}"
    );

    Captured cap = CliRunner.run(new S3TestConnectionCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(1);
    assertThat(cap.stdout()).contains("false");
    assertThat(cap.stdout()).contains("connection refused");
  }

  @Test
  void testConnection_jsonOutput_emitsParseableJson() {
    backend.route(
      "/v2/admin/storage/s3/test-connection",
      200,
      rr -> "{\"reachable\":true,\"statusCode\":200,\"latencyMs\":10,\"endpoint\":\"https://s3.example.com\",\"bucket\":\"b\",\"detail\":null}"
    );

    Captured cap = CliRunner.run(new S3TestConnectionCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"reachable\"");
    assertThat(cap.stdout()).contains("\"latencyMs\"");
  }
}
