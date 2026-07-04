package de.dlr.shepard.v2.admin.mffd.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.admin.mffd.io.ProcessChainMappingResultIO;
import de.dlr.shepard.v2.admin.mffd.services.MffdProcessChainMappingService.InvalidMappingPayloadException;
import org.junit.jupiter.api.Test;

/**
 * Pure-helper + parsing tests for {@link MffdProcessChainMappingService}.
 *
 * <p>Database-level edge MERGE behaviour is intentionally not covered
 * here — that would require Neo4j testcontainers. The structural
 * surface (YAML shape validation, predicate normalisation,
 * schemaVersion enforcement, transitionKind warnings) is the contract
 * worth pinning at unit-test level.
 *
 * @see MffdProcessChainMappingService
 */
class MffdProcessChainMappingServiceTest {

  @Test
  void yamlKeyToPredicate_explicitKeyMapsToCanonicalIri() {
    assertEquals("urn:shepard:mffd:process-type",
      MffdProcessChainMappingService.yamlKeyToPredicate("process"));
    assertEquals("urn:shepard:mffd:track-number",
      MffdProcessChainMappingService.yamlKeyToPredicate("track_number"));
    assertEquals("urn:shepard:mffd:ply-number",
      MffdProcessChainMappingService.yamlKeyToPredicate("ply_number"));
    assertEquals("urn:shepard:mffd:part-name",
      MffdProcessChainMappingService.yamlKeyToPredicate("part_name"));
  }

  @Test
  void yamlKeyToPredicate_unknownKeyFallsBackToHyphenated() {
    assertEquals("urn:shepard:mffd:foo-bar",
      MffdProcessChainMappingService.yamlKeyToPredicate("foo_bar"));
    assertEquals("urn:shepard:mffd:station-id",
      MffdProcessChainMappingService.yamlKeyToPredicate("station_id"));
  }

  @Test
  void apply_emptyBodyThrowsInvalidPayload() {
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    assertThrows(InvalidMappingPayloadException.class, () -> svc.apply(""));
    assertThrows(InvalidMappingPayloadException.class, () -> svc.apply(null));
    assertThrows(InvalidMappingPayloadException.class, () -> svc.apply("   \n"));
  }

  @Test
  void apply_malformedYamlThrowsInvalidPayload() {
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    // Unbalanced braces — Jackson YAML rejects.
    assertThrows(InvalidMappingPayloadException.class,
      () -> svc.apply("schemaVersion: 1\nmappings:\n  - {unbalanced: yaml: nope"));
  }

  @Test
  void apply_unsupportedSchemaVersionThrowsInvalidPayload() {
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    InvalidMappingPayloadException ex = assertThrows(InvalidMappingPayloadException.class,
      () -> svc.apply("schemaVersion: 99\nmappings: []\n"));
    assertTrue(ex.getMessage().contains("Unsupported schemaVersion"),
      "Error must call out the unsupported version: " + ex.getMessage());
  }

  @Test
  void apply_emptyMappingsReturnsZeroCounters() {
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    ProcessChainMappingResultIO result = svc.apply("schemaVersion: 1\nmappings: []\n");
    assertEquals(1, result.getSchemaVersion());
    assertEquals(0, result.getEntries());
    assertEquals(0, result.getMatched());
    assertEquals(0, result.getEdgesCreated());
    assertTrue(result.getUnresolved().isEmpty());
  }

  @Test
  void apply_mappingsNotAListThrowsInvalidPayload() {
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    assertThrows(InvalidMappingPayloadException.class,
      () -> svc.apply("schemaVersion: 1\nmappings: notalist\n"));
  }

  @Test
  void apply_unknownTransitionKindBecomesWarning() {
    // Neo4j session is unavailable in unit test → all entries unresolve,
    // but the transitionKind warning still fires (it's a structural check).
    String yaml =
      "schemaVersion: 1\n" +
      "mappings:\n" +
      "  - source: {process: a}\n" +
      "    target: {process: b}\n" +
      "    transitionKind: bogus\n";
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    ProcessChainMappingResultIO result = svc.apply(yaml);
    assertFalse(result.getWarnings().isEmpty(),
      "Unknown transitionKind must produce a non-fatal warning");
    assertTrue(result.getWarnings().get(0).contains("bogus"),
      "Warning must mention the rejected value: " + result.getWarnings());
  }

  @Test
  void apply_emptySelectorIsUnresolved() {
    String yaml =
      "schemaVersion: 1\n" +
      "mappings:\n" +
      "  - source: {}\n" +
      "    target: {process: b}\n";
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    ProcessChainMappingResultIO result = svc.apply(yaml);
    assertEquals(1, result.getEntries());
    assertTrue(
      result.getUnresolved().stream().anyMatch(u -> "source".equals(u.getSide())),
      "Empty source selector must surface in the unresolved checklist"
    );
  }

  @Test
  void apply_validTransitionKindsDoNotWarn() {
    String yaml =
      "schemaVersion: 1\n" +
      "mappings:\n" +
      "  - source: {process: a}\n" +
      "    target: {process: b}\n" +
      "    transitionKind: rework\n" +
      "  - source: {process: c}\n" +
      "    target: {process: d}\n" +
      "    transitionKind: concession\n" +
      "  - source: {process: e}\n" +
      "    target: {process: f}\n" +
      "    transitionKind: re-test\n" +
      "  - source: {process: g}\n" +
      "    target: {process: h}\n" +
      "    transitionKind: normal\n";
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    ProcessChainMappingResultIO result = svc.apply(yaml);
    assertEquals(4, result.getEntries());
    assertTrue(result.getWarnings().isEmpty(),
      "Canonical transitionKinds must not produce warnings: " + result.getWarnings());
  }

  @Test
  void apply_omittedTransitionKindDefaultsToNormalNoWarning() {
    // transitionKind absent → defaults to "normal" — no warning.
    String yaml =
      "schemaVersion: 1\n" +
      "mappings:\n" +
      "  - source: {process: a}\n" +
      "    target: {process: b}\n";
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    ProcessChainMappingResultIO result = svc.apply(yaml);
    assertTrue(result.getWarnings().isEmpty(),
      "Omitted transitionKind must not warn (defaults to 'normal'): " + result.getWarnings());
  }

  @Test
  void selectorAnnotations_translatesEveryKeyToPredicateIri() {
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    com.fasterxml.jackson.databind.node.ObjectNode node =
      com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    node.put("process", "afp-layup");
    node.put("track_number", "244");
    node.put("custom_role", "primary");

    var annotations = svc.selectorAnnotations(node);

    assertEquals("afp-layup", annotations.get("urn:shepard:mffd:process-type"));
    assertEquals("244", annotations.get("urn:shepard:mffd:track-number"));
    assertEquals("primary", annotations.get("urn:shepard:mffd:custom-role"));
  }

  @Test
  void selectorAnnotations_nonObjectReturnsEmpty() {
    MffdProcessChainMappingService svc = new MffdProcessChainMappingService();
    assertTrue(svc.selectorAnnotations(null).isEmpty());
    assertTrue(svc.selectorAnnotations(
      com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode()
    ).isEmpty());
  }
}
