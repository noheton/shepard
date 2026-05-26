package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.daos.VersionableEntityConcreteDAO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * TPL4 — unit tests for {@link AttributeAnnotationDualWriteService}.
 *
 * <p>Covers four required scenarios (per CLAUDE.md "always write tests"):
 * <ol>
 *   <li>Toggle off → no annotation created.</li>
 *   <li>Toggle on, first save with attributes → correct annotation nodes created.</li>
 *   <li>Toggle on, second save with same attributes → no duplicates (stale delete + re-create).</li>
 *   <li>Toggle on, update path with changed/removed attributes → stale annotations removed and new set written.</li>
 * </ol>
 */
@QuarkusComponentTest
@TestConfigProperty(key = "shepard.tpl4.dual-write.enabled", value = "true")
public class AttributeAnnotationDualWriteServiceTest {

  @InjectMock
  SemanticAnnotationDAO semanticAnnotationDAO;

  @InjectMock
  VersionableEntityConcreteDAO versionableEntityConcreteDAO;

  @Inject
  AttributeAnnotationDualWriteService service;

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private DataObject dataObjectWithAttrs(Map<String, String> attrs) {
    DataObject do1 = new DataObject(42L);
    do1.setShepardId(99L);
    do1.setAttributes(attrs);
    return do1;
  }

  private DataObject dataObjectWithAttrsAndAppId(Map<String, String> attrs, String appId) {
    DataObject do1 = dataObjectWithAttrs(attrs);
    do1.setAppId(appId);
    return do1;
  }

  private SemanticAnnotation savedAnnotation(String propIri, String valName, String source) {
    SemanticAnnotation sa = new SemanticAnnotation();
    sa.setId(1L);
    sa.setPropertyIRI(propIri);
    sa.setPropertyName(propIri.substring(AttributeAnnotationDualWriteService.PREDICATE_PREFIX.length()));
    sa.setValueName(valName);
    sa.setSource(source);
    return sa;
  }

  // ---------------------------------------------------------------------------
  // Test 1: toggle off — no annotation written
  // ---------------------------------------------------------------------------

  @Test
  @TestConfigProperty(key = "shepard.tpl4.dual-write.enabled", value = "false")
  public void toggleOff_noAnnotationsCreated() {
    DataObject do1 = dataObjectWithAttrs(Map.of("bench", "TB-01"));
    // entity would be found, but the toggle should short-circuit before any DAO call
    service.backfillFromAttributes(do1);
    verify(semanticAnnotationDAO, never()).createOrUpdate(any());
    verify(semanticAnnotationDAO, never()).findAllSemanticAnnotationsByNeo4jId(any(Long.class));
  }

  // ---------------------------------------------------------------------------
  // Test 2: toggle on, first save — annotation created with correct field values
  // ---------------------------------------------------------------------------

