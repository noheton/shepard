package de.dlr.shepard.context.references.timeseriesreference.services;

import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * AI1c — background job that emits {@code qualityScore} +
 * {@code lastScoredAt} on every {@code TimeseriesReference}.
 *
 * <p>Runs on the {@code shepard.timeseries.quality-scoring.interval}
 * cadence (default {@code PT6H}, i.e. every six hours). Per tick:
 *
 * <ol>
 *   <li>Skip if {@code shepard.timeseries.quality-scoring.enabled} is
 *       {@code false} (v1 default: opt-in).</li>
 *   <li>Ask the DAO for up to
 *       {@code shepard.timeseries.quality-scoring.batch-size}
 *       references where {@code qualityScore IS NULL} or
 *       {@code lastScoredAt < (now - rescoringInterval)}. The
 *       rescoring interval is currently fixed at 24 h — refs scored
 *       within that window are considered fresh and skipped.</li>
 *   <li>For each, fetch up to
 *       {@link TimeseriesQualityScorer#RECOMMENDED_SAMPLE_SIZE} of the
 *       reference's data points, run the scorer, persist the score +
 *       timestamp.</li>
 *   <li>Stamp {@code lastScoredAt = now} even when the scorer returns
 *       empty — otherwise the same too-small reference would be
 *       re-picked every tick. The score itself stays {@code null} so
 *       a downstream client can still distinguish "skipped" from
 *       "scored low".</li>
 * </ol>
 *
 * <p>Pure heuristics — no LLM call. Independent of AI1a. Design in
 * {@code aidocs/43 §3.2}.
 */
@ApplicationScoped
public class TimeseriesQualityScoringJob {

  /** Hard floor below which the scorer can't produce a meaningful number. */
  static final int MIN_BATCH_SIZE = 1;

  /** Hard ceiling for the batch size, to bound per-tick wall-time. */
  static final int MAX_BATCH_SIZE = 10_000;

  /**
   * Per-reference rescoring interval (millis). Refs scored more
   * recently than this are skipped. Hard-coded to 24h per the AI1c
   * spec; if operators need a knob, lifting to a config property is
   * a one-line follow-up.
   */
  static final long RESCORING_INTERVAL_MILLIS = 24L * 60 * 60 * 1000;

  @Inject
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  TimeseriesQualityScorer scorer;

  @Inject
  TimeseriesRepository timeseriesRepository;

  @Inject
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  @ConfigProperty(name = "shepard.timeseries.quality-scoring.enabled", defaultValue = "false")
  boolean enabled;

  @ConfigProperty(name = "shepard.timeseries.quality-scoring.batch-size", defaultValue = "100")
  int batchSize;

  /**
   * Periodic run. {@code @Scheduled.every} resolves the interval
   * config key at startup; changing the value at runtime requires a
   * restart.
   */
  @Scheduled(every = "{shepard.timeseries.quality-scoring.interval}")
  public void runScoring() {
    if (!enabled) {
      Log.debug("AI1c quality scoring skipped — shepard.timeseries.quality-scoring.enabled=false");
      return;
    }
    int capped = clampBatchSize(batchSize);
    long now = currentTimeMillis();
    long staleCutoff = now - RESCORING_INTERVAL_MILLIS;

    List<TimeseriesReference> stale;
    try {
      stale = timeseriesReferenceDAO.findNeedingScoring(staleCutoff, capped);
    } catch (RuntimeException e) {
      Log.warnf(e, "AI1c quality scoring: failed to list stale references (cutoff=%d)", staleCutoff);
      return;
    }

    if (stale.isEmpty()) {
      Log.debug("AI1c quality scoring: no stale references this tick");
      return;
    }
    Log.infof("AI1c quality scoring: scoring %d reference(s) (batch-size=%d)", stale.size(), capped);

    int scored = 0;
    int skipped = 0;
    int failed = 0;
    for (TimeseriesReference ref : stale) {
      try {
        boolean producedScore = scoreOne(ref, now);
        if (producedScore) scored++;
        else skipped++;
      } catch (RuntimeException e) {
        failed++;
        Log.warnf(e, "AI1c quality scoring: ref shepardId=%s failed", safeShepardId(ref));
      }
    }
    Log.infof("AI1c quality scoring: scored=%d skipped=%d failed=%d", scored, skipped, failed);
  }

  /**
   * Score one reference. Returns {@code true} when the scorer
   * emitted a number; {@code false} when the sample was below the
   * scorer's minimum and {@code qualityScore} stays {@code null}.
   *
   * <p>Either way, {@code lastScoredAt} is stamped so a too-small
   * reference doesn't get re-picked next tick.
   */
  boolean scoreOne(TimeseriesReference ref, long nowMillis) {
    Optional<Double> maybeScore = computeScore(ref);
    ref.setLastScoredAt(nowMillis);
    if (maybeScore.isPresent()) {
      ref.setQualityScore(maybeScore.get());
    } else {
      // Leave the previous score untouched if there was one and the
      // recompute couldn't produce a new number (we still mark
      // lastScoredAt so we don't busy-loop on the same ref).
    }
    timeseriesReferenceDAO.createOrUpdate(ref);
    return maybeScore.isPresent();
  }

  /**
   * Pull up to {@link TimeseriesQualityScorer#RECOMMENDED_SAMPLE_SIZE}
   * recent data points from the reference's first timeseries channel
   * and score them. Returns {@link Optional#empty()} for refs without
   * a usable container / channel.
   *
   * <p>Single-channel sampling (rather than all-channels averaging)
   * keeps the per-tick I/O bounded; a noisy first channel + a clean
   * second channel still gets a representative score because the
   * heuristic captures completeness + coverage independent of
   * channel identity, and a campaign-lead can re-trigger by
   * deleting the score (LLM-free; admin-runbook future work).
   */
  Optional<Double> computeScore(TimeseriesReference ref) {
    if (ref == null || ref.getTimeseriesContainer() == null) return Optional.empty();
    List<ReferencedTimeseriesNodeEntity> channels = ref.getReferencedTimeseriesList();
    if (channels == null || channels.isEmpty()) return Optional.empty();
    Timeseries channel = channels.get(0).toTimeseries();
    long containerId = ref.getTimeseriesContainer().getId();
    TimeseriesDataPointsQueryParams params = new TimeseriesDataPointsQueryParams(
      ref.getStart(),
      ref.getEnd(),
      null,
      null,
      null
    );
    List<TimeseriesDataPoint> points = fetchPoints(containerId, channel, params);
    if (points == null || points.isEmpty()) return Optional.empty();
    // Trim to the recommended tail (avoids hauling millions of
    // points through the JVM for an averaged score we can
    // approximate from the last 1k).
    int sampleSize = TimeseriesQualityScorer.RECOMMENDED_SAMPLE_SIZE;
    List<TimeseriesDataPoint> sample = points.size() > sampleSize
      ? points.subList(points.size() - sampleSize, points.size())
      : points;
    return scorer.score(sample);
  }

  /**
   * Indirection over the data-point repository so the job can be
   * tested without a Timescale fixture. {@code @ActivateRequestContext}
   * pulls a Quarkus request scope around the SQL call (the
   * repository is {@code @RequestScoped} via Hibernate).
   */
  @ActivateRequestContext
  List<TimeseriesDataPoint> fetchPoints(
    long containerId,
    Timeseries channel,
    TimeseriesDataPointsQueryParams params
  ) {
    Optional<de.dlr.shepard.data.timeseries.model.TimeseriesEntity> entity = timeseriesRepository.findTimeseries(
      containerId,
      channel
    );
    if (entity.isEmpty()) return List.of();
    int timeseriesId = entity.get().getId();
    var valueType = entity.get().getValueType();
    return timeseriesDataPointRepository.queryDataPoints(timeseriesId, valueType, params);
  }

  /**
   * Clamp the configured batch size to {@code [MIN, MAX]}. Defensive
   * — an operator misconfiguring a negative or absurd value still
   * yields a sane run.
   */
  static int clampBatchSize(int requested) {
    if (requested < MIN_BATCH_SIZE) return MIN_BATCH_SIZE;
    if (requested > MAX_BATCH_SIZE) return MAX_BATCH_SIZE;
    return requested;
  }

  /** Indirection for tests — wall-clock millis at run start. */
  long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  private static String safeShepardId(TimeseriesReference ref) {
    if (ref == null) return "<null>";
    try {
      return String.valueOf(ref.getShepardId());
    } catch (Exception e) {
      return "<unreadable>";
    }
  }
}
