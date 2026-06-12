package de.dlr.shepard.v2.svdx.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.plugin.fileformat.svdx.TcScopeCsvParser;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Glue layer for {@code MFFD-PLUGIN-SVDX-CSV-INGEST-1}.
 *
 * <p>V2CONV-A7 — formerly the collaborator of the bespoke
 * {@code POST /v2/svdx/ingest} REST resource, this service is now the collaborator
 * of {@link de.dlr.shepard.v2.svdx.transform.SvdxCsvTransformExecutor}. The
 * Tier-3 top-level {@code /v2/svdx} namespace was dissolved onto the generic
 * MAPPING_RECIPE / {@code TransformExecutor} seam (aidocs/platform/191), exactly
 * mirroring the KRL (V2CONV-B5) and URScript dissolutions. The ingestion logic
 * below is unchanged — only its entry point moved from a JAX-RS handler to a
 * ServiceLoader executor that resolves this {@code @ApplicationScoped} bean via
 * {@code CDI.current()} at materialize time.
 *
 * <p>The pure-Java {@link TcScopeCsvParser} (in the
 * {@code shepard-plugin-fileformat-svdx} module) owns the file-format
 * knowledge. This service owns the persistence + auth + CDI wiring:
 * resolves the two FileReferences (SVDX + CSV) by appId via
 * {@link SingletonFileReferenceService}, asserts Write permission on
 * the parent DataObject, hands the CSV bytes to the parser, mints a
 * {@link TimeseriesContainer} when none is supplied, and streams the
 * parsed channels into TimescaleDB via the existing
 * {@link TimeseriesService#saveDataPoints} path — no new database
 * write path, no schema migration.
 *
 * <p>The 5-tuple {@link Timeseries} identity for the rows is built
 * from the SVDX/CSV metadata as follows:
 *
 * <ul>
 *   <li>{@code measurement} — the SVDX project name (falls back to
 *       a stable {@code "svdx-ingest-<csvAppId>"} when absent).</li>
 *   <li>{@code device} — the channel's {@code NetID} (TwinCAT
 *       AmsNetId), e.g. {@code 169.254.165.182.1.1}.</li>
 *   <li>{@code location} — the channel's ADS {@code Port} (e.g.
 *       {@code 851}). When the CSV did not record a port, falls back
 *       to {@code "ads"} so the 5-tuple stays non-blank.</li>
 *   <li>{@code symbolicName} — the fully qualified TwinCAT
 *       {@code SymbolName} ({@code RobotData.rRoboPosA}). Falls back
 *       to the channel display name when the CSV omits it.</li>
 *   <li>{@code field} — the channel's short display name
 *       ({@code rRoboPosA}). Falls back to a synthetic
 *       {@code "ch<index>"} when no name is present (rare).</li>
 * </ul>
 *
 * <p>This mapping survives re-ingest of the same campaign data: same
 * project + same symbol resolves to the same channel row, so
 * additional rows append rather than fork into duplicate channels.
 * The {@code measurement}-as-project ties channels to the campaign
 * file, so unrelated projects don't collide on shared symbol names.
 *
 * <p>Idempotency: the reference name is deterministically built from
 * the SVDX + CSV appIds ({@code svdx-ingest:<svdxId>+<csvId>}). When a
 * TimeseriesReference with that exact name already exists on the
 * DataObject, the call short-circuits and returns the existing
 * reference with {@code idempotentReplay=true}. This is the cheap
 * detection path that avoids both duplicate channels and duplicate
 * data rows.
 */
@ApplicationScoped
public class SvdxCsvIngestionService {

  /** Stable prefix used in the deterministic reference name; bumping breaks idempotency. */
  public static final String REF_NAME_PREFIX = "svdx-ingest:";

  @Inject SingletonFileReferenceService singletonService;
  @Inject DataObjectDAO dataObjectDAO;
  @Inject PermissionsService permissionsService;
  @Inject TimeseriesService timeseriesService;
  @Inject TimeseriesContainerService timeseriesContainerService;
  @Inject TimeseriesReferenceService timeseriesReferenceService;