  @Test
  public void toggleOn_firstSave_annotationsCreated() {
    DataObject do1 = dataObjectWithAttrs(Map.of("bench", "TB-01", "propellant", "LOX/LH2"));

    // No existing backfill annotations.
    when(semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(42L)).thenReturn(List.of());
    when(versionableEntityConcreteDAO.findByNeo4jId(42L)).thenReturn(do1);

    // DAO returns an annotation with an id (simulates Neo4j write).
    when(semanticAnnotationDAO.createOrUpdate(any())).thenAnswer(inv -> {
      SemanticAnnotation in = inv.getArgument(0);
      SemanticAnnotation out = new SemanticAnnotation();
      out.setId(100L);
      out.setPropertyIRI(in.getPropertyIRI());
      out.setPropertyName(in.getPropertyName());
      out.setValueName(in.getValueName());
      out.setSource(in.getSource());
      return out;
    });

    service.backfillFromAttributes(do1);

    // Two attributes → two createOrUpdate calls.
    ArgumentCaptor<SemanticAnnotation> captor = ArgumentCaptor.forClass(SemanticAnnotation.class);
    verify(semanticAnnotationDAO, times(2)).createOrUpdate(captor.capture());

    List<SemanticAnnotation> written = captor.getAllValues();
    // Verify both carry the correct source and synthetic predicate format.
    for (SemanticAnnotation sa : written) {
      assertNotNull(sa.getPropertyIRI());
      assertNotNull(sa.getPropertyName());
      assertNotNull(sa.getValueName());
      assertEquals(AttributeAnnotationDualWriteService.BACKFILL_SOURCE, sa.getSource());
      assertEquals(
        Constants.ANNOTATION_SOURCE_ATTRIBUTES_BACKFILL,
        sa.getSource(),
        "source constant must match service constant"
      );
      assert sa.getPropertyIRI().startsWith(Constants.TPL4_ATTRIBUTE_PREDICATE_PREFIX) :
        "propertyIRI must start with " + Constants.TPL4_ATTRIBUTE_PREDICATE_PREFIX;
    }

    // Verify specific key→IRI mapping.
    boolean hasBench = written.stream()
      .anyMatch(
        a ->
          a.getPropertyIRI().equals(Constants.TPL4_ATTRIBUTE_PREDICATE_PREFIX + "bench") &&
          "TB-01".equals(a.getValueName())
      );
    boolean hasPropellant = written.stream()
      .anyMatch(
        a ->
          a.getPropertyIRI().equals(Constants.TPL4_ATTRIBUTE_PREDICATE_PREFIX + "propellant") &&
          "LOX/LH2".equals(a.getValueName())
      );
    assert hasBench : "Missing annotation for 'bench'";
    assert hasPropellant : "Missing annotation for 'propellant'";
  }

  // ---------------------------------------------------------------------------
  // Test 3: toggle on, second save with same attributes → stale deleted, re-created (no net duplicate)
  // ---------------------------------------------------------------------------

  @Test
  public void toggleOn_secondSave_staleDeletedThenRecreated() {
    DataObject do1 = dataObjectWithAttrs(Map.of("bench", "TB-01"));

    // Simulate an existing backfill annotation for 'bench' from a previous run.
    SemanticAnnotation stale = savedAnnotation(
      Constants.TPL4_ATTRIBUTE_PREDICATE_PREFIX + "bench",
      "TB-01",
      AttributeAnnotationDualWriteService.BACKFILL_SOURCE
    );
    when(semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(42L)).thenReturn(List.of(stale));
    when(versionableEntityConcreteDAO.findByNeo4jId(42L)).thenReturn(do1);
    when(semanticAnnotationDAO.createOrUpdate(any())).thenAnswer(inv -> {
      SemanticAnnotation in = inv.getArgument(0);
      SemanticAnnotation out = new SemanticAnnotation();
      out.setId(101L);
      out.setPropertyIRI(in.getPropertyIRI());
      out.setPropertyName(in.getPropertyName());
      out.setValueName(in.getValueName());
      out.setSource(in.getSource());
      return out;
    });

    service.backfillFromAttributes(do1);

    // Stale annotation must be deleted.
    verify(semanticAnnotationDAO, times(1)).deleteByNeo4jId(stale.getId());
    // Then one new annotation created.
    verify(semanticAnnotationDAO, times(1)).createOrUpdate(any());
  }

  // ---------------------------------------------------------------------------
  // Test 4: toggle on, update path — old backfill removed, new set written
  // ---------------------------------------------------------------------------

