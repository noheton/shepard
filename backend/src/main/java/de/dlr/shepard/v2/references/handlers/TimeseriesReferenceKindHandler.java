package de.dlr.shepard.v2.references.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
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

    // Same validation the converged PATCH /v2/timeseries-references/{appId} applied.
    String effectiveTimeRef = body.getTimeReference() != null ? body.getTimeReference() : ref.getTimeReference();
    Long effectiveOffset = body.getWallClockOffset() != null ? body.getWallClockOffset() : ref.getWallClockOffset();
    if ("EXPERIMENT_RELATIVE".equals(effectiveTimeRef) && effectiveOffset == null) {
      throw new BadRequestException("wallClockOffset is required when timeReference is EXPERIMENT_RELATIVE");
    }

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
