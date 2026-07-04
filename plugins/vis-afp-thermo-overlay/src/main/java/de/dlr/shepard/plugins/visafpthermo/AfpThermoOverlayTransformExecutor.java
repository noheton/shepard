package de.dlr.shepard.plugins.visafpthermo;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * MFFD-RENDER-AFP-THERMO-OVERLAY slice 2 — the {@link TransformExecutor} that
 * resolves AFP course metadata and NDT measurement metadata for the synced
 * dual-pane AFP+NDT overlay VIEW envelope.
 *
 * <p>Claims the {@code AfpThermoOverlayShape} IRI. A MAPPING_RECIPE template
 * targeting that shape binds two DataObject appIds:
 * <ul>
 *   <li>{@code afpDataObjectAppId} — an {@code mffd:afp-course} DataObject
 *       carrying AFP process annotations (TCP path ref, ply-id, course-id,
 *       setpoints, section, module).</li>
 *   <li>{@code ndtDataObjectAppId} — an {@code mffd:ndt-otvis-measurement}
 *       DataObject for the same tile, carrying SMFL coordinate annotations
 *       and a source OTvis FileReference IRI.</li>
 * </ul>
 *
 * <p>Materialising the template resolves both DataObjects' annotation sets
 * and returns a VIEW envelope containing all appIds and display parameters
 * the slice-3 {@code AfpThermoOverlayCanvas.vue} component needs to drive
 * its own lazy data fetches (TCP timeseries channels + NDT heatmap image).
 *
 * <h2>Template body parameters</h2>
 * <table>
 *   <caption>AfpThermoOverlayShape body fields</caption>
 *   <tr><th>Field</th><th>Required</th><th>Default</th></tr>
 *   <tr><td>afpDataObjectAppId</td><td>yes</td><td>—</td></tr>
 *   <tr><td>ndtDataObjectAppId</td><td>yes</td><td>—</td></tr>
 *   <tr><td>section</td><td>no</td><td>read from AFP DataObject annotation</td></tr>
 *   <tr><td>module</td><td>no</td><td>read from AFP DataObject annotation</td></tr>
 *   <tr><td>plyNumber</td><td>no</td><td>—</td></tr>
 *   <tr><td>courseNumber</td><td>no</td><td>—</td></tr>
 *   <tr><td>tcpChannel</td><td>no</td><td>"tcp-temperature"</td></tr>
 *   <tr><td>colourMap</td><td>no</td><td>"hot"</td></tr>
 *   <tr><td>syncMode</td><td>no</td><td>"side-by-side"</td></tr>
 *   <tr><td>timeWindowStartUs</td><td>no</td><td>—</td></tr>
 *   <tr><td>timeWindowEndUs</td><td>no</td><td>—</td></tr>
 * </table>
 *
 * <h2>Why ServiceLoader POJO + lazy CDI lookup</h2>
 * <p>{@link TransformExecutor} is a plain ServiceLoader SPI (NOT a CDI bean).
 * To reach the request-scoped DAOs it uses {@link CDI#current()} lazily inside
 * {@link #materialize} — inside the request scope of the dispatching
 * {@code POST /v2/mappings/{templateAppId}/materialize} call.
 * Same pattern as {@code NdtGridTransformExecutor}.
 */
public final class AfpThermoOverlayTransformExecutor implements TransformExecutor {

  private static final Logger LOG =
      Logger.getLogger(AfpThermoOverlayTransformExecutor.class.getName());

  // AFP annotation predicates — match MffdAfpCourseKind constants.
  // Package-private for test access.
  static final String PRED_AFP_TCP_PATH_REF   = "urn:shepard:mffd:tcp-path-ref";
  static final String PRED_AFP_PLY_ID         = "urn:shepard:mffd:ply-id";
  static final String PRED_AFP_COURSE_ID      = "urn:shepard:mffd:course-id";
  static final String PRED_AFP_LASER_TEMP     = "urn:shepard:mffd:laser-temp-setpoint";
  static final String PRED_AFP_TAPE_SPEED     = "urn:shepard:mffd:tape-speed-setpoint";
  static final String PRED_AFP_MATERIAL_BATCH = "urn:shepard:mffd:material-batch";
  static final String PRED_AFP_SECTION        = "urn:shepard:mffd:afp-section";
  static final String PRED_AFP_MODULE         = "urn:shepard:mffd:afp-module";

  // NDT annotation predicates — match MffdNdtOtvisMeasurementKind constants.
  static final String PRED_NDT_SECTION        = "urn:shepard:mffd:ndt-section";
  static final String PRED_NDT_MODULE         = "urn:shepard:mffd:ndt-module";
  static final String PRED_NDT_LAYER          = "urn:shepard:mffd:ndt-layer";
  static final String PRED_NDT_FRAME          = "urn:shepard:mffd:ndt-frame";
  static final String PRED_NDT_SOURCE_FILEREF = "urn:shepard:mffd:ndt-source-fileref";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(VisAfpThermoOverlayPluginManifest.SHAPE_IRI);
  }

  @Override
  public String name() {
    return "AfpThermoOverlayTransformExecutor";
  }

  @Override
  public TransformResult materialize(TransformRequest req) {
    if (req == null) {
      throw new TransformException("transform.body.invalid", "request must not be null");
    }
    JsonNode body = parseBody(req.templateBodyJson());

    String afpDataObjectAppId = text(body, "afpDataObjectAppId");
    String ndtDataObjectAppId = text(body, "ndtDataObjectAppId");

    if (afpDataObjectAppId == null) {
      throw new TransformException(
          "transform.input.missing",
          "afp-thermo-overlay MAPPING_RECIPE requires 'afpDataObjectAppId' in the template body");
    }
    if (ndtDataObjectAppId == null) {
      throw new TransformException(
          "transform.input.missing",
          "afp-thermo-overlay MAPPING_RECIPE requires 'ndtDataObjectAppId' in the template body");
    }

    String sectionOverride = text(body, "section");
    String moduleOverride  = text(body, "module");
    Integer plyNumber      = intValue(body, "plyNumber");
    Integer courseNumber   = intValue(body, "courseNumber");
    String tcpChannel      = textOrDefault(body, "tcpChannel", "tcp-temperature");
    String colourMap       = textOrDefault(body, "colourMap", "hot");
    String syncMode        = textOrDefault(body, "syncMode", "side-by-side");
    Long timeWindowStartUs = longValue(body, "timeWindowStartUs");
    Long timeWindowEndUs   = longValue(body, "timeWindowEndUs");

    DataObjectDAO dataObjectDAO;
    SemanticAnnotationDAO annotationDAO;
    try {
      dataObjectDAO = CDI.current().select(DataObjectDAO.class).get();
      annotationDAO = CDI.current().select(SemanticAnnotationDAO.class).get();
    } catch (RuntimeException ex) {
      throw new TransformException(
          "transform.internal-error",
          "afp-thermo-overlay executor could not resolve DAO services: " + ex.getMessage());
    }

    DataObject afpDo = dataObjectDAO.findByAppId(afpDataObjectAppId);
    if (afpDo == null) {
      throw new TransformException(
          "transform.input.not-found",
          "afpDataObjectAppId not found: " + afpDataObjectAppId);
    }
    List<SemanticAnnotation> afpAnnots = annotationDAO.findBySubjectAppId(afpDataObjectAppId);
    AfpRecord afp = buildAfpRecord(afpDataObjectAppId, afpAnnots);
    LOG.debugf(
        "MFFD-RENDER-AFP-THERMO-OVERLAY: AFP DO=%s plyId=%s courseId=%s tcpRef=%s",
        afpDataObjectAppId, afp.plyId(), afp.courseId(), afp.tcpPathRefIri());

    DataObject ndtDo = dataObjectDAO.findByAppId(ndtDataObjectAppId);
    if (ndtDo == null) {
      throw new TransformException(
          "transform.input.not-found",
          "ndtDataObjectAppId not found: " + ndtDataObjectAppId);
    }
    List<SemanticAnnotation> ndtAnnots = annotationDAO.findBySubjectAppId(ndtDataObjectAppId);
    NdtRecord ndt = buildNdtRecord(ndtDataObjectAppId, ndtAnnots);
    LOG.debugf(
        "MFFD-RENDER-AFP-THERMO-OVERLAY: NDT DO=%s section=%s module=%s layer=%s",
        ndtDataObjectAppId, ndt.section(), ndt.module(), ndt.layer());

    String effectiveSection = sectionOverride != null ? sectionOverride : afp.section();
    String effectiveModule  = moduleOverride  != null ? moduleOverride  : afp.module();
    String tileMatch = resolveTileMatch(effectiveSection, effectiveModule, ndt);

    Map<String, Object> envelope = buildEnvelope(
        afp, ndt, tcpChannel, colourMap, syncMode,
        effectiveSection, effectiveModule,
        plyNumber, courseNumber,
        timeWindowStartUs, timeWindowEndUs,
        tileMatch);
    return TransformResult.view(envelope, name());
  }

  // ─── Internal data holders ────────────────────────────────────────────────────

  record AfpRecord(
      String dataObjectAppId,
      String tcpPathRefIri,
      String plyId,
      String courseId,
      Double laserTempSetpointC,
      Double tapeSpeedSetpointMpm,
      String materialBatchIri,
      String section,
      String module) {}

  record NdtRecord(
      String dataObjectAppId,
      String section,
      String module,
      String layer,
      String frame,
      String sourceFileRefIri) {}

  // ─── Annotation extraction ────────────────────────────────────────────────────

  AfpRecord buildAfpRecord(String appId, List<SemanticAnnotation> annots) {
    String tcpPathRef = null, plyId = null, courseId = null, materialBatch = null;
    String section = null, module = null;
    Double laserTemp = null, tapeSpeed = null;

    for (SemanticAnnotation a : annots) {
      String iri = a.getPropertyIRI();
      if (iri == null) continue;
      switch (iri) {
        case PRED_AFP_TCP_PATH_REF   -> tcpPathRef    = a.getValueName();
        case PRED_AFP_PLY_ID         -> plyId         = a.getValueName();
        case PRED_AFP_COURSE_ID      -> courseId      = a.getValueName();
        case PRED_AFP_LASER_TEMP     -> laserTemp     = a.getNumericValue();
        case PRED_AFP_TAPE_SPEED     -> tapeSpeed     = a.getNumericValue();
        case PRED_AFP_MATERIAL_BATCH -> materialBatch = a.getValueName();
        case PRED_AFP_SECTION        -> section       = a.getValueName();
        case PRED_AFP_MODULE         -> module        = a.getValueName();
        default -> { /* ignore */ }
      }
    }
    return new AfpRecord(appId, tcpPathRef, plyId, courseId, laserTemp, tapeSpeed,
                         materialBatch, section, module);
  }

  NdtRecord buildNdtRecord(String appId, List<SemanticAnnotation> annots) {
    String section = null, module = null, layer = null, frame = null, sourceFileRef = null;

    for (SemanticAnnotation a : annots) {
      String iri = a.getPropertyIRI();
      if (iri == null) continue;
      switch (iri) {
        case PRED_NDT_SECTION        -> section       = a.getValueName();
        case PRED_NDT_MODULE         -> module        = a.getValueName();
        case PRED_NDT_LAYER          -> layer         = a.getValueName();
        case PRED_NDT_FRAME          -> frame         = a.getValueName();
        case PRED_NDT_SOURCE_FILEREF -> sourceFileRef = a.getValueName();
        default -> { /* ignore */ }
      }
    }
    return new NdtRecord(appId, section, module, layer, frame, sourceFileRef);
  }

  // ─── Tile match verification ──────────────────────────────────────────────────

  String resolveTileMatch(String effectiveSection, String effectiveModule, NdtRecord ndt) {
    if (effectiveSection == null || effectiveModule == null) return "UNVERIFIED";
    if (ndt.section() == null || ndt.module() == null) return "UNVERIFIED";
    boolean sMatch = effectiveSection.equals(ndt.section());
    boolean mMatch = effectiveModule.equals(ndt.module());
    return (sMatch && mMatch) ? "MATCHED" : "MISMATCHED";
  }

  // ─── VIEW envelope construction ───────────────────────────────────────────────

  Map<String, Object> buildEnvelope(
      AfpRecord afp,
      NdtRecord ndt,
      String tcpChannel,
      String colourMap,
      String syncMode,
      String effectiveSection,
      String effectiveModule,
      Integer plyNumber,
      Integer courseNumber,
      Long timeWindowStartUs,
      Long timeWindowEndUs,
      String tileMatch) {

    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("kind", "afp-thermo-overlay");
    envelope.put("syncMode", syncMode);
    envelope.put("tcpChannel", tcpChannel);
    envelope.put("colourMap", colourMap);
    if (effectiveSection != null) envelope.put("section", effectiveSection);
    if (effectiveModule  != null) envelope.put("module",  effectiveModule);
    envelope.put("tileMatch", tileMatch);

    // AFP DataObject summary
    Map<String, Object> afpMap = new LinkedHashMap<>();
    afpMap.put("dataObjectAppId", afp.dataObjectAppId());
    if (afp.tcpPathRefIri()      != null) afpMap.put("tcpTimeseriesRefAppId",   afp.tcpPathRefIri());
    if (afp.plyId()              != null) afpMap.put("plyId",                   afp.plyId());
    if (afp.courseId()           != null) afpMap.put("courseId",                afp.courseId());
    if (afp.laserTempSetpointC() != null) afpMap.put("laserTempSetpointC",      afp.laserTempSetpointC());
    if (afp.tapeSpeedSetpointMpm() != null) afpMap.put("tapeSpeedSetpointMpm", afp.tapeSpeedSetpointMpm());
    if (afp.materialBatchIri()   != null) afpMap.put("materialBatchIri",        afp.materialBatchIri());
    if (plyNumber                != null) afpMap.put("plyFilter",               plyNumber);
    if (courseNumber             != null) afpMap.put("courseFilter",             courseNumber);
    if (timeWindowStartUs        != null) afpMap.put("timeWindowStartUs",        timeWindowStartUs);
    if (timeWindowEndUs          != null) afpMap.put("timeWindowEndUs",          timeWindowEndUs);
    envelope.put("afp", afpMap);

    // NDT DataObject summary
    Map<String, Object> ndtMap = new LinkedHashMap<>();
    ndtMap.put("dataObjectAppId", ndt.dataObjectAppId());
    if (ndt.section()         != null) ndtMap.put("section",           ndt.section());
    if (ndt.module()          != null) ndtMap.put("module",            ndt.module());
    if (ndt.layer()           != null) ndtMap.put("layer",             ndt.layer());
    if (ndt.frame()           != null) ndtMap.put("frame",             ndt.frame());
    if (ndt.sourceFileRefIri() != null) ndtMap.put("sourceFileRefAppId", ndt.sourceFileRefIri());
    envelope.put("ndt", ndtMap);

    return envelope;
  }

  // ─── Body parsing helpers ─────────────────────────────────────────────────────

  private JsonNode parseBody(String bodyJson) {
    if (bodyJson == null || bodyJson.isBlank()) return MAPPER.createObjectNode();
    try {
      return MAPPER.readTree(bodyJson);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new TransformException(
          "transform.body.invalid",
          "MAPPING_RECIPE body is not valid JSON: " + e.getMessage());
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

  private static Integer intValue(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isIntegralNumber() ? v.intValue() : null;
  }

  private static Long longValue(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isIntegralNumber() ? v.longValue() : null;
  }
}
