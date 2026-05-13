package de.dlr.shepard.plugins.unhide.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * UH1a — end-to-end CLI surface tests for {@code unhide <verb>}.
 *
 * <p>Each test wires the picocli command against an in-process
 * {@link StubBackend} so we exercise the real {@code HttpClient} +
 * Jackson + Picocli wiring without booting Quarkus. Same harness
 * used by the core CLI command tests; the harness comes from the
 * CLI's `tests` classifier JAR (PM1d).
 *
 * <p>PM1d — moved from {@code cli/src/test/...UnhideCommandsTest} to
 * this plugin module's test tree once the
 * {@code AdminCliCommandProvider} SPI shipped. The
 * {@code shepard-admin unhide …} end-user UX is byte-identical —
 * the test assertions on output strings and request bodies didn't
 * change beyond the package-relocation imports.
 */
class UnhideCommandsTest {

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
  void status_humanOutput_renderTable() {
    backend.route(
      "/v2/admin/unhide/config",
      200,
      rr -> "{\"enabled\":true,\"feedPublic\":false,\"contactEmail\":\"ops@example.dlr.de\"," +
        "\"harvestApiKeyMintedAt\":\"2026-05-13T05:11:00Z\",\"harvestApiKeyFingerprint\":\"01234567\"}"
    );

    Captured cap = CliRunner.run(new UnhideStatusCommand(), backend.baseUrl(), "test-key");

    assertEquals(0, cap.exit(), cap.stderr());
    assertTrue(cap.stdout().contains("enabled"));
    assertTrue(cap.stdout().contains("true"));
    assertTrue(cap.stdout().contains("01234567"), "fingerprint surfaced in human output: " + cap.stdout());
  }

