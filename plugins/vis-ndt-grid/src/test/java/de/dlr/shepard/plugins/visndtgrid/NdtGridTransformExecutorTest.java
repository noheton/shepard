package de.dlr.shepard.plugins.visndtgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * MFFD-RENDER-NDT-GRID slice 2 — unit tests for {@link NdtGridTransformExecutor}.
 *
 * <p>The full {@code materialize} path resolves DataObjects and annotations via
 * request-scoped DAOs through {@code CDI.current()}, which is not available in a
 * plain JUnit context — so the CDI-coupled DAO lookups are exercised by the
 * backend integration tests. Here we cover the pure machinery:
 * shape-IRI claim, body parsing, the input-missing failure path,
 * {@link NdtGridTransformExecutor#buildTile tile extraction},
 * {@link NdtGridTransformExecutor#buildNdtGrid envelope construction}, and the
 * {@link NdtGridTransformExecutor#smflNumericPart SMFL numeric sort helper}.
 */
class NdtGridTransformExecutorTest {

  private final NdtGridTransformExecutor executor = new NdtGridTransformExecutor();

  // ─── SPI contract ────────────────────────────────────────────────────────────

  @Test
  void claimsNdtGridShapeIri() {
    assertThat(executor.supportedShapeIris())
      .containsExactly(VisNdtGridPluginManifest.SHAPE_IRI);
  }

  @Test
  void nameIsStable() {
    assertThat(executor.name()).isEqualTo("NdtGridTransformExecutor");
  }

  // ─── materialize guards ───────────────────────────────────────────────────────

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(() -> executor.materialize(null))
      .isInstanceOf(TransformException.class);
  }

  @Test
  void failsWithTypedCodeWhenCollectionAppIdMissing() {
    var req = new TransformRequest(
      "tmpl-1",
      VisNdtGridPluginManifest.SHAPE_IRI,
      Map.of(),
      "alice",
      "{\"colourMode\":\"mean-delta-t\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("collectionAppId");
  }

  // ─── buildTile tests ──────────────────────────────────────────────────────────

  @Test
  void buildTile_extractsSmflCoordinates() {
    List<SemanticAnnotation> annots = List.of(
      annotation(NdtGridTransformExecutor.PRED_SECTION, "S4"),
      annotation(NdtGridTransformExecutor.PRED_MODULE,  "M13"),
      annotation(NdtGridTransformExecutor.PRED_LAYER,   "L18"),
      annotation(NdtGridTransformExecutor.PRED_FRAME,   "F4")
    );
    NdtGridTransformExecutor.TileRecord tile =
      executor.buildTile("do-1", annots, NdtGridTransformExecutor.PRED_MEAN_DT);

    assertThat(tile.dataObjectAppId()).isEqualTo("do-1");
    assertThat(tile.section()).isEqualTo("S4");
    assertThat(tile.module()).isEqualTo("M13");
    assertThat(tile.layer()).isEqualTo("L18");
    assertThat(tile.frame()).isEqualTo("F4");
    assertThat(tile.numericValue()).isNull();
    assertThat(tile.qualityValue()).isNull();
  }

  @Test
  void buildTile_extractsMeanDeltaTNumericValue() {
    SemanticAnnotation meanDt = annotation(NdtGridTransformExecutor.PRED_MEAN_DT, "2.37");
    meanDt.setNumericValue(2.37);
    List<SemanticAnnotation> annots = List.of(
      annotation(NdtGridTransformExecutor.PRED_SECTION, "S4"),
      meanDt
    );
    NdtGridTransformExecutor.TileRecord tile =
      executor.buildTile("do-2", annots, NdtGridTransformExecutor.PRED_MEAN_DT);

    assertThat(tile.numericValue()).isEqualTo(2.37);
    assertThat(tile.qualityValue()).isEqualTo("2.37");
  }

  @Test
  void buildTile_extractsQualityValueInPassFailMode() {
    String qualPred = NdtGridTransformExecutor.DEFAULT_QUALITY_PRED;
    List<SemanticAnnotation> annots = List.of(
      annotation(NdtGridTransformExecutor.PRED_SECTION, "S5"),
      annotation(qualPred, "pass")
    );
    NdtGridTransformExecutor.TileRecord tile = executor.buildTile("do-3", annots, qualPred);

    assertThat(tile.qualityValue()).isEqualTo("pass");
    assertThat(tile.numericValue()).isNull();
  }

  @Test
  void buildTile_returnsNullCoordinatesForEmptyAnnotations() {
    NdtGridTransformExecutor.TileRecord tile =
      executor.buildTile("do-4", List.of(), NdtGridTransformExecutor.PRED_MEAN_DT);

    assertThat(tile.section()).isNull();
    assertThat(tile.module()).isNull();
    assertThat(tile.layer()).isNull();
    assertThat(tile.frame()).isNull();
    assertThat(tile.numericValue()).isNull();
    assertThat(tile.qualityValue()).isNull();
  }

  @Test
  void buildTile_ignoresAnnotationsWithNullPredicate() {
    SemanticAnnotation noIri = new SemanticAnnotation();
    noIri.setValueName("garbage");
    // propertyIRI left null — should be skipped
    NdtGridTransformExecutor.TileRecord tile =
      executor.buildTile("do-5", List.of(noIri), NdtGridTransformExecutor.PRED_MEAN_DT);

    assertThat(tile.section()).isNull();
  }

  // ─── buildNdtGrid tests ───────────────────────────────────────────────────────

  @Test
  void buildNdtGrid_buildsCorrectRowsColumnsAndCells() {
    List<NdtGridTransformExecutor.TileRecord> tiles = List.of(
      new NdtGridTransformExecutor.TileRecord("do-1", "S4", "M13", "L18", "F4", 2.37, null),
      new NdtGridTransformExecutor.TileRecord("do-2", "S4", "M14", "L18", "F4", 1.89, null),
      new NdtGridTransformExecutor.TileRecord("do-3", "S5", "M13", "L18", "F4", 3.10, null)
    );

    Map<String, Object> env = executor.buildNdtGrid(
      "coll-1", "section", "module", "mean-delta-t", "hot",
      NdtGridTransformExecutor.DEFAULT_QUALITY_PRED,
      "L18", null, null, tiles
    );

    assertThat(env.get("kind")).isEqualTo("ndt-grid");
    assertThat(env.get("collectionAppId")).isEqualTo("coll-1");
    assertThat(env.get("rowDimension")).isEqualTo("section");
    assertThat(env.get("columnDimension")).isEqualTo("module");
    assertThat(env.get("colourMode")).isEqualTo("mean-delta-t");
    assertThat(env.get("colourMap")).isEqualTo("hot");
    assertThat(env.get("layerFilter")).isEqualTo("L18");
    assertThat(env).doesNotContainKey("sectionFilter");
    assertThat(env).doesNotContainKey("moduleFilter");

    @SuppressWarnings("unchecked")
    List<String> rows = (List<String>) env.get("rows");
    assertThat(rows).containsExactly("S4", "S5");

    @SuppressWarnings("unchecked")
    List<String> cols = (List<String>) env.get("columns");
    assertThat(cols).containsExactly("M13", "M14");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> cells = (List<Map<String, Object>>) env.get("cells");
    assertThat(cells).hasSize(3);
    assertThat(cells.get(0))
      .containsEntry("row", "S4")
      .containsEntry("col", "M13")
      .containsEntry("value", 2.37)
      .containsEntry("dataObjectAppId", "do-1")
      .containsEntry("status", "RESOLVED");
  }

  @Test
  void buildNdtGrid_emptyTilesProducesEmptyGrid() {
    Map<String, Object> env = executor.buildNdtGrid(
      "coll-1", "layer", "section-module", "mean-delta-t", "hot",
      NdtGridTransformExecutor.DEFAULT_QUALITY_PRED,
      null, null, null, List.of()
    );

    assertThat((List<?>) env.get("rows")).isEmpty();
    assertThat((List<?>) env.get("columns")).isEmpty();
    assertThat((List<?>) env.get("cells")).isEmpty();
  }

  @Test
  void buildNdtGrid_sectionModuleCompositeColumn() {
    List<NdtGridTransformExecutor.TileRecord> tiles = List.of(
      new NdtGridTransformExecutor.TileRecord("do-1", "S4", "M13", "L18", "F4", null, null),
      new NdtGridTransformExecutor.TileRecord("do-2", "S5", "M13", "L18", "F4", null, null)
    );

    Map<String, Object> env = executor.buildNdtGrid(
      "coll-1", "layer", "section-module", "mean-delta-t", "hot",
      NdtGridTransformExecutor.DEFAULT_QUALITY_PRED,
      null, null, null, tiles
    );

    @SuppressWarnings("unchecked")
    List<String> cols = (List<String>) env.get("columns");
    assertThat(cols).containsExactlyInAnyOrder("S4-M13", "S5-M13");
  }

  @Test
  void buildNdtGrid_rowsSortedNumericallyBySmflLabel() {
    // All tiles share the same section (non-null col key) so the sort is exercised on layer.
    List<NdtGridTransformExecutor.TileRecord> tiles = List.of(
      new NdtGridTransformExecutor.TileRecord("do-1", "S4", null, "L10", null, null, null),
      new NdtGridTransformExecutor.TileRecord("do-2", "S4", null, "L2",  null, null, null),
      new NdtGridTransformExecutor.TileRecord("do-3", "S4", null, "L18", null, null, null)
    );

    Map<String, Object> env = executor.buildNdtGrid(
      "coll-1", "layer", "section", "mean-delta-t", "hot",
      NdtGridTransformExecutor.DEFAULT_QUALITY_PRED,
      null, null, null, tiles
    );

    @SuppressWarnings("unchecked")
    List<String> rows = (List<String>) env.get("rows");
    assertThat(rows).containsExactly("L2", "L10", "L18");
  }

  @Test
  void buildNdtGrid_doesNotEchoDefaultQualityPredInEnvelope() {
    Map<String, Object> env = executor.buildNdtGrid(
      "coll-1", "layer", "section-module", "pass-fail", "rdbu",
      NdtGridTransformExecutor.DEFAULT_QUALITY_PRED,
      null, null, null, List.of()
    );

    assertThat(env).doesNotContainKey("qualityAnnotationPredicate");
  }

  @Test
  void buildNdtGrid_echoesCustomQualityPredInEnvelope() {
    String customPred = "urn:custom:ndt-quality";
    Map<String, Object> env = executor.buildNdtGrid(
      "coll-1", "layer", "section-module", "pass-fail", "rdbu",
      customPred, null, null, null, List.of()
    );

    assertThat(env).containsEntry("qualityAnnotationPredicate", customPred);
  }

  @Test
  void buildNdtGrid_qualityCellsHaveQualityField() {
    List<NdtGridTransformExecutor.TileRecord> tiles = List.of(
      new NdtGridTransformExecutor.TileRecord("do-1", "S4", "M13", "L18", "F4", null, "pass"),
      new NdtGridTransformExecutor.TileRecord("do-2", "S5", "M13", "L18", "F4", null, "fail")
    );

    Map<String, Object> env = executor.buildNdtGrid(
      "coll-1", "section", "module", "pass-fail", "rdbu",
      NdtGridTransformExecutor.DEFAULT_QUALITY_PRED,
      null, null, null, tiles
    );

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> cells = (List<Map<String, Object>>) env.get("cells");
    assertThat(cells).hasSize(2);
    assertThat(cells.get(0)).containsEntry("quality", "pass");
    assertThat(cells.get(1)).containsEntry("quality", "fail");
  }

  // ─── smflNumericPart tests ────────────────────────────────────────────────────

  @Test
  void smflNumericPart_parsesSimpleLabels() {
    assertThat(NdtGridTransformExecutor.smflNumericPart("S4")).isEqualTo(4);
    assertThat(NdtGridTransformExecutor.smflNumericPart("M13")).isEqualTo(13);
    assertThat(NdtGridTransformExecutor.smflNumericPart("L18")).isEqualTo(18);
    assertThat(NdtGridTransformExecutor.smflNumericPart("F4")).isEqualTo(4);
  }

  @Test
  void smflNumericPart_stripsTrailingPlus() {
    assertThat(NdtGridTransformExecutor.smflNumericPart("L18+")).isEqualTo(18);
  }

  @Test
  void smflNumericPart_parsesUpToFirstDashForComposites() {
    assertThat(NdtGridTransformExecutor.smflNumericPart("S4-M13")).isEqualTo(4);
    assertThat(NdtGridTransformExecutor.smflNumericPart("S10-M13")).isEqualTo(10);
  }

  @Test
  void smflNumericPart_returnsMaxValueForNullOrEmpty() {
    assertThat(NdtGridTransformExecutor.smflNumericPart(null)).isEqualTo(Integer.MAX_VALUE);
    assertThat(NdtGridTransformExecutor.smflNumericPart("")).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void smflNumericPart_returnsMaxValueForUnparseable() {
    assertThat(NdtGridTransformExecutor.smflNumericPart("UNKNOWN")).isEqualTo(Integer.MAX_VALUE);
  }

  // ─── Helper ──────────────────────────────────────────────────────────────────

  private static SemanticAnnotation annotation(String predicateIri, String valueName) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyIRI(predicateIri);
    a.setValueName(valueName);
    return a;
  }
}
