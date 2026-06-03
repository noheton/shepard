package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.template.services.TemplateBodyValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * TPL-SEED-DEMO-1 smoke test — guards the bodies seeded by
 * {@code V101__Demo_templates_seed.cypher} against JSON-DSL drift.
 *
 * <p>This test does NOT require Neo4j — it reads the Cypher migration
 * as a classpath resource, extracts each MERGE's {@code body} +
 * {@code templateKind} pair, and asserts the body parses cleanly under
 * {@link TemplateBodyValidator} for the declared kind. Catches the
 * regression "someone tweaked a template body and broke the JSON" /
 * "someone changed the templateKind without updating the body" before
 * it lands on a live Neo4j and gets MERGE'd.
 */
class V101DemoTemplatesSeedTest {

  private static final String MIGRATION_PATH = "neo4j/migrations/V101__Demo_templates_seed.cypher";

  /** Captures every {@code t.templateKind = '...'} value in source order. */
  private static final Pattern KIND_PATTERN = Pattern.compile("t\\d+\\.templateKind\\s*=\\s*'([^']+)'");

  /** Captures every {@code t.body = '...'} value in source order. */
  private static final Pattern BODY_PATTERN = Pattern.compile("t\\d+\\.body\\s+=\\s+'(\\{.*?\\})'\\s*,\\s*\\n");

  /** Captures every {@code t.source = '...'} marker — must be V99-builtin on each row. */
  private static final Pattern SOURCE_PATTERN = Pattern.compile("t\\d+\\.source\\s+=\\s+'([^']+)'");

  private final TemplateBodyValidator validator = new TemplateBodyValidator();

  private String loadMigration() throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(MIGRATION_PATH)) {
      assertNotNull(in, "Migration not on classpath: " + MIGRATION_PATH);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void migrationSeedsExactlySixTemplates() throws IOException {
    String migration = loadMigration();
    List<String> kinds = matchAll(KIND_PATTERN, migration);
    List<String> bodies = matchAll(BODY_PATTERN, migration);
    List<String> sources = matchAll(SOURCE_PATTERN, migration);
    assertEquals(6, kinds.size(), "expected 6 templateKind entries in V99");
    assertEquals(6, bodies.size(), "expected 6 body entries in V99");
    assertEquals(6, sources.size(), "expected 6 source entries in V99");
    for (String s : sources) {
      assertEquals("V99-builtin", s, "every row must carry source='V99-builtin' for rollback");
    }
  }

  @Test
  void everySeededBodyValidatesUnderItsDeclaredKind() throws IOException {
    String migration = loadMigration();
    List<String> kinds = matchAll(KIND_PATTERN, migration);
    List<String> bodies = matchAll(BODY_PATTERN, migration);
    assertEquals(kinds.size(), bodies.size(), "kind/body parse mismatch — regex broken");
    for (int i = 0; i < kinds.size(); i++) {
      String kind = kinds.get(i);
      String body = bodies.get(i);
      List<String> errors = validator.collectErrors(body, kind);
      assertTrue(
        errors.isEmpty(),
        "V99 template #" + (i + 1) + " (" + kind + ") body failed validation: " + errors + " — body was: " + body
      );
    }
  }

  @Test
  void kindsAreOnlyKnownTemplateKinds() throws IOException {
    String migration = loadMigration();
    List<String> kinds = matchAll(KIND_PATTERN, migration);
    for (String kind : kinds) {
      assertTrue(
        "DATAOBJECT_RECIPE".equals(kind) || "COLLECTION_RECIPE".equals(kind),
        "V99 row declared unknown templateKind=" + kind +
        " — V99 covers DATAOBJECT_RECIPE + COLLECTION_RECIPE only"
      );
    }
  }

  @Test
  void rollbackFileExistsAndOnlyDeletesV99BuiltinRows() throws IOException {
    String rollbackPath = "neo4j/migrations/V101_R__Demo_templates_seed.cypher";
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(rollbackPath)) {
      assertNotNull(in, "Rollback twin missing on classpath: " + rollbackPath);
      String rollback = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(rollback.contains("source: 'V99-builtin'"), "rollback must scope DELETE to source='V99-builtin'");
      assertTrue(rollback.contains("DETACH DELETE"), "rollback must DETACH DELETE the matched templates");
      assertFalse(rollback.contains("MATCH (t:ShepardTemplate)\nDETACH"), "rollback must NOT mass-delete every template");
    }
  }

  private List<String> matchAll(Pattern pattern, String input) {
    Matcher m = pattern.matcher(input);
    java.util.ArrayList<String> out = new java.util.ArrayList<>();
    while (m.find()) {
      out.add(m.group(1));
    }
    return out;
  }
}