  /**
   * Run the full ingest flow. Caller is expected to have authenticated;
   * the {@code callerUsername} parameter is propagated to the
   * {@link PermissionsService} write-gate check.
   *
   * @throws InvalidPathException 404-equivalent — DataObject or
   *     one of the FileReferences not found.
   * @throws InvalidBodyException 400-equivalent — files don't belong to
   *     the named DataObject, CSV can't be parsed, etc.
   * @throws SecurityException 403-equivalent — caller lacks Write on
   *     the parent DataObject.
   */
  public SvdxCsvIngestResult ingest(SvdxCsvIngestParams req, String callerUsername) {
    if (req == null) throw new InvalidBodyException("request body required");
    if (req.svdxFileAppId() == null || req.svdxFileAppId().isBlank())
      throw new InvalidBodyException("svdxFileAppId required");
    if (req.csvFileAppId() == null || req.csvFileAppId().isBlank())
      throw new InvalidBodyException("csvFileAppId required");
    if (req.dataObjectAppId() == null || req.dataObjectAppId().isBlank())
      throw new InvalidBodyException("dataObjectAppId required");

    // ── resolve DataObject ──
    DataObject dataObject = dataObjectDAO.findByAppId(req.dataObjectAppId());
    if (dataObject == null || dataObject.isDeleted()) {
      throw new InvalidPathException("DataObject '" + req.dataObjectAppId() + "' not found");
    }
    long dataObjectShepardId = dataObject.getId();

    // ── auth gate (Write on parent DataObject) ──
    if (!permissionsService.isAccessTypeAllowedForUser(dataObjectShepardId, AccessType.Write, callerUsername)) {
      throw new SecurityException("caller lacks Write on DataObject " + req.dataObjectAppId());
    }

    // ── resolve both FileReferences ──
    FileReference svdxFile = singletonService.getByAppId(req.svdxFileAppId());
    FileReference csvFile = singletonService.getByAppId(req.csvFileAppId());
    if (svdxFile == null || svdxFile.isDeleted())
      throw new InvalidPathException("SVDX FileReference '" + req.svdxFileAppId() + "' not found");
    if (csvFile == null || csvFile.isDeleted())
      throw new InvalidPathException("CSV FileReference '" + req.csvFileAppId() + "' not found");

    // Sanity check: both refs must belong to the named DataObject.
    Long svdxParent = singletonService.getDataObjectOgmId(req.svdxFileAppId());
    Long csvParent = singletonService.getDataObjectOgmId(req.csvFileAppId());
    if (svdxParent == null || svdxParent.longValue() != dataObjectShepardId
        || csvParent == null || csvParent.longValue() != dataObjectShepardId) {
      throw new InvalidBodyException(
          "Both svdxFileAppId and csvFileAppId must be attached to the named dataObjectAppId");
    }

    long collectionShepardId = resolveCollectionId(dataObject);

    // ── idempotency probe — deterministic name on the DataObject ──
    String refName = (req.referenceName() != null && !req.referenceName().isBlank())
        ? req.referenceName()
        : (REF_NAME_PREFIX + req.svdxFileAppId() + "+" + req.csvFileAppId());

    TimeseriesReference existing = findExistingReference(collectionShepardId, dataObjectShepardId, refName);
    if (existing != null) {
      Log.infof("SVDX ingest replay short-circuit: ref '%s' already exists on DO %s",
          refName, req.dataObjectAppId());
      return buildResponse(existing, /*idempotent*/ true, /*unmatched*/ 0);
    }

    // ── pull CSV bytes ──
    TcScopeCsvParser.ParsedScopeCsv parsed;
    NamedInputStream nis = singletonService.getPayload(req.csvFileAppId());
    if (nis == null || nis.getInputStream() == null) {
      throw new InvalidBodyException("CSV payload for '" + req.csvFileAppId() + "' is empty");
    }
    try {
      parsed = TcScopeCsvParser.parse(nis.getInputStream());
    } catch (TcScopeCsvParser.CsvParseException cpe) {
      throw new InvalidBodyException("CSV parse failed: " + cpe.getMessage());
    } finally {
      try { nis.getInputStream().close(); } catch (IOException ignored) { /* best-effort */ }
    }

    // ── target container ──
    TimeseriesContainer container = resolveOrMintContainer(req.tsContainerAppId(), refName);
    long containerShepardId = container.getId();

    String projectName = parsed.projectName().orElse("svdx-ingest-" + req.csvFileAppId());

    // ── pre-flight: build channel list, count unmatched against manifest names (best-effort) ──
    Map<String, Timeseries> tupleByChannel = new LinkedHashMap<>();
    long minTs = Long.MAX_VALUE;
    long maxTs = Long.MIN_VALUE;
    int realRows = 0;
    int unmatched = 0;

    for (int i = 0; i < parsed.channels().size(); i++) {
      TcScopeCsvParser.Channel ch = parsed.channels().get(i);
      String field = (ch.name() != null && !ch.name().isBlank()) ? ch.name() : ("ch" + i);
      String symbolic = (ch.symbolName() != null && !ch.symbolName().isBlank()) ? ch.symbolName() : field;
      String device = (ch.netId() != null && !ch.netId().isBlank()) ? ch.netId() : "ads";
      String location = (ch.port() != null && !ch.port().isBlank()) ? ch.port() : "ads";

      // best-effort: a channel is "matched" if its name is non-blank and looks like
      // it came from the export-tool (i.e. carries SymbolName or NetID metadata).
      if (symbolic.equals(field) && (ch.netId() == null || ch.netId().isBlank())) {
        unmatched++;
      }

      Timeseries tuple = new Timeseries(projectName, device, location, symbolic, field);
      tupleByChannel.put(field + "::" + i, tuple);

      // collect data points
      int sampleTimeMs = ch.sampleTimeMs() > 0 ? ch.sampleTimeMs() : 1;
      List<TimeseriesDataPoint> points = new ArrayList<>(ch.sampleCount());
      for (int s = 0; s < ch.values().size(); s++) {
        Object v = ch.values().get(s);
        if (v == null) continue; // null cells skipped
        long ts = parsed.sampleTimestampNs(s, sampleTimeMs);
        if (ts < minTs) minTs = ts;
        if (ts > maxTs) maxTs = ts;
        points.add(new TimeseriesDataPoint(ts, v));
      }
      realRows = Math.max(realRows, points.size());

      if (!points.isEmpty()) {
        try {
          timeseriesService.saveDataPoints(containerShepardId, tuple, points);
        } catch (RuntimeException ex) {
          // ON CONFLICT-equivalent: TimeseriesService throws on duplicate
          // (timeseries_id, time) pairs. We tolerate that — same row
          // already there means the ingest is partially-replayed; log
          // and continue with the next channel.
          String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
          if (msg.contains("duplicate") || msg.contains("unique")) {
            Log.warnf("SVDX ingest: duplicate rows on channel %s — partial replay tolerated", field);
          } else {
            throw ex;
          }
        }
      }
    }

    if (tupleByChannel.isEmpty()) {
      // empty CSV — still mint a zero-channel reference so the operator
      // sees the call landed.
      minTs = parsed.startTimeFileTime() == 0
          ? 0
          : TcScopeCsvParser.fileTimeToUnixNanos(parsed.startTimeFileTime());
      maxTs = minTs;
    }

    // ── create TimeseriesReference ──
    TimeseriesReferenceIO tio = new TimeseriesReferenceIO();
    tio.setName(refName);
    tio.setStart(minTs == Long.MAX_VALUE ? 0L : minTs);
    tio.setEnd(maxTs == Long.MIN_VALUE ? 0L : maxTs);
    tio.setTimeseriesContainerId(containerShepardId);
    tio.setTimeseries(new ArrayList<>(tupleByChannel.values()));

    TimeseriesReference created;
    if (tupleByChannel.isEmpty()) {
      // The reference service requires @NotEmpty timeseries; for the
      // empty-CSV path we synthesise a placeholder channel so the
      // operator gets a real ref back. Marked via the field name.
      List<Timeseries> placeholder = new ArrayList<>(1);
      placeholder.add(new Timeseries(projectName, "ads", "ads", "_empty", "_empty"));
      tio.setTimeseries(placeholder);
    }
    created = timeseriesReferenceService.createReference(collectionShepardId, dataObjectShepardId, tio);

    Log.infof("SVDX ingest: minted TimeseriesReference '%s' (shepardId=%d, %d channels, %d rows max) on DO %s",
        refName, created.getId(), tupleByChannel.size(), realRows, req.dataObjectAppId());

    return buildResponse(created, /*idempotent*/ false, unmatched);
  }