  @Test
  public void toggleOn_updatePath_oldAnnotationsReplacedWithNewSet() {
    // Before update: had 'bench=TB-01', after update: now has 'bench=TB-02' + 'phase=STATIC'
    DataObject updated = dataObjectWithAttrs(Map.of("bench", "TB-02", "phase", "STATIC"));

    SemanticAnnotation oldBench = savedAnnotation(
      Constants.TPL4_ATTRIBUTE_PREDICATE_PREFIX + "bench",
      "TB-01",
      AttributeAnnotationDualWriteService.BACKFILL_SOURCE
    );
    when(semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(42L)).thenReturn(List.of(oldBench));
    when(versionableEntityConcreteDAO.findByNeo4jId(42L)).thenReturn(updated);
    when(semanticAnnotationDAO.createOrUpdate(any())).thenAnswer(inv -> {
      SemanticAnnotation in = inv.getArgument(0);
      SemanticAnnotation out = new SemanticAnnotation();
      out.setId(102L);
      out.setPropertyIRI(in.getPropertyIRI());
      out.setPropertyName(in.getPropertyName());
      out.setValueName(in.getValueName());
      out.setSource(in.getSource());
      return out;
    });

    service.backfillFromAttributes(updated);

    // Old annotation deleted.
    verify(semanticAnnotationDAO, times(1)).deleteByNeo4jId(oldBench.getId());
    // Two new annotations created (bench=TB-02, phase=STATIC).
    verify(semanticAnnotationDAO, times(2)).createOrUpdate(any());
  }

  // ---------------------------------------------------------------------------
  // Test 5: null or empty attributes — no annotation written
  // ---------------------------------------------------------------------------

  @Test
  public void toggleOn_nullAttributes_noAnnotationCreated() {
    DataObject do1 = new DataObject(42L);
    do1.setShepardId(99L);
    do1.setAttributes(null);

    service.backfillFromAttributes(do1);

    verify(semanticAnnotationDAO, never()).createOrUpdate(any());
    verify(semanticAnnotationDAO, never()).findAllSemanticAnnotationsByNeo4jId(any(Long.class));
  }

  @Test
  public void toggleOn_emptyAttributes_noAnnotationCreated() {
    DataObject do1 = dataObjectWithAttrs(Map.of());

    service.backfillFromAttributes(do1);

    verify(semanticAnnotationDAO, never()).createOrUpdate(any());
    verify(semanticAnnotationDAO, never()).findAllSemanticAnnotationsByNeo4jId(any(Long.class));
  }

  // ---------------------------------------------------------------------------
  // SEMA-V6-011 Test 6: subjectKind, subjectAppId, sourceMode populated correctly
  // ---------------------------------------------------------------------------

  @Test
  public void semaV6011_v6FieldsPopulatedOnDualWrite() {
    String testAppId = "01900000-0000-7000-8000-000000000042";
    DataObject do1 = dataObjectWithAttrsAndAppId(Map.of("propellant", "LOX/LH2"), testAppId);

    when(semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(42L)).thenReturn(List.of());
    when(versionableEntityConcreteDAO.findByNeo4jId(42L)).thenReturn(do1);
    when(semanticAnnotationDAO.createOrUpdate(any())).thenAnswer(inv -> {
      SemanticAnnotation in = inv.getArgument(0);
      SemanticAnnotation out = new SemanticAnnotation();
      out.setId(200L);
      out.setPropertyIRI(in.getPropertyIRI());
      out.setPropertyName(in.getPropertyName());
      out.setValueName(in.getValueName());
      out.setSource(in.getSource());
      out.setSubjectKind(in.getSubjectKind());
      out.setSubjectAppId(in.getSubjectAppId());
      out.setSourceMode(in.getSourceMode());
      out.setVocabularyId(in.getVocabularyId());
      out.setSourceActivityAppId(in.getSourceActivityAppId());
      out.setConfidence(in.getConfidence());
      return out;
    });

    service.backfillFromAttributes(do1);

    ArgumentCaptor<SemanticAnnotation> captor = ArgumentCaptor.forClass(SemanticAnnotation.class);
    verify(semanticAnnotationDAO, times(1)).createOrUpdate(captor.capture());

    SemanticAnnotation written = captor.getValue();
    assertEquals("DataObject", written.getSubjectKind(), "subjectKind must be 'DataObject'");
    assertEquals(testAppId, written.getSubjectAppId(), "subjectAppId must match the DataObject's appId");
    assertEquals("human", written.getSourceMode(), "sourceMode must be 'human' for legacy attributes");
    assertNull(written.getVocabularyId(), "vocabularyId must be null — no controlled vocabulary");
    assertNull(written.getSourceActivityAppId(), "sourceActivityAppId must be null");
    assertNull(written.getConfidence(), "confidence must be null");
  }

