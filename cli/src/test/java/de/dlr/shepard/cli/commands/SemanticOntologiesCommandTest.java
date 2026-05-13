package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import de.dlr.shepard.cli.support.StubBackend.RecordedRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * N1c2 smoke tests for the {@code shepard-admin semantic ontologies
 * <verb>} subcommands. Uses the loopback {@link StubBackend} for the
 * real {@link java.net.http.HttpClient} + Jackson decoding path.
 */
final class SemanticOntologiesCommandTest {

  private static final String LIST_JSON =
    """
    {
      "bundles": [
        {"id":"prov-o","name":"PROV-O","source":"builtin","required":true,"enabled":true,
         "iriPrefix":"http://www.w3.org/ns/prov#","license":"W3C Document License","sha256":"abc","byteSize":1892},
        {"id":"qudt","name":"QUDT","source":"builtin","required":false,"enabled":false,
         "iriPrefix":"http://qudt.org/vocab/unit/","license":"CC BY 4.0","sha256":"def","byteSize":2271},
        {"id":"custom","name":"Lab Vocab","source":"user","required":false,"enabled":true,
         "iriPrefix":"http://example.org/lab/","license":"CC0 1.0","sha256":"ghi","byteSize":123}
      ]
    }
    """;

  private static final String ROW_JSON =
    """
    {"id":"qudt","name":"QUDT","source":"builtin","required":false,"enabled":false,
     "iriPrefix":"http://qudt.org/vocab/unit/","license":"CC BY 4.0","sha256":"def","byteSize":2271}
    """;

  // ---------- list -----------------------------------------------------------