  // ────────────────────────────────────────────────────────────── helpers

  private TimeseriesContainer resolveOrMintContainer(String containerAppId, String refName) {
    if (containerAppId != null && !containerAppId.isBlank()) {
      return timeseriesContainerService.getContainerByAppId(containerAppId);
    }
    TimeseriesContainerIO ioReq = new TimeseriesContainerIO();
    ioReq.setName(refName + "-container");
    return timeseriesContainerService.createContainer(ioReq);
  }

  private TimeseriesReference findExistingReference(long collectionShepardId, long dataObjectShepardId, String name) {
    try {
      List<TimeseriesReference> refs = timeseriesReferenceService.getAllReferencesByDataObjectId(
          collectionShepardId, dataObjectShepardId, null);
      if (refs == null) return null;
      for (TimeseriesReference r : refs) {
        if (r == null || r.isDeleted()) continue;
        if (name.equals(r.getName())) return r;
      }
    } catch (RuntimeException ex) {
      // Fail-soft: idempotency probe must not block ingest.
      Log.debugf(ex, "SVDX ingest: idempotency probe failed for DO %d", dataObjectShepardId);
    }
    return null;
  }

  private long resolveCollectionId(DataObject dataObject) {
    if (dataObject.getCollection() == null) {
      throw new InvalidPathException("DataObject '" + dataObject.getAppId() + "' is orphaned (no Collection)");
    }
    return dataObject.getCollection().getId();
  }

  private SvdxCsvIngestResult buildResponse(TimeseriesReference ref, boolean idempotent, int unmatchedCount) {
    String containerAppId = ref.getTimeseriesContainer() != null ? ref.getTimeseriesContainer().getAppId() : null;
    int channelCount = ref.getReferencedTimeseriesList() == null ? 0 : ref.getReferencedTimeseriesList().size();
    long span = ref.getEnd() - ref.getStart();
    int rowCount = span <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, span / 1_000_000L + 1L);
    return new SvdxCsvIngestResult(
        ref.getAppId(),
        ref.getId(),
        containerAppId,
        channelCount,
        rowCount,
        unmatchedCount,
        idempotent);
  }
}