  // ---------------------------------------------------------------------------
  // SEMA-V6-011 Test 7: import-marker keys are NOT dual-written
  // ---------------------------------------------------------------------------

  @Test
  public void semaV6011_importMarkerKeysNotDualWritten() {
    // Mix of import-marker keys (should be filtered) and domain keys (should be written)
    DataObject do1 = dataObjectWithAttrs(Map.of(
      "v16_pass1", "done",
      "source_file", "/data/run004.h5",
      "import_ready", "true",
      "_import_batch", "batch-42",
      "v15_migrate_flag", "1",
      "propellant", "LOX/LH2",    // legitimate domain key — should be written
      "test_engineer", "J.Smith"  // legitimate domain key — should be written
    ));

    when(semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(42L)).thenReturn(List.of());
    when(versionableEntityConcreteDAO.findByNeo4jId(42L)).thenReturn(do1);
    when(semanticAnnotationDAO.createOrUpdate(any())).thenAnswer(inv -> {
      SemanticAnnotation in = inv.getArgument(0);
      SemanticAnnotation out = new SemanticAnnotation();
      out.setId(201L);
      out.setPropertyIRI(in.getPropertyIRI());
      out.setPropertyName(in.getPropertyName());
      out.setValueName(in.getValueName());
      out.setSource(in.getSource());
      return out;
    });

    service.backfillFromAttributes(do1);

    // Only the 2 domain keys should have been written; 5 import-marker keys filtered.
    ArgumentCaptor<SemanticAnnotation> captor = ArgumentCaptor.forClass(SemanticAnnotation.class);
    verify(semanticAnnotationDAO, times(2)).createOrUpdate(captor.capture());

    List<String> writtenKeys = captor.getAllValues().stream()
      .map(SemanticAnnotation::getPropertyName)
      .toList();
    assertTrue(writtenKeys.contains("propellant"), "propellant must be written");
    assertTrue(writtenKeys.contains("test_engineer"), "test_engineer must be written");
    assertFalse(writtenKeys.contains("v16_pass1"), "v16_pass1 must be filtered");
    assertFalse(writtenKeys.contains("source_file"), "source_file must be filtered");
    assertFalse(writtenKeys.contains("import_ready"), "import_ready must be filtered");
    assertFalse(writtenKeys.contains("_import_batch"), "_import_batch must be filtered");
    assertFalse(writtenKeys.contains("v15_migrate_flag"), "v15_migrate_flag must be filtered");
  }

  // ---------------------------------------------------------------------------
  // SEMA-V6-011 Test 8: isImportMarkerKey() unit test — all filter patterns
  // ---------------------------------------------------------------------------

  @Test
  public void semaV6011_isImportMarkerKey_filterPatterns() {
    // Keys that MUST be filtered
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("source_file"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("source_collection"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("source_"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("v15_anything"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("v16_pass1"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("v16_pass2"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("v16_custom"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("import_ready"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("import_batch"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("_import_batch"));
    assertTrue(AttributeAnnotationDualWriteService.isImportMarkerKey("_import_anything"));

    // Keys that must NOT be filtered (legitimate domain keys)
    assertFalse(AttributeAnnotationDualWriteService.isImportMarkerKey("bench"));
    assertFalse(AttributeAnnotationDualWriteService.isImportMarkerKey("propellant"));
    assertFalse(AttributeAnnotationDualWriteService.isImportMarkerKey("test_engineer"));
    assertFalse(AttributeAnnotationDualWriteService.isImportMarkerKey("phase"));
    assertFalse(AttributeAnnotationDualWriteService.isImportMarkerKey("description"));
    assertFalse(AttributeAnnotationDualWriteService.isImportMarkerKey("operator_id"));
    assertFalse(AttributeAnnotationDualWriteService.isImportMarkerKey(null));
  }
}
