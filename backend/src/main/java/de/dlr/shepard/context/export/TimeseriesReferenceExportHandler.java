package de.dlr.shepard.context.export;

import de.dlr.shepard.common.exceptions.ShepardException;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Built-in {@link PayloadExportHandler} for {@code TimeseriesReference} rows.
 *
 * <p>Writes the reference JSON sidecar and, when accessible, the timeseries CSV payload.
 * Supports per-payload column and time-range selection from {@link ExportContext}.
 */
@ApplicationScoped
public class TimeseriesReferenceExportHandler implements PayloadExportHandler {

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Override
  public boolean handles(String referenceType) {
    return "TimeseriesReference".equals(referenceType);
  }

  @Override
  public void export(ExportBuilder builder, BasicReference reference, ExportContext ctx) throws IOException {
    var tsRef = timeseriesReferenceService.getReference(
      ctx.collectionId(),
      ctx.dataObjectId(),
      reference.getShepardId(),
      null
    );
    builder.addReference(new TimeseriesReferenceIO(tsRef), tsRef.getCreatedBy());

    Set<String> requestedColumns = ctx.perPayload() != null && ctx.perPayload().columns() != null
      ? new LinkedHashSet<>(ctx.perPayload().columns())
      : Collections.emptySet();
    Long startNanosOverride = null;
    Long endNanosOverride = null;
    if (ctx.perPayload() != null && ctx.perPayload().timeRange() != null) {
      var tr = ctx.perPayload().timeRange();
      if (tr.start() != null) startNanosOverride = nanosFromInstant(tr.start());
      if (tr.end() != null) endNanosOverride = nanosFromInstant(tr.end());
    }

    Set<String> effectiveFieldFilter = Collections.emptySet();
    if (!requestedColumns.isEmpty()) {
      Set<String> knownFields = new HashSet<>();
      for (var ts : tsRef.getReferencedTimeseriesList()) {
        var f = ts.toTimeseries().getField();
        if (f != null) knownFields.add(f);
      }
      Set<String> unknown = new LinkedHashSet<>();
      Set<String> matched = new LinkedHashSet<>();
      for (String c : requestedColumns) {
        if (knownFields.contains(c)) matched.add(c);
        else unknown.add(c);
      }
      if (!unknown.isEmpty()) {
        if (ctx.strict()) {
          throw new BadRequestException(
            "Unknown column(s) for TimeseriesReference id=" + reference.getShepardId() + ": " + unknown
          );
        }
        builder.addSelectionWarning(
          "TimeseriesReference id=" + reference.getShepardId() + ": unknown columns skipped " + unknown
        );
      }
      effectiveFieldFilter = matched;
    }

    InputStream timeseriesPayload = null;
    try {
      timeseriesPayload = timeseriesReferenceService.exportReferencedTimeseriesByShepardId(
        ctx.collectionId(),
        ctx.dataObjectId(),
        reference.getShepardId(),
        null,
        null,
        null,
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        effectiveFieldFilter,
        CsvFormat.ROW,
        startNanosOverride,
        endNanosOverride
      );
    } catch (ShepardException e) {
      Log.warn("Cannot access timeseries payload during export");
    }
    if (timeseriesPayload != null) {
      writeTimeseriesPayload(builder, timeseriesPayload, tsRef);
    }
  }

  private static void writeTimeseriesPayload(
    ExportBuilder builder,
    InputStream payload,
    TimeseriesReference reference
  ) throws IOException {
    var filename = reference.getUniqueId() + ExportConstants.CSV_FILE_EXTENSION;
    builder.addPayload(payload.readAllBytes(), filename, reference.getName(), "text/csv");
  }

  private static long nanosFromInstant(java.time.Instant instant) {
    return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
  }
}
