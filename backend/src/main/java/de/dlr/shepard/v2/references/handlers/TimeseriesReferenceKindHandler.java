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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
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
 * {@code PATCH /v2/references/{appId}} endpoint it converges.
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
    io.put("start", nanosToIso(kindIO.getStart()));
    io.put("end", nanosToIso(kindIO.getEnd()));
    io.put("timeseriesContainerId", kindIO.getTimeseriesContainerId());
    // APISIMP-TSCONT-APPID-KEY-3: expose container appId so callers can reach /v2/channels
    io.put("timeseriesContainerAppId",
        ref.getTimeseriesContainer() != null ? ref.getTimeseriesContainer().getAppId() : null);
    io.put("timeseries", kindIO.getTimeseries());
    io.put("timeReference", kindIO.getTimeReference());
    // APISIMP-TSREF-WALLCLOCK-OFFSET-NANOS: nanosecond epoch → ISO 8601 UTC instant
    Long wco = kindIO.getWallClockOffset();
    io.put("wallClockOffset", wco != null ? nanosToIso(wco) : null);
    io.put("wallClockOffsetSource", kindIO.getWallClockOffsetSource());
    io.put("qualityScore", kindIO.getQualityScore());
    // APISIMP-TSREF-LASTSCOREDAT-MS-TO-ISO: epoch-ms → ISO 8601 UTC instant (response-only)
    Long lsa = kindIO.getLastScoredAt();
    io.put("lastScoredAt", lsa != null ? Instant.ofEpochMilli(lsa).toString() : null);
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) throw new BadRequestException("create body is required for kind=timeseries");
    DataObject parent = resolveParent(dataObjectAppId);

    // APISIMP-TSREF-TIMEWINDOW-NANOS / APISIMP-TSREF-WALLCLOCK-OFFSET-NANOS: accept ISO 8601 for
    // start/end/wallClockOffset; normalise to nanosecond longs before ObjectMapper deserialization
    // so TimeseriesReferenceIO long primitives / Long fields bind correctly.
    Map<String, Object> normalized = new HashMap<>(body);
    if (normalized.containsKey("start") && normalized.get("start") != null) {
      normalized.put("start", isoOrLongToNanos(normalized.get("start"), "start"));
    }
    if (normalized.containsKey("end") && normalized.get("end") != null) {
      normalized.put("end", isoOrLongToNanos(normalized.get("end"), "end"));
    }
    if (normalized.containsKey("wallClockOffset") && normalized.get("wallClockOffset") instanceof String) {
      normalized.put("wallClockOffset", isoOrLongToNanos(normalized.get("wallClockOffset"), "wallClockOffset"));
    }

    TimeseriesReferenceIO ioIn;
    try {
      ioIn = objectMapper.convertValue(normalized, TimeseriesReferenceIO.class);
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

    // APISIMP-TSREF-TIMEWINDOW-NANOS / APISIMP-TSREF-WALLCLOCK-OFFSET-NANOS: normalise ISO 8601
    // start/end/wallClockOffset strings to nanosecond longs before ObjectMapper deserialises.
    Map<String, Object> normalized = new HashMap<>(patch);
    if (normalized.containsKey("start") && normalized.get("start") instanceof String) {
      normalized.put("start", isoOrLongToNanos(normalized.get("start"), "start"));
    }
    if (normalized.containsKey("end") && normalized.get("end") instanceof String) {
      normalized.put("end", isoOrLongToNanos(normalized.get("end"), "end"));
    }
    if (normalized.containsKey("wallClockOffset") && normalized.get("wallClockOffset") instanceof String) {
      normalized.put("wallClockOffset", isoOrLongToNanos(normalized.get("wallClockOffset"), "wallClockOffset"));
    }

    TimeseriesReferenceIO body;
    try {
      body = objectMapper.convertValue(normalized, TimeseriesReferenceIO.class);
    } catch (IllegalArgumentException iae) {
      throw new BadRequestException("invalid timeseries patch body: " + iae.getMessage());
    }

    // REF-EDIT-1: apply basic scalar fields from the normalised map so that absent keys
    // are distinguished from explicit zero/null (primitive long can't express absence).
    if (normalized.containsKey("name") && normalized.get("name") instanceof String s && !s.isBlank()) {
      ref.setName(s.strip());
    }
    if (normalized.containsKey("start") && normalized.get("start") != null) {
      ref.setStart(isoOrLongToNanos(normalized.get("start"), "start"));
    }
    if (normalized.containsKey("end") && normalized.get("end") != null) {
      ref.setEnd(isoOrLongToNanos(normalized.get("end"), "end"));
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

    // Same validation the converged PATCH /v2/references/{appId} applied.
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

  @Override
  public int countByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    return timeseriesReferenceDAO.countByDataObjectAppId(dataObjectAppId);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind, int skip, int limit) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<TimeseriesReference> refs = timeseriesReferenceDAO.findByDataObjectAppId(dataObjectAppId, skip, limit);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (TimeseriesReference ref : refs) {
      if (ref != null) out.add(toIO(ref));
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
  public long countAnnotations(String refAppId) {
    return tsAnnotationDAO.countByTimeseriesReferenceAppId(refAppId);
  }

  @Override
  public List<Map<String, Object>> listAnnotations(String refAppId, long skip, int limit) {
    return tsAnnotationDAO.findByTimeseriesReferenceAppId(refAppId, skip, limit).stream()
      .map(TimeseriesReferenceKindHandler::annotationToMap)
      .toList();
  }

  @Override
  public Map<String, Object> createAnnotation(String refAppId, Map<String, Object> body) {
    Object startVal = body != null ? (body.containsKey("start") ? body.get("start") : body.get("startNs")) : null;
    if (startVal == null) {
      throw new BadRequestException("start (ISO 8601) or startNs (nanoseconds) is required for timeseries annotations");
    }
    String label = requireLabel(body);
    TimeseriesAnnotation a = new TimeseriesAnnotation();
    a.setStartNs(isoOrLongToNanos(startVal, "start"));
    Object endVal = body.containsKey("end") ? body.get("end") : body.get("endNs");
    if (endVal != null) {
      a.setEndNs(isoOrLongToNanos(endVal, "end"));
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
    String startKey = patch.containsKey("start") ? "start" : "startNs";
    if (patch.containsKey(startKey) && patch.get(startKey) != null) {
      a.setStartNs(isoOrLongToNanos(patch.get(startKey), startKey));
    }
    String endKey = patch.containsKey("end") ? "end" : "endNs";
    if (patch.containsKey(endKey)) {
      a.setEndNs(patch.get(endKey) == null ? null : isoOrLongToNanos(patch.get(endKey), endKey));
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
    m.put("start", nanosToIso(a.getStartNs()));
    m.put("end", a.getEndNs() != null ? nanosToIso(a.getEndNs()) : null);
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

  /** APISIMP-TSREF-TIMEWINDOW-NANOS: convert nanosecond epoch to ISO 8601 UTC string. */
  private static String nanosToIso(long ns) {
    return Instant.ofEpochSecond(ns / 1_000_000_000L, ns % 1_000_000_000L).toString();
  }

  /**
   * APISIMP-TSREF-TIMEWINDOW-NANOS: accept ISO 8601 strings or legacy nanosecond longs for
   * {@code start}/{@code end}. Tries ISO 8601 parse first, then plain long parse.
   */
  private static long isoOrLongToNanos(Object v, String field) {
    if (v instanceof Number n) return n.longValue();
    if (v instanceof String s) {
      try {
        Instant inst = Instant.parse(s);
        return inst.getEpochSecond() * 1_000_000_000L + inst.getNano();
      } catch (DateTimeParseException ignored) { /* fall through to numeric parse */ }
      try { return Long.parseLong(s); } catch (NumberFormatException e) { /* fall through */ }
    }
    throw new BadRequestException("'" + field + "' must be an ISO 8601 timestamp or nanosecond long, got: " + v);
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