  @Test
  void status_jsonOutput_emitsParseableJson() {
    backend.route(
      "/v2/admin/unhide/config",
      200,
      rr -> "{\"enabled\":true,\"feedPublic\":false,\"contactEmail\":null}"
    );

    Captured cap = CliRunner.run(new UnhideStatusCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertEquals(0, cap.exit(), cap.stderr());
    assertTrue(cap.stdout().contains("\"enabled\""));
    assertTrue(cap.stdout().contains("true"));
  }

  // ─── enable / disable ────────────────────────────────────────────────────

  @Test
  void enable_sendsPatchWithEnabledTrue() {
    backend.route("/v2/admin/unhide/config", 200, rr ->
      "{\"enabled\":true,\"feedPublic\":false,\"contactEmail\":null}"
    );

    Captured cap = CliRunner.run(new UnhideEnableCommand(), backend.baseUrl(), "test-key");

    assertEquals(0, cap.exit(), cap.stderr());
    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertEquals("PATCH", requests.get(0).method());
    assertTrue(requests.get(0).body().contains("\"enabled\":true"), "PATCH body sets enabled=true: " + requests.get(0).body());
    assertTrue(cap.stdout().contains("Unhide enabled"));
  }

  @Test
  void disable_sendsPatchWithEnabledFalse() {
    backend.route("/v2/admin/unhide/config", 200, rr ->
      "{\"enabled\":false,\"feedPublic\":false,\"contactEmail\":null}"
    );

    Captured cap = CliRunner.run(new UnhideDisableCommand(), backend.baseUrl(), "test-key");

    assertEquals(0, cap.exit(), cap.stderr());
    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertEquals("PATCH", requests.get(0).method());
    assertTrue(requests.get(0).body().contains("\"enabled\":false"));
    assertTrue(cap.stdout().contains("Unhide disabled"));
  }

  // ─── set-feed-public ─────────────────────────────────────────────────────

  @Test
  void setFeedPublic_true_sendsPatch() {
    backend.route("/v2/admin/unhide/config", 200, rr ->
      "{\"enabled\":true,\"feedPublic\":true,\"contactEmail\":null}"
    );

    Captured cap = CliRunner.run(new UnhideSetFeedPublicCommand(), backend.baseUrl(), "test-key", "true");

    assertEquals(0, cap.exit(), cap.stderr());
    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertTrue(requests.get(0).body().contains("\"feedPublic\":true"));
    assertTrue(cap.stdout().contains("feedPublic = true"));
  }

  // ─── set-contact-email ───────────────────────────────────────────────────

  @Test
  void setContactEmail_setsValue() {
    backend.route("/v2/admin/unhide/config", 200, rr ->
      "{\"enabled\":true,\"feedPublic\":false,\"contactEmail\":\"alice@example.dlr.de\"}"
    );

    Captured cap = CliRunner.run(
      new UnhideSetContactEmailCommand(),
      backend.baseUrl(),
      "test-key",
      "alice@example.dlr.de"
    );

    assertEquals(0, cap.exit(), cap.stderr());
    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertTrue(requests.get(0).body().contains("\"contactEmail\":\"alice@example.dlr.de\""));
    assertTrue(cap.stdout().contains("alice@example.dlr.de"));
  }

  @Test
  void setContactEmail_emptyArg_sendsNullToClear() {
    backend.route("/v2/admin/unhide/config", 200, rr ->
      "{\"enabled\":true,\"feedPublic\":false,\"contactEmail\":null}"
    );

    Captured cap = CliRunner.run(new UnhideSetContactEmailCommand(), backend.baseUrl(), "test-key");

    assertEquals(0, cap.exit(), cap.stderr());
    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    // The body contains "contactEmail":null
    assertTrue(requests.get(0).body().contains("\"contactEmail\":null"),
      "explicit-null clears the field: " + requests.get(0).body());
  }

  // ─── rotate / revoke ─────────────────────────────────────────────────────

  @Test
  void rotateHarvestKey_returnsPlaintextOnStdout_warningOnStderr() {
    backend.route(
      "/v2/admin/unhide/harvest-key/rotate",
      200,
      rr -> "{\"harvestApiKey\":\"11111111-2222-4333-8444-555555555555\"," +
        "\"fingerprint\":\"abcdef01\",\"mintedAt\":\"2026-05-13T05:00:00Z\"," +
        "\"warning\":\"This is the only time this harvest API key is shown.\"}"
    );

    Captured cap = CliRunner.run(new UnhideRotateHarvestKeyCommand(), backend.baseUrl(), "admin-key");

    assertEquals(0, cap.exit(), cap.stderr());
    // The plaintext is on stdout — operator pipes it to a secret store.
    assertTrue(cap.stdout().contains("11111111-2222-4333-8444-555555555555"),
      "plaintext on stdout: " + cap.stdout());
    // Warning + fingerprint on stderr (so they don't pollute machine-readable
    // stdout pipes).
    assertTrue(cap.stderr().contains("abcdef01"), "fingerprint on stderr: " + cap.stderr());
    assertTrue(cap.stderr().contains("WARNING"), "warning on stderr: " + cap.stderr());
  }

  @Test
  void rotateHarvestKey_jsonOutput_emitsFullJson() {
    backend.route(
      "/v2/admin/unhide/harvest-key/rotate",
      200,
      rr -> "{\"harvestApiKey\":\"11111111-2222-4333-8444-555555555555\"," +
        "\"fingerprint\":\"abcdef01\",\"mintedAt\":\"2026-05-13T05:00:00Z\"," +
        "\"warning\":\"This is the only time this harvest API key is shown.\"}"
    );

    Captured cap = CliRunner.run(
      new UnhideRotateHarvestKeyCommand(),
      backend.baseUrl(),
      "admin-key",
      "--output=json"
    );

    assertEquals(0, cap.exit(), cap.stderr());
    assertTrue(cap.stdout().contains("\"harvestApiKey\""));
    assertTrue(cap.stdout().contains("\"fingerprint\""));
  }

  @Test
  void revokeHarvestKey_postShape() {
    backend.route(
      "/v2/admin/unhide/harvest-key/revoke",
      200,
      rr -> "{\"enabled\":true,\"feedPublic\":false,\"contactEmail\":null,\"harvestApiKeyFingerprint\":null}"
    );

    Captured cap = CliRunner.run(new UnhideRevokeHarvestKeyCommand(), backend.baseUrl(), "admin-key");

    assertEquals(0, cap.exit(), cap.stderr());
    assertTrue(cap.stdout().contains("revoked"));
    assertTrue(cap.stdout().contains("(none)"));
    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertEquals("POST", requests.get(0).method());
  }

  // ─── auth-header propagation ─────────────────────────────────────────────

  @Test
  void allCommands_sendXApiKeyHeader() {
    backend.route(
      "/v2/admin/unhide/config",
      200,
      rr -> "{\"enabled\":true,\"feedPublic\":false,\"contactEmail\":null}"
    );

    CliRunner.run(new UnhideStatusCommand(), backend.baseUrl(), "operator-key");

    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertEquals("operator-key", requests.get(0).apiKeyHeader(), "X-API-KEY propagated to backend");
  }

  // ─── parent wiring ───────────────────────────────────────────────────────

  @Test
  void shepardAdminTopLevel_wiresUnhideSubcommand_viaServiceLoader() {
    // PM1d: `unhide` is no longer a hard-coded subcommand on the
    // `ShepardAdmin` root — it's contributed via the
    // `AdminCliCommandProvider` SPI. Use `ShepardAdmin.commandLine()`
    // so the `CliPluginBootstrap` runs its classpath ServiceLoader
    // pass and registers the verb.
    picocli.CommandLine cmd = de.dlr.shepard.cli.ShepardAdmin.commandLine();
    assertNotNull(cmd.getSubcommands().get("unhide"), "shepard-admin must surface the unhide group");
    picocli.CommandLine sub = cmd.getSubcommands().get("unhide");
    // All seven verbs must be reachable from the unhide parent.
    for (String v : List.of("status", "enable", "disable", "set-feed-public",
                             "set-contact-email", "rotate-harvest-key", "revoke-harvest-key")) {
      assertNotNull(sub.getSubcommands().get(v), "unhide must surface `" + v + "`");
    }
  }
}
