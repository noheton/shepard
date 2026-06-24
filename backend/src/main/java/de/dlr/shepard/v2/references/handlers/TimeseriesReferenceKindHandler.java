package de.dlr.shepard.v2.references.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.timeseriesreference.daos.ReferencedTimeseriesNodeEntityDAO;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.utilities.TimeseriesValidator;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V2CONV-A2 — in-tree {@link ReferenceKindHandler} for {@code kind=timeseries}.
 * Delegates to {@link TimeseriesReferenceService}.
 *
 * <p>Create deserialises the body into a {@link TimeseriesReferenceIO} (the
 * existing per-kind create shape). Patch honours the TM1 time-alignment
 * fields only ({@code timeReference}, {@code wallClockOffset},
 * {@code wallClockOffsetSource}) — the same contract as the
 * {@code PATCH /v2/timeseries-references/{appId}} endpoint it converges.
 * Payload key set: {@code {start, end, timeseriesContainerId, timeseries,
 * timeReference, wallClockOffset, wallClockOffsetSource, qualityScore,
 * lastScoredAt}}.
 */
@RequestScoped
public class TimeseriesReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  TimeseriesAnnotationDAO tsAnnotationDAO;

  @Inject
  ReferencedTimeseriesNodeEntityDAO tsNodeEntityDAO;

  @Override
  public String kind() {
    return "timeseries";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof TimeseriesReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return timeseriesReferenceDAO.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    TimeseriesReference ref = (TimeseriesReference) reference;
    TimeseriesReferenceIO kindIO = new TimeseriesReferenceIO(ref);
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    io.put("start", kindIO.getStart());
    io.put("end", kindIO.getEnd());
    io.put("timeseriesContainerId", kindIO.getTimeseriesContainerId());
    // APISIMP-TSCONT-APPID-KEY-3: expose container appId so callers can reach /v2/channels
    io.put("timeseriesContainerAppId",
        ref.getTimeseriesContainer() != null ? ref.getTimeseriesContainer().getAppId() : null);
    io.put("timeseries", kindIO.getTimeseries());
    io.put("timeReference", kindIO.getTimeReference());
    io.put("wallClockOffset", kindIO.getWallClockOffset());
    io.put("wallClockOffsetSource", kindIO.getWallClockOffsetSource());
    io.put("qualityScore", kindIO.getQualityScore());
    io.put("lastScoredAt", kindIO.getLastScoredAt());
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) throw new BadRequestException("create body is required for kind=timeseries");
    DataObject parent = resolveParent(dataObjectAppId);

    TimeseriesReferenceIO ioIn;
    try {
      ioIn = objectMapper.convertValue(body, TimeseriesReferenceIO.class);
    } catch (IllegalArgumentException iae) {
      throw new BadRequestException("invalid timeseries create body: " + iae.getMessage());
    }

    TimeseriesReference created = timeseriesReferenceService.createReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ioIn
    );
    return toIO(created);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    TimeseriesReference ref = timeseriesReferenceDAO.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No TimeseriesReference with appId " + appId);

    TimeseriesReferenceIO body;
    try {
      body = objectMapper.convertValue(patch, TimeseriesReferenceIO.class);
    } catch (IllegalArgumentException iae) {
      throw new BadRequestException("invalid timeseries patch body: " + iae.getMessage());
    }

    // REF-EDIT-1: apply basic scalar fields from the raw map so that absent keys
    // are distinguished from explicit zero/null (primitive long can't express absence).
    if (patch.containsKey("name") && patch.get("name") instanceof String s && !s.isBlank()) {
      ref.setName(s.strip());
    }
    if (patch.containsKey("start") && patch.get("start") != null) {
      ref.setStart(toLong(patch.get("start"), "start"));
    }
    if (patch.containsKey("end") && patch.get("end") != null) {
      ref.setEnd(toLong(patch.get("end"), "end"));
    }

    // REF-EDIT-1: replace the channel list when provided.
    if (patch.containsKey("timeseries") && patch.get("timeseries") instanceof List<?> rawList && !rawList.isEmpty()) {
      List<Timeseries> newTimeseries;
      try {
        newTimeseries = objectMapper.convertValue(rawList, new TypeReference<List<Timeseries>>() {});
      } catch (IllegalArgumentException iae) {
        throw new BadRequestException("invalid timeseries channel list: " + iae.getMessage());
      }
      if (newTimeseries.isEmpty()) {
        throw new BadRequestException("timeseries must be non-empty when provided");
      }
      newTimeseries.forEach(ts -> TimeseriesValidator.assertTimeseriesPropertiesAreValid(ts));
      ref.getReferencedTimeseriesList().clear();
      for (Timeseries ts : newTimeseries) {
        ReferencedTimeseriesNodeEntity found = tsNodeEntityDAO.find(
          ts.getMeasurement(), ts.getDevice(), ts.getLocation(), ts.getSymbolicName(), ts.getField());
        ref.addTimeseries(found != null ? found : new ReferencedTimeseriesNodeEntity(ts));
      }
    }

    // Same validation the converged PATCH /v2/timeseries-references/{appId} applied.
    String effectiveTimeRef = body.getTimeReference() != null ? body.getTimeReference() : ref.getTimeReference();
    Long effectiveOffset = body.getWallClockOffset() != null ? body.getWallClockOffset() : ref.getWallClockOffset();
    if ("EXPERIMENT_RELATIVE".equals(effectiveTimeRef) && effectiveOffset == null) {
      throw new BadRequestException("wallClockOffset is required when timeReference is EXPERIMENT_RELATIVE");
    }

    // updateTimeReference applies TM1 fields and persists (single save covers all changes above).
    TimeseriesReference updated = timeseriesReferenceService.updateTimeReference(ref, body);
    return toIO(updated);
  }

  @Override
  public void delete(String appId) {
    TimeseriesReference ref = timeseriesReferenceDAO.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No TimeseriesReference with appId " + appId);
    DataObject parent = ref.getDataObject();
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("TimeseriesReference " + appId + " has no resolvable parent DataObject");
    }
    timeseriesReferenceService.deleteReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ref.getShepardId()
    );
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    DataObject parent = resolveParent(dataObjectAppId);
    List<TimeseriesReference> refs = timeseriesReferenceService.getAllReferencesByDataObjectId(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      null
    );
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (TimeseriesReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }

  // ─── annotation sub-resource (APISIMP-ANNOTATION-SUBRESOURCE-COLLISION) ──

  @Override
  public boolean supportsAnnotations() { return true; }

  @Override
  public List<Map<String, Object>> listAnnotations(String refAppId) {
    return tsAnnotationDAO.findByTimeseriesReferenceAppId(refAppId).stream()
      .map(TimeseriesReferenceKindHandler::annotationToMap)
      .toList();
  }

  @Override
  public Map<String, Object> createAnnotation(String refAppId, Map<String, Object> body) {
    if (body == null || !body.containsKey("startNs") || body.get("startNs") == null) {
      throw new BadRequestException("startNs is required for timeseries annotations");
    }
    String label = requireLabel(body);
    TimeseriesAnnotation a = new TimeseriesAnnotation();
    a.setStartNs(toLong(body.get("startNs"), "startNs"));
    if (body.containsKey("endNs") && body.get("endNs") != null) {
      a.setEndNs(toLong(body.get("endNs"), "endNs"));
    }
    a.setLabel(label);
    if (body.containsKey("description")) a.setDescription(asString(body.get("description")));
    if (Boolean.TRUE.equals(body.get("aiGenerated"))) a.setAiGenerated(true);
    if (body.containsKey("confidence") && body.get("confidence") != null) {
      a.setConfidence(toDouble(body.get("confidence"), "confidence"));
    }
    tsAnnotationDAO.createOrUpdate(a);
    tsAnnotationDAO.linkToReference(refAppId, a.getAppId());
    return annotationToMap(a);
  }

  @Override
  public Map<String, Object> getAnnotation(String refAppId, String annotationAppId) {
    TimeseriesAnnotation a = tsAnnotationDAO.findByAppId(annotationAppId);
    if (a == null) throw new NotFoundException("Annotation not found: " + annotationAppId);
    return annotationToMap(a);
  }

  @Override
  public Map<String, Object> patchAnnotation(String refAppId, String annotationAppId, Map<String, Object> patch) {
    TimeseriesAnnotation a = tsAnnotationDAO.findByAppId(annotationAppId);
    if (a == null) throw new NotFoundException("Annotation not found: " + annotationAppId);
    if (patch == null) return annotationToMap(a);
    if (patch.containsKey("startNs") && patch.get("startNs") != null) {
      a.setStartNs(toLong(patch.get("startNs"), "startNs"));
    }
    if (patch.containsKey("endNs")) {
      a.setEndNs(patch.get("endNs") == null ? null : toLong(patch.get("endNs"), "endNs"));
    }
    if (patch.containsKey("label")) {
      String lbl = patch.get("label") instanceof String s ? s : null;
      if (lbl == null || lbl.isBlank()) throw new BadRequestException("label must be non-blank when provided");
      a.setLabel(lbl.strip());
    }
    if (patch.containsKey("description")) a.setDescription(asString(patch.get("description")));
    if (patch.containsKey("confidence")) {
      a.setConfidence(patch.get("confidence") == null ? null : toDouble(patch.get("confidence"), "confidence"));
    }
    tsAnnotationDAO.createOrUpdate(a);
    return annotationToMap(a);
  }

  @Override
  public void deleteAnnotation(String refAppId, String annotationAppId) {
    TimeseriesAnnotation a = tsAnnotationDAO.findByAppId(annotationAppId);
    if (a == null) throw new NotFoundException("Annotation not found: " + annotationAppId);
    tsAnnotationDAO.unlinkAndDelete(refAppId, a);
  }

  private static Map<String, Object> annotationToMap(TimeseriesAnnotation a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("appId", a.getAppId());
    m.put("startNs", a.getStartNs());
    m.put("endNs", a.getEndNs());
    m.put("label", a.getLabel());
    m.put("description", a.getDescription());
    m.put("aiGenerated", a.isAiGenerated());
    m.put("confidence", a.getConfidence());
    return m;
  }

  private static String requireLabel(Map<String, Object> body) {
    Object v = body.get("label");
    if (!(v instanceof String s) || s.isBlank()) {
      throw new BadRequestException("label is required and must be non-blank");
    }
    return s.strip();
  }

  private static Long toLong(Object v, String field) {
    if (v instanceof Number n) return n.longValue();
    if (v instanceof String s) {
      try { return Long.parseLong(s); } catch (NumberFormatException e) { /* fall through */ }
    }
    throw new BadRequestException("'" + field + "' must be a long integer, got: " + v);
  }

  private static Double toDouble(Object v, String field) {
    if (v instanceof Number n) return n.doubleValue();
    throw new BadRequestException("'" + field + "' must be a number, got: " + v);
  }

  private static String asString(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  // ─────────────────────────────────────────────────────────────────────────

  private DataObject resolveParent(String dataObjectAppId) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    DataObject parent = dataObjectDAO.findByAppId(dataObjectAppId);
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    return parent;
  }
}
