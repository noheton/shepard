package de.dlr.shepard.plugins.visndtgrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import jakarta.enterprise.inject.spi.CDI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jboss.logging.Logger;

/**
 * MFFD-RENDER-NDT-GRID slice 2 — the {@link TransformExecutor} that builds the
 * NDT thermography tile-grid mosaic VIEW envelope.
 *
 * <p>Claims the {@code NdtGridShape} IRI. A MAPPING_RECIPE template targeting
 * that shape binds a Shepard Collection (by {@code collectionAppId} in the
 * template body) of {@code mffd:ndt-otvis-measurement} DataObjects. Materialising
 * the template:
 * <ol>
 *   <li>Loads all top-level DataObjects in the Collection via
 *       {@link DataObjectDAO#findTopLevelByCollectionAppId}.</li>
 *   <li>For each DataObject reads its SMFL coordinate annotations
 *       ({@code ndt-section}, {@code ndt-module}, {@code ndt-layer},
 *       {@code ndt-frame}) and the cell-value annotation ({@code ndt-mean-delta-t}
 *       in mean-delta-t mode, or the configurable quality predicate in
 *       pass-fail mode) via
 *       {@link SemanticAnnotationDAO#findBySubjectAppId}.</li>
 *   <li>Applies optional dimension filters, then projects unique sorted row/column
 *       axes and a cells list onto the VIEW envelope.</li>
 * </ol>
 *
 * <p>The VIEW envelope is consumed by the {@code NdtGridView.vue} slice-3
 * renderer (canvas 2D mosaic with colour map).
 *
 * <h2>Template body parameters</h2>
 * <table>
 *   <caption>NdtGridShape body fields</caption>
 *   <tr><th>Field</th><th>Required</th><th>Default</th></tr>
 *   <tr><td>collectionAppId</td><td>yes</td><td>—</td></tr>
 *   <tr><td>rowDimension</td><td>no</td><td>"layer"</td></tr>
 *   <tr><td>columnDimension</td><td>no</td><td>"section-module"</td></tr>
 *   <tr><td>colourMode</td><td>no</td><td>"mean-delta-t"</td></tr>
 *   <tr><td>colourMap</td><td>no</td><td>"hot"</td></tr>
 *   <tr><td>qualityAnnotationPredicate</td><td>no</td>
 *       <td>"urn:shepard:mffd:ndt-quality"</td></tr>
 *   <tr><td>layerFilter</td><td>no</td><td>—</td></tr>
 *   <tr><td>sectionFilter</td><td>no</td><td>—</td></tr>
 *   <tr><td>moduleFilter</td><td>no</td><td>—</td></tr>
 * </table>
 *
 * <h2>Why ServiceLoader POJO + lazy CDI lookup</h2>
 * <p>{@link TransformExecutor} is a plain ServiceLoader SPI (NOT a CDI bean).
 * To reach the request-scoped DAOs it uses {@link CDI#current()} lazily inside
 * {@link #materialize} — inside the request scope of the dispatching
 * {@code POST /v2/mappings/{templateAppId}/materialize} call. Same pattern as
 * {@code SceneGraphPlayTransformExecutor}.
 */
public final class NdtGridTransformExecutor implements TransformExecutor {

  private static final Logger LOG = Logger.getLogger(NdtGridTransformExecutor.class.getName());

