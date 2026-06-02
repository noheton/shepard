package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.template.services.TemplateBodyValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * QM1c smoke test — guards the body seeded by
 * {@code V102__Disposition_record_template.cypher} against drift.
 *
 * <p>Verifies:
 * <ul>
 *   <li>The migration is on the classpath and parseable.</li>
 *   <li>It seeds exactly one ShepardTemplate.</li>
 *   <li>The templateKind is {@code STRUCTURED_RECIPE}.</li>
 *   <li>The body validates under {@link TemplateBodyValidator}.</li>
 *   <li>The body's structured record carries the EN 9100 §8.7 disposition
 *       fields: ncr_id, defect_type, disposition (enum), approver_orcid,
 *       approver_username, decided_at, notes.</li>
 *   <li>The disposition enum lists {@code use-as-is / rework / scrap / concession}.</li>
 *   <li>Both the migration and its rollback twin carry the
 *       {@code V102-builtin} source marker.</li>
 * </ul>
 */
class V102DispositionRecordTemplateTest {

  private static final String MIGRATION_PATH = "neo4j/migrations/V102__Disposition_record_template.cypher";
  private static final String ROLLBACK_PATH = "neo4j/migrations/V102_R__Disposition_record_template.cypher";

  private static final Pattern KIND_PATTERN = Pattern.compile("t\\.templateKind\\s*=\\s*'([^']+)'");
  private static final Pattern BODY_PATTERN = Pattern.compile("t\\.body\\s+=\\s+'(\\{.*?\\})'\\s*,");
  private static final Pattern SOURCE_PATTERN = Pattern.compile("t\\.source\\s+=\\s+'([^']+)'");

  private final TemplateBodyValidator validator = new TemplateBodyValidator();
  private final ObjectMapper mapper = new ObjectMapper();

  private String load(String path) throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
      assertNotNull(in, "Migration not on classpath: " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void migrationSeedsExactlyOneTemplate() throws IOException {
    String migration = load(MIGRATION_PATH);
    assertEquals(1, matchAll(KIND_PATTERN, migration).size(),
      "QM1c V102 must seed exactly one ShepardTemplate");
    assertEquals(1, matchAll(BODY_PATTERN, migration).size());
  }

  @Test
  void templateKindIsStructuredRecipe() throws IOException {
    String migration = load(MIGRATION_PATH);
    List<String> kinds = matchAll(KIND_PATTERN, migration);
    assertEquals(1, kinds.size());
    assertEquals("STRUCTURED_RECIPE", kinds.get(0),
      "QM1c template must be STRUCTURED_RECIPE");
  }

  @Test
  void bodyValidatesUnderStructuredRecipe() throws IOException {
    String migration = load(MIGRATION_PATH);
    List<String> bodies = matchAll(BODY_PATTERN, migration);
    assertEquals(1, bodies.size());
    List<String> errors = validator.collectErrors(bodies.get(0), "STRUCTURED_RECIPE");
    assertTrue(errors.isEmpty(),
      "V102 body failed STRUCTURED_RECIPE validation: " + errors +
      " — body was: " + bodies.get(0));
  }

  @Test
  void bodyCarriesEn9100Fields() throws IOException {
    String migration = load(MIGRATION_PATH);
    List<String> bodies = matchAll(BODY_PATTERN, migration);
    JsonNode root = mapper.readTree(bodies.get(0));
    JsonNode fields = root.path("structuredData").path("fields");
    assertTrue(fields.isArray() && fields.size() >= 7,
      "structured-data record must declare at least the 7 EN 9100 §8.7 fields");

    // Index by name for easy assertion.
    java.util.Set<String> names = new java.util.HashSet<>();
    for (JsonNode f : fields) names.add(f.path("name").asText());

    for (String required : List.of(
      "ncr_id", "defect_type", "disposition",
      "approver_orcid", "approver_username",
      "decided_at", "notes"
    )) {
      assertTrue(names.contains(required),
        "QM1c body missing required field: " + required + " — fields were: " + names);
    }
  }

  @Test
  void dispositionEnumCoversEn9100Vocabulary() throws IOException {
    String migration = load(MIGRATION_PATH);
    List<String> bodies = matchAll(BODY_PATTERN, migration);
    JsonNode root = mapper.readTree(bodies.get(0));
    JsonNode fields = root.path("structuredData").path("fields");

    JsonNode dispositionField = null;
    for (JsonNode f : fields) {
      if ("disposition".equals(f.path("name").asText())) {
        dispositionField = f;
        break;
      }
    }
    assertNotNull(dispositionField, "QM1c body must include a 'disposition' field");
    JsonNode enumValues = dispositionField.path("enum");
    assertTrue(enumValues.isArray(), "disposition.enum must be a JSON array");
    java.util.Set<String> values = new java.util.HashSet<>();
    enumValues.forEach(n -> values.add(n.asText()));
    for (String v : List.of("use-as-is", "rework", "scrap", "concession")) {
      assertTrue(values.contains(v),
        "EN 9100 disposition enum missing value: " + v + " — found: " + values);
    }
  }

  @Test
  void migrationAndRollbackCarryV102BuiltinSource() throws IOException {
    String migration = load(MIGRATION_PATH);
    List<String> sources = matchAll(SOURCE_PATTERN, migration);
    assertEquals(1, sources.size());
    assertEquals("V102-builtin", sources.get(0),
      "V102 row must carry source='V102-builtin' for scoped rollback");

    String rollback = load(ROLLBACK_PATH);
    assertTrue(rollback.contains("V102-builtin"),
      "Rollback must scope DELETE to source='V102-builtin'");
    assertTrue(rollback.contains("DETACH DELETE"),
      "Rollback must DETACH DELETE");
  }

  private List<String> matchAll(Pattern pattern, String input) {
    Matcher m = pattern.matcher(input);
    java.util.ArrayList<String> out = new java.util.ArrayList<>();
    while (m.find()) out.add(m.group(1));
    return out;
  }
}
