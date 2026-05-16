package de.dlr.shepard.context.references.timeseriesreference.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "TimeseriesReference")
public class TimeseriesReferenceIO extends BasicReferenceIO {

  @NotNull
  @Schema(required = true)
  private long start;

  @NotNull
  @Schema(required = true)
  private long end;

  @NotEmpty
  @Schema(required = true)
  private List<Timeseries> timeseries;

  @NotNull
  @Schema(required = true)
  private long timeseriesContainerId;

  /**
   * AI1c — read-only quality score in {@code [0.0, 1.0]} emitted by
   * the background {@code TimeseriesQualityScoringJob}. {@code null}
   * means "not yet scored" (either the job hasn't run, or the
   * heuristic skipped this ref for lack of data). Clients filter via
   * the standard search shape: e.g. {@code {"property":"qualityScore",
   * "operator":"lt", "value":"0.5"}} finds suspect runs. See
   * {@code aidocs/43 §3.2}.
   */
  @Schema(description = "Background-computed quality score in [0.0, 1.0]. null = not yet scored. Read-only.")
  private Double qualityScore;

  /**
   * AI1c — millisecond epoch of the last quality-score computation.
   * Read-only; surfaced so clients can detect a stale score.
   */
  @Schema(description = "Epoch millis of the last quality-score run. null = never scored. Read-only.")
  private Long lastScoredAt;

  /**
   * TM1 — how timestamps in the referenced timeseries relate to wall clock.
   * "WALL_CLOCK": sample timestamps are already UTC nanoseconds (default).
   * "EXPERIMENT_RELATIVE": sample timestamps are relative to t=0; convert via wallClockOffset.
   * Null on pre-TM1a rows — treat as "WALL_CLOCK".
   */
  @Schema(description = "TM1: timestamp semantics. WALL_CLOCK (default) or EXPERIMENT_RELATIVE. null = legacy row, treat as WALL_CLOCK.")
  private String timeReference;

  /**
   * TM1 — nanoseconds epoch (UTC) of the DAQ's t=0.
   * Only meaningful when timeReference == "EXPERIMENT_RELATIVE".
   */
  @Schema(description = "TM1: UTC nanoseconds of experiment t=0. Only meaningful when timeReference=EXPERIMENT_RELATIVE.")
  private Long wallClockOffset;

  /**
   * TM1 — free-text provenance tag for how wallClockOffset was determined.
   */
  @Schema(description = "TM1: provenance of wallClockOffset (e.g. manual, ffprobe, SA_sync, NTP_marker).")
  private String wallClockOffsetSource;

  public TimeseriesReferenceIO(TimeseriesReference ref) {
    super(ref);
    this.start = ref.getStart();
    this.end = ref.getEnd();
    this.timeseries = ref.getReferencedTimeseriesList().stream().map(entity -> entity.toTimeseries()).toList();
    this.timeseriesContainerId = ref.getTimeseriesContainer() != null ? ref.getTimeseriesContainer().getId() : -1;
    this.qualityScore = ref.getQualityScore();
    this.lastScoredAt = ref.getLastScoredAt();
    this.timeReference = ref.getTimeReference();
    this.wallClockOffset = ref.getWallClockOffset();
    this.wallClockOffsetSource = ref.getWallClockOffsetSource();
  }
}