  // NDT annotation predicates — matches MffdNdtOtvisMeasurementKind constants.
  // Package-private for test access.
  static final String PRED_SECTION       = "urn:shepard:mffd:ndt-section";
  static final String PRED_MODULE        = "urn:shepard:mffd:ndt-module";
  static final String PRED_LAYER         = "urn:shepard:mffd:ndt-layer";
  static final String PRED_FRAME         = "urn:shepard:mffd:ndt-frame";
  static final String PRED_MEAN_DT       = "urn:shepard:mffd:ndt-mean-delta-t";
  static final String DEFAULT_QUALITY_PRED = "urn:shepard:mffd:ndt-quality";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(VisNdtGridPluginManifest.SHAPE_IRI);
  }

  @Override
  public String name() {
    return "NdtGridTransformExecutor";
  }

  @Override
  public TransformResult materialize(TransformRequest req) {
    if (req == null) {
      throw new TransformException("transform.body.invalid", "request must not be null");
    }
    JsonNode body = parseBody(req.templateBodyJson());

    String collectionAppId = text(body, "collectionAppId");
    if (collectionAppId == null) {
      throw new TransformException(
        "transform.input.missing",
        "ndt-grid MAPPING_RECIPE requires 'collectionAppId' in the template body"
      );
    }

    String rowDimension  = textOrDefault(body, "rowDimension",               "layer");
    String colDimension  = textOrDefault(body, "columnDimension",            "section-module");
    String colourMode    = textOrDefault(body, "colourMode",                 "mean-delta-t");
    String colourMap     = textOrDefault(body, "colourMap",                  "hot");
    String qualityPred   = textOrDefault(body, "qualityAnnotationPredicate", DEFAULT_QUALITY_PRED);
    String layerFilter   = text(body, "layerFilter");
    String sectionFilter = text(body, "sectionFilter");
    String moduleFilter  = text(body, "moduleFilter");

    DataObjectDAO dataObjectDAO;
    SemanticAnnotationDAO annotationDAO;
    try {
      dataObjectDAO = CDI.current().select(DataObjectDAO.class).get();
      annotationDAO = CDI.current().select(SemanticAnnotationDAO.class).get();
    } catch (RuntimeException ex) {
      throw new TransformException(
        "transform.internal-error",
        "ndt-grid executor could not resolve DAO services: " + ex.getMessage()
      );
    }

    List<DataObject> dataObjects = dataObjectDAO.findTopLevelByCollectionAppId(collectionAppId);
    LOG.debugf("MFFD-RENDER-NDT-GRID: collection=%s → %d top-level DataObjects",
               collectionAppId, dataObjects.size());

    String valuePred = "pass-fail".equals(colourMode) ? qualityPred : PRED_MEAN_DT;

    List<TileRecord> tiles = new ArrayList<>();
    for (DataObject dObj : dataObjects) {
      List<SemanticAnnotation> annots = annotationDAO.findBySubjectAppId(dObj.getAppId());
      TileRecord tile = buildTile(dObj.getAppId(), annots, valuePred);

      if (layerFilter   != null && !layerFilter.equals(tile.layer()))     continue;
      if (sectionFilter != null && !sectionFilter.equals(tile.section())) continue;
      if (moduleFilter  != null && !moduleFilter.equals(tile.module()))   continue;

      String rowKey = dimensionValue(tile, rowDimension);
      String colKey = columnValue(tile, colDimension);
      if (rowKey == null || colKey == null) {
        LOG.tracef(
          "MFFD-RENDER-NDT-GRID: DataObject %s missing %s or %s coordinate — skipped",
          dObj.getAppId(), rowDimension, colDimension
        );
        continue;
      }
      tiles.add(tile);
    }

    Map<String, Object> envelope = buildNdtGrid(
      collectionAppId, rowDimension, colDimension, colourMode, colourMap,
      qualityPred, layerFilter, sectionFilter, moduleFilter, tiles
    );
    return TransformResult.view(envelope, name());
  }

  // ─── Tile extraction ─────────────────────────────────────────────────────────

  /**
   * Extracts SMFL coordinates and cell value from a DataObject's annotation set.
   * Package-private for unit-test access.
   *
   * @param dataObjectAppId appId of the source DataObject
   * @param annots          its full annotation list
   * @param valuePred       the predicate whose value drives the cell colour
   *                        ({@link #PRED_MEAN_DT} or the quality predicate)
   * @return a {@link TileRecord} with nullable coordinate and value fields
   */
  TileRecord buildTile(
    String dataObjectAppId,
    List<SemanticAnnotation> annots,
    String valuePred
  ) {
    String section = null, module = null, layer = null, frame = null;
    Double numericValue = null;
    String qualityValue = null;

    for (SemanticAnnotation a : annots) {
      String iri = a.getPropertyIRI();
      if (iri == null) continue;
      switch (iri) {
        case PRED_SECTION -> section = a.getValueName();
        case PRED_MODULE  -> module  = a.getValueName();
        case PRED_LAYER   -> layer   = a.getValueName();
        case PRED_FRAME   -> frame   = a.getValueName();
        default -> {
          if (iri.equals(valuePred)) {
            numericValue = a.getNumericValue();
            qualityValue = a.getValueName();
          }
        }
      }
    }
    return new TileRecord(dataObjectAppId, section, module, layer, frame, numericValue, qualityValue);
  }

  // ─── Envelope construction ────────────────────────────────────────────────────

  /**
   * Builds the VIEW envelope from the already-filtered tile list.
   * Package-private for unit-test access.
   */
  Map<String, Object> buildNdtGrid(
    String collectionAppId,
    String rowDimension,
    String colDimension,
    String colourMode,
    String colourMap,
    String qualityPred,
    String layerFilter,
    String sectionFilter,
    String moduleFilter,
    List<TileRecord> tiles
  ) {
    Comparator<String> smflOrder = Comparator
      .comparingInt(NdtGridTransformExecutor::smflNumericPart)
      .thenComparing(Comparator.naturalOrder());
    Set<String> rowSet = new TreeSet<>(smflOrder);
    Set<String> colSet = new TreeSet<>(smflOrder);
    for (TileRecord t : tiles) {
      String rk = dimensionValue(t, rowDimension);
      String ck = columnValue(t, colDimension);
      if (rk != null) rowSet.add(rk);
      if (ck != null) colSet.add(ck);
    }

    List<Map<String, Object>> cells = new ArrayList<>();
    for (TileRecord t : tiles) {
      String rk = dimensionValue(t, rowDimension);
      String ck = columnValue(t, colDimension);
      if (rk == null || ck == null) continue;
      Map<String, Object> cell = new LinkedHashMap<>();
      cell.put("row",             rk);
      cell.put("col",             ck);
      cell.put("dataObjectAppId", t.dataObjectAppId());
      if (t.numericValue() != null) cell.put("value",   t.numericValue());
      if (t.qualityValue() != null) cell.put("quality", t.qualityValue());
      cell.put("status", "RESOLVED");
      cells.add(cell);
    }

    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("kind",            "ndt-grid");
    envelope.put("collectionAppId", collectionAppId);
    envelope.put("rowDimension",    rowDimension);
    envelope.put("columnDimension", colDimension);
    envelope.put("colourMode",      colourMode);
    envelope.put("colourMap",       colourMap);
    if (!DEFAULT_QUALITY_PRED.equals(qualityPred)) {
      envelope.put("qualityAnnotationPredicate", qualityPred);
    }
    if (layerFilter   != null) envelope.put("layerFilter",   layerFilter);
    if (sectionFilter != null) envelope.put("sectionFilter", sectionFilter);
    if (moduleFilter  != null) envelope.put("moduleFilter",  moduleFilter);
    envelope.put("rows",    new ArrayList<>(rowSet));
    envelope.put("columns", new ArrayList<>(colSet));
    envelope.put("cells",   cells);
    return envelope;
  }

  // ─── Axis resolution ─────────────────────────────────────────────────────────

  private static String dimensionValue(TileRecord t, String dimension) {
    return switch (dimension) {
      case "section" -> t.section();
      case "module"  -> t.module();
      case "layer"   -> t.layer();
      case "frame"   -> t.frame();
      default        -> null;
    };
  }

  private static String columnValue(TileRecord t, String dimension) {
    if ("section-module".equals(dimension)) {
      if (t.section() == null || t.module() == null) return null;
      return t.section() + "-" + t.module();
    }
    return dimensionValue(t, dimension);
  }

  // ─── SMFL label sort: S4 < S10, L18 < L18+, S4-M13 sorts by leading number ──

  /**
   * Extracts the leading integer from an SMFL label for numeric axis sorting.
   * Strips the letter prefix, then reads digits up to the first {@code -} or
   * {@code +} character. Returns {@link Integer#MAX_VALUE} when parsing fails
   * so unlabelled tiles sort last. Package-private for test access.
   */
  static int smflNumericPart(String label) {
    if (label == null || label.isEmpty()) return Integer.MAX_VALUE;
    int start = 0;
    while (start < label.length() && Character.isLetter(label.charAt(start))) start++;
    String rest = label.substring(start);
    int end = rest.length();
    for (int i = 0; i < rest.length(); i++) {
      char c = rest.charAt(i);
      if (c == '-' || c == '+') { end = i; break; }
    }
    try {
      return Integer.parseInt(rest.substring(0, end));
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  // ─── Body parsing ────────────────────────────────────────────────────────────

  private JsonNode parseBody(String bodyJson) {
    if (bodyJson == null || bodyJson.isBlank()) return MAPPER.createObjectNode();
    try {
      return MAPPER.readTree(bodyJson);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new TransformException(
        "transform.body.invalid",
        "MAPPING_RECIPE body is not valid JSON: " + e.getMessage()
      );
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (!v.isTextual()) return null;
    String s = v.asText();
    return (s == null || s.isBlank()) ? null : s;
  }

  private static String textOrDefault(JsonNode node, String field, String defaultValue) {
    String v = text(node, field);
    return v != null ? v : defaultValue;
  }

  // ─── Internal data holder ────────────────────────────────────────────────────

  record TileRecord(
    String dataObjectAppId,
    String section,
    String module,
    String layer,
    String frame,
    Double numericValue,
    String qualityValue
  ) {}
}
