package de.dlr.shepard.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * PM1b — end-to-end CLI surface tests for {@code plugins <verb>}.
 *
 * <p>Same harness as {@code UnhideCommandsTest} +
 * {@code SemanticOntologiesCommandTest} — picocli wired against an
 * in-process {@link StubBackend} so we exercise the real
 * {@code HttpClient} + Jackson + Picocli pipeline without booting
 * Quarkus. The tests cover:
 *
 * <ul>
 *   <li>{@code list} happy path (table + JSON output).</li>
 *   <li>{@code list} 401 → exit 1 with the
 *       {@code SHEPARD_ADMIN_API_KEY} hint.</li>
 *   <li>{@code enable} / {@code disable} happy path (PATCH body
 *       shape, success message).</li>
 *   <li>{@code enable} 404 → exit 1 surfacing the missing-id
 *       message.</li>
 *   <li>Parent-wiring check (each verb reachable from
 *       {@code shepard-admin plugins}).</li>
 * </ul>
 */
class PluginsCommandsTest {

  private StubBackend backend;

  @BeforeEach
  void setUp() throws IOException {
    backend = StubBackend.start();
  }

  @AfterEach
  void tearDown() {
    backend.close();
  }

  // ─── list ────────────────────────────────────────────────────────────────

  @Test
  void list_humanOutput_renderTable() {
    backend.route(
      "/v2/admin/plugins",
      200,
      rr -> "{\"plugins\":[{\"id\":\"unhide\",\"version\":\"1.0.0\"," +
        "\"shepardCompatibility\":\">=5.2.0,<6\",\"state\":\"ENABLED\",\"enabled\":true," +
        "\"sourcePath\":\"/deployments/plugins/shepard-plugin-unhide-1.0.0.jar\"," +
        "\"registeredAt\":\"2026-05-13T05:00:00Z\"}]}"
    );

    Captured cap = CliRunner.run(new PluginsListCommand(), backend.baseUrl(), "test-key");

    assertEquals(0, cap.exit(), cap.stderr());
    assertTrue(cap.stdout().contains("ID"), "header rendered: " + cap.stdout());
    assertTrue(cap.stdout().contains("unhide"), "plugin id rendered: " + cap.stdout());
    assertTrue(cap.stdout().contains("1.0.0"), "version rendered: " + cap.stdout());
    assertTrue(cap.stdout().contains("ENABLED"), "state rendered: " + cap.stdout());
    assertTrue(cap.stdout().contains("true"), "enabled toggle rendered: " + cap.stdout());
    assertTrue(cap.stdout().contains("shepard-plugin-unhide-1.0.0.jar"), "source path rendered: " + cap.stdout());
  }

