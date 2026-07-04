package de.dlr.shepard.plugins.visafpthermo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * MFFD-RENDER-AFP-THERMO-OVERLAY slice 2 — unit tests for
 * {@link AfpThermoOverlayTransformExecutor}.
 *
 * <p>The full {@code materialize} path resolves DataObjects and annotations
 * via request-scoped DAOs through {@code CDI.current()}, which is not available
 * in a plain JUnit context — so the CDI-coupled DAO lookups are exercised by
 * the backend integration tests. Here we cover the pure machinery:
 * shape-IRI claim, body parsing, missing-required-field failure paths,
 * {@link AfpThermoOverlayTransformExecutor#buildAfpRecord AFP annotation extraction},
 * {@link AfpThermoOverlayTransformExecutor#buildNdtRecord NDT annotation extraction},
 * {@link AfpThermoOverlayTransformExecutor#resolveTileMatch tile match},
 * and {@link AfpThermoOverlayTransformExecutor#buildEnvelope envelope construction}.
 */
class AfpThermoOverlayTransformExecutorTest {

  private final AfpThermoOverlayTransformExecutor executor =
      new AfpThermoOverlayTransformExecutor();

  // ─── SPI contract ────────────────────────────────────────────────────────────

  @Test
  void claimsAfpThermoOverlayShapeIri() {
    assertThat(executor.supportedShapeIris())
        .containsExactly(VisAfpThermoOverlayPluginManifest.SHAPE_IRI);
  }

  @Test
  void nameIsStable() {
    assertThat(executor.name()).isEqualTo("AfpThermoOverlayTransformExecutor");
  }

  // ─── materialize guards ───────────────────────────────────────────────────────

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(() -> executor.materialize(null))
        .isInstanceOf(TransformException.class);
  }

  @Test
  void failsWhenAfpDataObjectAppIdMissing() {
    var req = makeRequest("{\"ndtDataObjectAppId\":\"ndt-1\"}");
    assertThatThrownBy(() -> executor.materialize(req))
        .isInstanceOf(TransformException.class)
        .hasMessageContaining("afpDataObjectAppId");
  }

  @Test
  void failsWhenNdtDataObjectAppIdMissing() {
    var req = makeRequest("{\"afpDataObjectAppId\":\"afp-1\"}");
    assertThatThrownBy(() -> executor.materialize(req))
        .isInstanceOf(TransformException.class)
        .hasMessageContaining("ndtDataObjectAppId");
  }

  @Test
  void failsWhenBothInputsMissing() {
    var req = makeRequest("{\"tcpChannel\":\"tcp-temperature\"}");
    assertThatThrownBy(() -> executor.materialize(req))
        .isInstanceOf(TransformException.class);
  }

  // ─── buildAfpRecord tests ─────────────────────────────────────────────────────

  @Test
  void buildAfpRecord_extractsTcpPathRef() {
    var annots = List.of(
        annotation(AfpThermoOverlayTransformExecutor.PRED_AFP_TCP_PATH_REF, "ref-appid-001"));
    var rec = executor.buildAfpRecord("afp-1", annots);
    assertThat(rec.dataObjectAppId()).isEqualTo("afp-1");
    assertThat(rec.tcpPathRefIri()).isEqualTo("ref-appid-001");
  }

  @Test
  void buildAfpRecord_extractsPlyAndCourseId() {
    var annots = List.of(
        annotation(AfpThermoOverlayTransformExecutor.PRED_AFP_PLY_ID, "ply-003"),
        annotation(AfpThermoOverlayTransformExecutor.PRED_AFP_COURSE_ID,
                   "AFP-AFPT-MTLH-S1-003-07"));
    var rec = executor.buildAfpRecord("afp-1", annots);
    assertThat(rec.plyId()).isEqualTo("ply-003");
    assertThat(rec.courseId()).isEqualTo("AFP-AFPT-MTLH-S1-003-07");
  }

  @Test
  void buildAfpRecord_extractsSetpoints() {
    var annots = List.of(
        annotationNumeric(AfpThermoOverlayTransformExecutor.PRED_AFP_LASER_TEMP, 280.0),
        annotationNumeric(AfpThermoOverlayTransformExecutor.PRED_AFP_TAPE_SPEED, 1.5));
    var rec = executor.buildAfpRecord("afp-1", annots);
    assertThat(rec.laserTempSetpointC()).isEqualTo(280.0);
    assertThat(rec.tapeSpeedSetpointMpm()).isEqualTo(1.5);
  }

  @Test
  void buildAfpRecord_extractsSectionAndModule() {
    var annots = List.of(
        annotation(AfpThermoOverlayTransformExecutor.PRED_AFP_SECTION, "S4"),
        annotation(AfpThermoOverlayTransformExecutor.PRED_AFP_MODULE, "M13"));
    var rec = executor.buildAfpRecord("afp-1", annots);
    assertThat(rec.section()).isEqualTo("S4");
    assertThat(rec.module()).isEqualTo("M13");
  }

  @Test
  void buildAfpRecord_extractsMaterialBatch() {
    var annots = List.of(
        annotation(AfpThermoOverlayTransformExecutor.PRED_AFP_MATERIAL_BATCH,
                   "urn:mffd:batch:CFHEX-2024-001"));
    var rec = executor.buildAfpRecord("afp-1", annots);
    assertThat(rec.materialBatchIri()).isEqualTo("urn:mffd:batch:CFHEX-2024-001");
  }

  @Test
  void buildAfpRecord_nullsForMissingAnnotations() {
    var rec = executor.buildAfpRecord("afp-1", List.of());
    assertThat(rec.tcpPathRefIri()).isNull();
    assertThat(rec.plyId()).isNull();
    assertThat(rec.courseId()).isNull();
    assertThat(rec.laserTempSetpointC()).isNull();
    assertThat(rec.tapeSpeedSetpointMpm()).isNull();
    assertThat(rec.materialBatchIri()).isNull();
    assertThat(rec.section()).isNull();
    assertThat(rec.module()).isNull();
  }

  @Test
  void buildAfpRecord_ignoresNullPropertyIri() {
    SemanticAnnotation a = new SemanticAnnotation();
    // propertyIRI stays null
    var rec = executor.buildAfpRecord("afp-1", List.of(a));
    assertThat(rec.plyId()).isNull();
  }

  // ─── buildNdtRecord tests ─────────────────────────────────────────────────────

  @Test
  void buildNdtRecord_extractsSmflCoordinates() {
    var annots = List.of(
        annotation(AfpThermoOverlayTransformExecutor.PRED_NDT_SECTION, "S4"),
        annotation(AfpThermoOverlayTransformExecutor.PRED_NDT_MODULE,  "M13"),
        annotation(AfpThermoOverlayTransformExecutor.PRED_NDT_LAYER,   "L18"),
        annotation(AfpThermoOverlayTransformExecutor.PRED_NDT_FRAME,   "F4"));
    var rec = executor.buildNdtRecord("ndt-1", annots);
    assertThat(rec.dataObjectAppId()).isEqualTo("ndt-1");
    assertThat(rec.section()).isEqualTo("S4");
    assertThat(rec.module()).isEqualTo("M13");
    assertThat(rec.layer()).isEqualTo("L18");
    assertThat(rec.frame()).isEqualTo("F4");
  }

  @Test
  void buildNdtRecord_extractsSourceFileRef() {
    var annots = List.of(
        annotation(AfpThermoOverlayTransformExecutor.PRED_NDT_SOURCE_FILEREF,
                   "fileref-appid-007"));
    var rec = executor.buildNdtRecord("ndt-1", annots);
    assertThat(rec.sourceFileRefIri()).isEqualTo("fileref-appid-007");
  }

  @Test
  void buildNdtRecord_nullsForMissingAnnotations() {
    var rec = executor.buildNdtRecord("ndt-1", List.of());
    assertThat(rec.section()).isNull();
    assertThat(rec.module()).isNull();
    assertThat(rec.layer()).isNull();
    assertThat(rec.frame()).isNull();
    assertThat(rec.sourceFileRefIri()).isNull();
  }

  // ─── resolveTileMatch tests ───────────────────────────────────────────────────

  @Test
  void tileMatch_matched_whenSectionAndModuleAgree() {
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", "S4", "M13", "L18", "F4", null);
    assertThat(executor.resolveTileMatch("S4", "M13", ndt)).isEqualTo("MATCHED");
  }

  @Test
  void tileMatch_mismatched_whenSectionDiffers() {
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", "S5", "M13", null, null, null);
    assertThat(executor.resolveTileMatch("S4", "M13", ndt)).isEqualTo("MISMATCHED");
  }

  @Test
  void tileMatch_mismatched_whenModuleDiffers() {
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", "S4", "M14", null, null, null);
    assertThat(executor.resolveTileMatch("S4", "M13", ndt)).isEqualTo("MISMATCHED");
  }

  @Test
  void tileMatch_unverified_whenEffectiveSectionNull() {
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", "S4", "M13", null, null, null);
    assertThat(executor.resolveTileMatch(null, "M13", ndt)).isEqualTo("UNVERIFIED");
  }

  @Test
  void tileMatch_unverified_whenNdtSectionNull() {
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", null, "M13", null, null, null);
    assertThat(executor.resolveTileMatch("S4", "M13", ndt)).isEqualTo("UNVERIFIED");
  }

  @Test
  void tileMatch_unverified_whenBothEffectiveCoordinatesNull() {
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", "S4", "M13", null, null, null);
    assertThat(executor.resolveTileMatch(null, null, ndt)).isEqualTo("UNVERIFIED");
  }

  // ─── buildEnvelope tests ──────────────────────────────────────────────────────

  @Test
  void buildEnvelope_containsRequiredTopLevelFields() {
    var afp = new AfpThermoOverlayTransformExecutor.AfpRecord(
        "afp-1", null, null, null, null, null, null, null, null);
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", null, null, null, null, null);
    Map<String, Object> env = executor.buildEnvelope(
        afp, ndt, "tcp-temperature", "hot", "side-by-side",
        null, null, null, null, null, null, "UNVERIFIED");

    assertThat(env).containsKey("kind");
    assertThat(env.get("kind")).isEqualTo("afp-thermo-overlay");
    assertThat(env).containsKey("syncMode");
    assertThat(env).containsKey("tcpChannel");
    assertThat(env).containsKey("colourMap");
    assertThat(env).containsKey("tileMatch");
    assertThat(env).containsKey("afp");
    assertThat(env).containsKey("ndt");
  }

  @Test
  void buildEnvelope_afpSubMapContainsDataObjectAppId() {
    var afp = new AfpThermoOverlayTransformExecutor.AfpRecord(
        "afp-99", "tcp-ref-1", "ply-003", "course-07",
        280.0, 1.5, "urn:batch:X", "S4", "M13");
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", "S4", "M13", "L18", "F4", "fr-007");
    Map<String, Object> env = executor.buildEnvelope(
        afp, ndt, "tcp-temperature", "hot", "side-by-side",
        "S4", "M13", 3, 7, null, null, "MATCHED");

    @SuppressWarnings("unchecked")
    Map<String, Object> afpMap = (Map<String, Object>) env.get("afp");
    assertThat(afpMap.get("dataObjectAppId")).isEqualTo("afp-99");
    assertThat(afpMap.get("tcpTimeseriesRefAppId")).isEqualTo("tcp-ref-1");
    assertThat(afpMap.get("plyId")).isEqualTo("ply-003");
    assertThat(afpMap.get("courseId")).isEqualTo("course-07");
    assertThat(afpMap.get("laserTempSetpointC")).isEqualTo(280.0);
    assertThat(afpMap.get("tapeSpeedSetpointMpm")).isEqualTo(1.5);
    assertThat(afpMap.get("materialBatchIri")).isEqualTo("urn:batch:X");
    assertThat(afpMap.get("plyFilter")).isEqualTo(3);
    assertThat(afpMap.get("courseFilter")).isEqualTo(7);
  }

  @Test
  void buildEnvelope_ndtSubMapContainsSmflAndFileRef() {
    var afp = new AfpThermoOverlayTransformExecutor.AfpRecord(
        "afp-1", null, null, null, null, null, null, "S4", "M13");
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-42", "S4", "M13", "L18", "F4", "fr-007");
    Map<String, Object> env = executor.buildEnvelope(
        afp, ndt, "consolidation-force", "plasma", "overlay",
        "S4", "M13", null, null, null, null, "MATCHED");

    @SuppressWarnings("unchecked")
    Map<String, Object> ndtMap = (Map<String, Object>) env.get("ndt");
    assertThat(ndtMap.get("dataObjectAppId")).isEqualTo("ndt-42");
    assertThat(ndtMap.get("section")).isEqualTo("S4");
    assertThat(ndtMap.get("module")).isEqualTo("M13");
    assertThat(ndtMap.get("layer")).isEqualTo("L18");
    assertThat(ndtMap.get("frame")).isEqualTo("F4");
    assertThat(ndtMap.get("sourceFileRefAppId")).isEqualTo("fr-007");
  }

  @Test
  void buildEnvelope_timeWindowIncludedWhenSet() {
    var afp = new AfpThermoOverlayTransformExecutor.AfpRecord(
        "afp-1", null, null, null, null, null, null, null, null);
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", null, null, null, null, null);
    Map<String, Object> env = executor.buildEnvelope(
        afp, ndt, "head-speed", "viridis", "split",
        null, null, null, null, 1718000000000L, 1718000060000L, "UNVERIFIED");

    @SuppressWarnings("unchecked")
    Map<String, Object> afpMap = (Map<String, Object>) env.get("afp");
    assertThat(afpMap.get("timeWindowStartUs")).isEqualTo(1718000000000L);
    assertThat(afpMap.get("timeWindowEndUs")).isEqualTo(1718000060000L);
  }

  @Test
  void buildEnvelope_optionalNullFieldsOmitted() {
    var afp = new AfpThermoOverlayTransformExecutor.AfpRecord(
        "afp-1", null, null, null, null, null, null, null, null);
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", null, null, null, null, null);
    Map<String, Object> env = executor.buildEnvelope(
        afp, ndt, "tcp-temperature", "hot", "side-by-side",
        null, null, null, null, null, null, "UNVERIFIED");

    assertThat(env).doesNotContainKey("section");
    assertThat(env).doesNotContainKey("module");

    @SuppressWarnings("unchecked")
    Map<String, Object> afpMap = (Map<String, Object>) env.get("afp");
    assertThat(afpMap).doesNotContainKey("tcpTimeseriesRefAppId");
    assertThat(afpMap).doesNotContainKey("plyId");
    assertThat(afpMap).doesNotContainKey("courseId");
    assertThat(afpMap).doesNotContainKey("laserTempSetpointC");
    assertThat(afpMap).doesNotContainKey("tapeSpeedSetpointMpm");
    assertThat(afpMap).doesNotContainKey("materialBatchIri");
    assertThat(afpMap).doesNotContainKey("plyFilter");
    assertThat(afpMap).doesNotContainKey("courseFilter");
    assertThat(afpMap).doesNotContainKey("timeWindowStartUs");
    assertThat(afpMap).doesNotContainKey("timeWindowEndUs");

    @SuppressWarnings("unchecked")
    Map<String, Object> ndtMap = (Map<String, Object>) env.get("ndt");
    assertThat(ndtMap).doesNotContainKey("section");
    assertThat(ndtMap).doesNotContainKey("module");
    assertThat(ndtMap).doesNotContainKey("layer");
    assertThat(ndtMap).doesNotContainKey("frame");
    assertThat(ndtMap).doesNotContainKey("sourceFileRefAppId");
  }

  @Test
  void buildEnvelope_tileMatchReflectedInEnvelope() {
    var afp = new AfpThermoOverlayTransformExecutor.AfpRecord(
        "afp-1", null, null, null, null, null, null, "S4", "M13");
    var ndt = new AfpThermoOverlayTransformExecutor.NdtRecord(
        "ndt-1", "S5", "M13", null, null, null);
    Map<String, Object> env = executor.buildEnvelope(
        afp, ndt, "tcp-temperature", "hot", "side-by-side",
        "S4", "M13", null, null, null, null, "MISMATCHED");
    assertThat(env.get("tileMatch")).isEqualTo("MISMATCHED");
  }

  // ─── helper ──────────────────────────────────────────────────────────────────

  private static TransformRequest makeRequest(String bodyJson) {
    return new TransformRequest(
        "tmpl-1",
        VisAfpThermoOverlayPluginManifest.SHAPE_IRI,
        Map.of(),
        "alice",
        bodyJson);
  }

  private static SemanticAnnotation annotation(String predIri, String value) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyIRI(predIri);
    a.setValueName(value);
    return a;
  }

  private static SemanticAnnotation annotationNumeric(String predIri, double value) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyIRI(predIri);
    a.setNumericValue(value);
    return a;
  }
}