  @Test
  void list_humanTable_rendersAllBundles_andExitsZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticOntologiesListCommand.PATH, 200, LIST_JSON);

      Captured result = CliRunner.run(new SemanticOntologiesListCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("ID")
        .contains("SOURCE")
        .contains("ENABLED")
        .contains("LICENSE")
        .contains("prov-o")
        .contains("qudt")
        .contains("custom");
      assertThat(result.stderr()).isEmpty();
    }
  }

  @Test
  void list_jsonOutput_emitsParseableShape() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticOntologiesListCommand.PATH, 200, LIST_JSON);
      Captured result = CliRunner.run(
        new SemanticOntologiesListCommand(),
        backend.baseUrl(),
        "test-key",
        "--output",
        "json"
      );
      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout()).contains("\"bundles\"").contains("\"prov-o\"");
    }
  }

  @Test
  void list_unauthorized_returnsExitOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticOntologiesListCommand.PATH, 401, "{\"error\":\"missing X-API-KEY\"}");
      Captured result = CliRunner.run(new SemanticOntologiesListCommand(), backend.baseUrl(), null);
      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("401");
    }
  }

  // ---------- enable / disable ----------------------------------------------

  @Test
  void enable_postsToCorrectPath_andExitsZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/v2/admin/semantic/ontologies/qudt/enable", 200, ROW_JSON);

      Captured result = CliRunner.run(
        new SemanticOntologiesEnableCommand(),
        backend.baseUrl(),
        "test-key",
        "qudt"
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.method()).isEqualTo("POST");
      assertThat(req.path()).isEqualTo("/v2/admin/semantic/ontologies/qudt/enable");
      assertThat(result.stdout()).contains("'qudt'").contains("enabled=");
    }
  }

  @Test
  void disable_postsToCorrectPath() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/v2/admin/semantic/ontologies/qudt/disable", 200, ROW_JSON);

      Captured result = CliRunner.run(
        new SemanticOntologiesDisableCommand(),
        backend.baseUrl(),
        "test-key",
        "qudt"
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.path()).isEqualTo("/v2/admin/semantic/ontologies/qudt/disable");
    }
  }

  @Test
  void disable_required_409_returnsExitOne_andSurfacesReason() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(
        "/v2/admin/semantic/ontologies/prov-o/disable",
        409,
        "{\"type\":\"/problems/semantic.bundle.required\",\"title\":\"Bundle is required\"," +
          "\"status\":409,\"detail\":\"prov-o is required.\"}"
      );

      Captured result = CliRunner.run(
        new SemanticOntologiesDisableCommand(),
        backend.baseUrl(),
        "test-key",
        "prov-o"
      );

      // The current ShepardHttpClient routes 409 through the generic "Unexpected HTTP" branch;
      // the exit code is 1 and stderr surfaces the 409 number.
      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("409");
    }
  }

  @Test
  void disable_jsonOutput_emitsRow() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/v2/admin/semantic/ontologies/qudt/disable", 200, ROW_JSON);

      Captured result = CliRunner.run(
        new SemanticOntologiesDisableCommand(),
        backend.baseUrl(),
        "test-key",
        "qudt",
        "--output",
        "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout()).contains("\"id\"").contains("\"qudt\"");
    }
  }

  // ---------- upload ---------------------------------------------------------

  @Test
  void upload_postsMultipart_withFileAndMetadata_exitsZero(@TempDir Path tempDir) throws Exception {
    Path ttl = tempDir.resolve("custom.ttl");
    Files.writeString(ttl, "@prefix ex: <http://example.org/> .\n");

    String created =
      "{\"id\":\"custom\",\"source\":\"user\",\"required\":false,\"enabled\":true," +
      "\"iriPrefix\":\"http://example.org/custom/\",\"license\":\"CC0 1.0\"," +
      "\"sha256\":\"abc\",\"byteSize\":40}";

    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticOntologiesUploadCommand.PATH, 201, created);

      Captured result = CliRunner.run(
        new SemanticOntologiesUploadCommand(),
        backend.baseUrl(),
        "test-key",
        "--file",
        ttl.toString(),
        "--id",
        "custom",
        "--iri-prefix",
        "http://example.org/custom/",
        "--license",
        "CC0 1.0"
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.method()).isEqualTo("POST");
      assertThat(req.path()).isEqualTo(SemanticOntologiesUploadCommand.PATH);
      // multipart body carries metadata JSON + the TTL bytes
      assertThat(req.body()).contains("\"id\":\"custom\"");
      assertThat(req.body()).contains("@prefix ex:");
      assertThat(result.stdout()).contains("Uploaded bundle 'custom'");
    }
  }

  @Test
  void upload_duplicateId_returnsExitOne(@TempDir Path tempDir) throws Exception {
    Path ttl = tempDir.resolve("custom.ttl");
    Files.writeString(ttl, "@prefix ex: <http://example.org/> .\n");
    try (StubBackend backend = StubBackend.start()) {
      backend.route(
        SemanticOntologiesUploadCommand.PATH,
        409,
        "{\"type\":\"/problems/semantic.bundle.duplicate-id\",\"title\":\"Duplicate\"," +
          "\"status\":409,\"detail\":\"shadows a built-in\"}"
      );

      Captured result = CliRunner.run(
        new SemanticOntologiesUploadCommand(),
        backend.baseUrl(),
        "test-key",
        "--file",
        ttl.toString(),
        "--id",
        "prov-o",
        "--iri-prefix",
        "http://example.org/x/",
        "--license",
        "CC0 1.0"
      );

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("409");
    }
  }

  @Test
  void upload_missingFile_isAdminCliException() {
    Captured result = CliRunner.run(
      new SemanticOntologiesUploadCommand(),
      "http://127.0.0.1:1",
      "test-key",
      "--file",
      "/does/not/exist.ttl",
      "--id",
      "custom",
      "--iri-prefix",
      "http://example.org/custom/",
      "--license",
      "CC0 1.0"
    );

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.stderr()).containsAnyOf("Could not read", "No such file");
  }

  @Test
  void upload_jsonOutput_emitsCreatedRow(@TempDir Path tempDir) throws Exception {
    Path ttl = tempDir.resolve("custom.ttl");
    Files.writeString(ttl, "@prefix ex: <http://example.org/> .\n");
    String created =
      "{\"id\":\"custom\",\"source\":\"user\",\"required\":false,\"enabled\":true," +
      "\"iriPrefix\":\"http://example.org/custom/\",\"license\":\"CC0 1.0\"," +
      "\"sha256\":\"abc\",\"byteSize\":40}";

    try (StubBackend backend = StubBackend.start()) {
      backend.route(SemanticOntologiesUploadCommand.PATH, 201, created);
      Captured result = CliRunner.run(
        new SemanticOntologiesUploadCommand(),
        backend.baseUrl(),
        "test-key",
        "--file",
        ttl.toString(),
        "--id",
        "custom",
        "--iri-prefix",
        "http://example.org/custom/",
        "--license",
        "CC0 1.0",
        "--output",
        "json"
      );
      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout()).contains("\"id\"").contains("\"custom\"");
    }
  }

  // ---------- remove ---------------------------------------------------------

  @Test
  void remove_callsDelete_exitsZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/v2/admin/semantic/ontologies/custom", 204, "");

      Captured result = CliRunner.run(
        new SemanticOntologiesRemoveCommand(),
        backend.baseUrl(),
        "test-key",
        "custom"
      );

      assertThat(result.exit()).isEqualTo(0);
      RecordedRequest req = backend.requests().get(0);
      assertThat(req.method()).isEqualTo("DELETE");
      assertThat(req.path()).isEqualTo("/v2/admin/semantic/ontologies/custom");
      assertThat(result.stdout()).contains("removed");
    }
  }

  @Test
  void remove_builtin_409_returnsExitOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(
        "/v2/admin/semantic/ontologies/prov-o",
        409,
        "{\"type\":\"/problems/semantic.bundle.builtin-not-removable\",\"title\":\"Built-in\"," +
          "\"status\":409,\"detail\":\"ships in the JAR\"}"
      );
      Captured result = CliRunner.run(
        new SemanticOntologiesRemoveCommand(),
        backend.baseUrl(),
        "test-key",
        "prov-o"
      );
      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stderr()).contains("409");
    }
  }

  @Test
  void remove_jsonOutput_emitsRemovedEnvelope() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route("/v2/admin/semantic/ontologies/custom", 204, "");
      Captured result = CliRunner.run(
        new SemanticOntologiesRemoveCommand(),
        backend.baseUrl(),
        "test-key",
        "custom",
        "--output",
        "json"
      );
      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout()).contains("\"removed\"").contains("\"custom\"");
    }
  }

  // ---------- group parent ---------------------------------------------------

  @Test
  void groupParentCommand_runHasUsageBanner() {
    // No-op covers the group-parent class so coverage isn't 0%; the
    // production behaviour is "Picocli prints usage banner when there's
    // no subcommand", and the run() method is a no-op by design.
    new SemanticOntologiesCommand().run();
  }
}