  @Test
  void list_jsonOutput_emitsParseableJson() {
    backend.route(
      "/v2/admin/plugins",
      200,
      rr -> "{\"plugins\":[{\"id\":\"unhide\",\"version\":\"1.0.0\"," +
        "\"shepardCompatibility\":\">=5.2.0,<6\",\"state\":\"ENABLED\",\"enabled\":true," +
        "\"sourcePath\":null,\"registeredAt\":\"2026-05-13T05:00:00Z\"}]}"
    );

    Captured cap = CliRunner.run(new PluginsListCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertEquals(0, cap.exit(), cap.stderr());
    assertTrue(cap.stdout().contains("\"plugins\""), "envelope key on stdout: " + cap.stdout());
    assertTrue(cap.stdout().contains("\"id\""), "plugin id key on stdout");
    assertTrue(cap.stdout().contains("\"unhide\""));
  }

  @Test
  void list_buildClasspathPlugin_humanRendersPlaceholder() {
    backend.route(
      "/v2/admin/plugins",
      200,
      rr -> "{\"plugins\":[{\"id\":\"unhide\",\"version\":\"1.0.0\"," +
        "\"shepardCompatibility\":\">=5.2.0,<6\",\"state\":\"ENABLED\",\"enabled\":true," +
        "\"sourcePath\":null,\"registeredAt\":\"2026-05-13T05:00:00Z\"}]}"
    );

    Captured cap = CliRunner.run(new PluginsListCommand(), backend.baseUrl(), "test-key");

    assertEquals(0, cap.exit(), cap.stderr());
    assertTrue(
      cap.stdout().contains("(build-classpath)"),
      "null sourcePath collapses to placeholder: " + cap.stdout()
    );
  }

  @Test
  void list_unauthorized_exitsOneWithApiKeyHint() {
    backend.route(
      "/v2/admin/plugins",
      401,
      rr -> "{\"type\":\"/problems/auth.denied\",\"title\":\"Auth required\",\"status\":401," +
        "\"detail\":\"Authentication required\"}"
    );

    Captured cap = CliRunner.run(new PluginsListCommand(), backend.baseUrl(), "wrong-key");

    assertEquals(1, cap.exit(), "401 → exit 1");
    assertTrue(
      cap.stderr().contains("SHEPARD_ADMIN_API_KEY") || cap.stderr().contains("--api-key"),
      "stderr names the API key env / flag: " + cap.stderr()
    );
  }

  // ─── enable / disable ────────────────────────────────────────────────────

  @Test
  void enable_sendsPatchWithEnabledTrue() {
    backend.route(
      "/v2/admin/plugins/unhide",
      200,
      rr -> "{\"id\":\"unhide\",\"version\":\"1.0.0\"," +
        "\"shepardCompatibility\":\">=5.2.0,<6\",\"state\":\"ENABLED\",\"enabled\":true," +
        "\"sourcePath\":null,\"registeredAt\":\"2026-05-13T05:00:00Z\"}"
    );

    Captured cap = CliRunner.run(new PluginsEnableCommand(), backend.baseUrl(), "test-key", "unhide");

    assertEquals(0, cap.exit(), cap.stderr());
    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertEquals("PATCH", requests.get(0).method());
    assertEquals("/v2/admin/plugins/unhide", requests.get(0).path());
    assertTrue(requests.get(0).body().contains("\"enabled\":true"), "PATCH body sets enabled=true: " + requests.get(0).body());
    assertTrue(cap.stdout().contains("Plugin 'unhide' enabled"), "success message: " + cap.stdout());
  }

  @Test
  void disable_sendsPatchWithEnabledFalse() {
    backend.route(
      "/v2/admin/plugins/unhide",
      200,
      rr -> "{\"id\":\"unhide\",\"version\":\"1.0.0\"," +
        "\"shepardCompatibility\":\">=5.2.0,<6\",\"state\":\"ENABLED\",\"enabled\":false," +
        "\"sourcePath\":null,\"registeredAt\":\"2026-05-13T05:00:00Z\"}"
    );

    Captured cap = CliRunner.run(new PluginsDisableCommand(), backend.baseUrl(), "test-key", "unhide");

    assertEquals(0, cap.exit(), cap.stderr());
    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertEquals("PATCH", requests.get(0).method());
    assertTrue(requests.get(0).body().contains("\"enabled\":false"), "PATCH body sets enabled=false: " + requests.get(0).body());
    assertTrue(cap.stdout().contains("Plugin 'unhide' disabled"), "success message: " + cap.stdout());
  }

  @Test
  void enable_unknownId_exitsOneSurfacingPluginNotFound() {
    backend.route(
      "/v2/admin/plugins/no-such-plugin",
      404,
      rr -> "{\"type\":\"/problems/plugin.not-found\",\"title\":\"Unknown plugin id\"," +
        "\"status\":404,\"detail\":\"No plugin registered with id 'no-such-plugin'.\"}"
    );

    Captured cap = CliRunner.run(new PluginsEnableCommand(), backend.baseUrl(), "test-key", "no-such-plugin");

    assertNotEquals(0, cap.exit(), "404 → non-zero exit");
    assertEquals(1, cap.exit());
    assertTrue(
      cap.stderr().contains("404") || cap.stderr().toLowerCase().contains("not found"),
      "stderr surfaces 404 message: " + cap.stderr()
    );
  }

  @Test
  void enable_jsonOutput_emitsParseableJson() {
    backend.route(
      "/v2/admin/plugins/unhide",
      200,
      rr -> "{\"id\":\"unhide\",\"version\":\"1.0.0\"," +
        "\"shepardCompatibility\":\">=5.2.0,<6\",\"state\":\"ENABLED\",\"enabled\":true," +
        "\"sourcePath\":null,\"registeredAt\":\"2026-05-13T05:00:00Z\"}"
    );

    Captured cap = CliRunner.run(
      new PluginsEnableCommand(),
      backend.baseUrl(),
      "test-key",
      "unhide",
      "--output=json"
    );

    assertEquals(0, cap.exit(), cap.stderr());
    assertTrue(cap.stdout().contains("\"id\""), "id key on JSON output");
    assertTrue(cap.stdout().contains("\"unhide\""));
  }

  // ─── auth-header propagation ─────────────────────────────────────────────

  @Test
  void allCommands_sendXApiKeyHeader() {
    backend.route(
      "/v2/admin/plugins",
      200,
      rr -> "{\"plugins\":[]}"
    );

    CliRunner.run(new PluginsListCommand(), backend.baseUrl(), "operator-key");

    List<RecordedRequest> requests = backend.requests();
    assertEquals(1, requests.size());
    assertEquals("operator-key", requests.get(0).apiKeyHeader(), "X-API-KEY propagated to backend");
  }

  // ─── parent wiring ───────────────────────────────────────────────────────

  @Test
  void shepardAdminTopLevel_wiresPluginsSubcommand() {
    de.dlr.shepard.cli.ShepardAdmin admin = new de.dlr.shepard.cli.ShepardAdmin();
    picocli.CommandLine cmd = new picocli.CommandLine(admin);
    assertNotNull(cmd.getSubcommands().get("plugins"), "shepard-admin must surface the plugins group");
    picocli.CommandLine sub = cmd.getSubcommands().get("plugins");
    for (String v : List.of("list", "enable", "disable")) {
      assertNotNull(sub.getSubcommands().get(v), "plugins must surface `" + v + "`");
    }
  }
}
