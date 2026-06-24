package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.qualityscoring.io.TimeseriesQualityScoringConfigIO;
import de.dlr.shepard.v2.admin.qualityscoring.services.TimeseriesQualityScoringConfigService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * FTOGGLE-QS-1 — {@link ConfigDescriptor} for the AI1c quality-scoring
 * runtime config, exposed via
 * {@code GET|PATCH /v2/admin/config/timeseries-quality-scoring}.
 *
 * <p>Makes {@code shepard.timeseries.quality-scoring.enabled} and
 * {@code shepard.timeseries.quality-scoring.batch-size} runtime-mutable
 * without a restart. The scheduling interval is intentionally excluded —
 * Quarkus evaluates {@code @Scheduled.every} at startup so it cannot be
 * changed at runtime (CLAUDE.md "Pre-startup ordering invariants" exception).
 *
 * <p>Patchable fields:
 * <ul>
 *   <li>{@code enabled} (boolean) — absent = leave alone, null = revert to
 *       deploy-time default (false), value = replace.</li>
 *   <li>{@code batchSize} (int) — absent = leave alone, null = revert to
 *       deploy-time default (100), value = replace. Must be ≥ 1.</li>
 * </ul>
 */
@ApplicationScoped
public class TimeseriesQualityScoringConfigDescriptor
  implements ConfigDescriptor<TimeseriesQualityScoringConfigIO> {

  static final String PROBLEM_TYPE_INVALID_BATCH_SIZE =
    "/problems/timeseries-quality-scoring.invalid-batch-size";

  @Inject
  TimeseriesQualityScoringConfigService service;

  @Override
  public String featureName() {
    return "timeseries-quality-scoring";
  }

  @Override
  public String description() {
    return "AI1c timeseries quality-scoring job runtime config: enabled flag and per-tick batch size.";
  }

  @Override
  public TimeseriesQualityScoringConfigIO currentShape() {
    return service.getConfig();
  }

  @Override
  public TimeseriesQualityScoringConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    var current = service.current();

    Boolean effectiveEnabled = patch.has("enabled")
      ? (patch.get("enabled").isNull() ? null : patch.get("enabled").booleanValue())
      : current.getEnabled();

    Integer effectiveBatchSize;
    if (patch.has("batchSize")) {
      if (patch.get("batchSize").isNull()) {
        effectiveBatchSize = null;
      } else {
        effectiveBatchSize = patch.get("batchSize").intValue();
        if (effectiveBatchSize < 1) {
          throw ConfigPatchException.badRequest(
            PROBLEM_TYPE_INVALID_BATCH_SIZE,
            "Invalid batchSize",
            "batchSize must be ≥ 1, got: " + effectiveBatchSize);
        }
      }
    } else {
      effectiveBatchSize = current.getBatchSize();
    }

    TimeseriesQualityScoringConfigIO updated = service.patch(effectiveEnabled, effectiveBatchSize);
    Log.infof(
      "FTOGGLE-QS-1: PATCH /v2/admin/config/timeseries-quality-scoring "
        + "→ enabled=%s batchSize=%s",
      updated.enabled(), updated.batchSize());
    return updated;
  }
}
