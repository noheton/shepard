package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import de.dlr.shepard.cli.support.StubBackend.RecordedRequest;
import org.junit.jupiter.api.Test;

/**
 * ROR1 — exercises {@code shepard-admin instance ror set}.
 * Issues {@code PATCH /v2/admin/instance/ror} with a correctly-formed
 * RFC 7396 merge-patch body for the supplied flags.
 */
final class InstanceRorSetCommandTest {

  private static final String ROR_CONFIGURED =
    """
    {
      "rorId": "04cvxnb49",
      "organizationName": "DLR e.V.",
      "rorUrl": "https://ror.org/04cvxnb49"
    }
    """;

  private static final String ROR_ORG_ONLY =
    """
    {
      "organizationName": "Example Org"
    }
    """;

  @Test
  void setRorId_patchesCorrectBody_andExitsZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorSetCommand.ROR_PATH, 200, ROR_CONFIGURED);

      Captured result = CliRunner.run(
        new InstanceRorSetCommand(),
        backend.baseUrl(),
        "test-key",
        "--ror-id", "04cvxnb49"
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.method()).isEqualTo("PATCH");
      assertThat(req.path()).isEqualTo(InstanceRorSetCommand.ROR_PATH);
      // Only rorId should be present in the patch body (not organizationName)
      assertThat(req.body()).contains("\"rorId\"").contains("04cvxnb49");
      assertThat(req.body()).doesNotContain("organizationName");
      // Human output contains updated values
      assertThat(result.stdout())
        .contains("04cvxnb49")
        .contains("DLR e.V.");
      assertThat(result.stderr()).isEmpty();
    }
  }

  @Test
  void setOrgName_patchesCorrectBody_andExitsZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorSetCommand.ROR_PATH, 200, ROR_ORG_ONLY);

      Captured result = CliRunner.run(
        new InstanceRorSetCommand(),
        backend.baseUrl(),
        "test-key",
        "--org-name", "Example Org"
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.body()).contains("\"organizationName\"").contains("Example Org");
      assertThat(req.body()).doesNotContain("rorId");
    }
  }

  @Test
  void setBothFlags_patchesAllFields() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorSetCommand.ROR_PATH, 200, ROR_CONFIGURED);

      Captured result = CliRunner.run(
        new InstanceRorSetCommand(),
        backend.baseUrl(),
        "test-key",
        "--ror-id", "04cvxnb49",
        "--org-name", "DLR e.V."
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.body()).contains("\"rorId\"").contains("\"organizationName\"");
    }
  }

  @Test
  void emptyRorId_sendsExplicitNull_clearingTheField() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      // Server responds with empty object when rorId is cleared
      backend.route(InstanceRorSetCommand.ROR_PATH, 200, "{}");

      Captured result = CliRunner.run(
        new InstanceRorSetCommand(),
        backend.baseUrl(),
        "test-key",
        "--ror-id", ""
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      // Empty string → explicit null in JSON (RFC 7396 clear)
      assertThat(req.body()).contains("\"rorId\":null");
    }
  }

  @Test
  void noArgs_returnsExitTwo_withUsageHint() {
    // No StubBackend needed — validation happens before any HTTP call
    Captured result = CliRunner.run(
      new InstanceRorSetCommand(),
      "http://127.0.0.1:1",
      "test-key"
    );

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.stderr())
      .contains("at least one of --ror-id or --org-name");
  }

  @Test
  void jsonOutput_emitsUpdatedConfig() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(InstanceRorSetCommand.ROR_PATH, 200, ROR_CONFIGURED);

      Captured result = CliRunner.run(
        new InstanceRorSetCommand(),
        backend.baseUrl(),
        "test-key",
        "--ror-id", "04cvxnb49",
        "--output", "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("04cvxnb49");
    }
  }
}
