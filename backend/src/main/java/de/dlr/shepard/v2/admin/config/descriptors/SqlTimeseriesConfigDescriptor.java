package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import de.dlr.shepard.v2.admin.sqltimeseries.io.SqlTimeseriesConfigIO;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;

/**
 * V2CONV-A4 — {@link ConfigDescriptor} for the SQL timeseries config singleton,
 * exposed as {@code GET|PATCH /v2/admin/config/sql-timeseries}. Replaces the
 * bespoke {@code SqlTimeseriesConfigRest} (deleted). Delegates unchanged to
 * {@link SqlTimeseriesConfigService}.
 *
 * <p>Patchable fields: {@code maxRows} (Long, &gt; 0), {@code maxDuration}
 * (ISO-8601 duration string). RFC-7396 semantics: absent = leave alone, null =
 * clear (revert to deploy-time default), value = replace.
 */
@ApplicationScoped
public class SqlTimeseriesConfigDescriptor implements ConfigDescriptor<SqlTimeseriesConfigIO> {

  static final String PROBLEM_TYPE_INVALID_MAX_ROWS = "/problems/sql-timeseries.config.invalid-max-rows";
  static final String PROBLEM_TYPE_INVALID_MAX_DURATION = "/problems/sql-timeseries.config.invalid-max-duration";

  @Inject
  SqlTimeseriesConfigService service;

  @Override
  public String featureName() {
    return "sql-timeseries";
  }

  @Override
  public String description() {
    return "SQL-over-timeseries endpoint toggle and query caps: enabled, maxRows, maxDuration.";
  }

  @Override
  public SqlTimeseriesConfigIO currentShape() {
    return toIO(service.current());
  }

  @Override
  public SqlTimeseriesConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    SqlTimeseriesConfig current = service.current();

    boolean enabledTouched = patch.has("enabled");
    boolean maxRowsTouched = patch.has("maxRows");
    boolean maxDurationTouched = patch.has("maxDuration");

    Boolean effectiveEnabled = enabledTouched ? boolOrNull(patch.get("enabled")) : current.getEnabled();
    Long effectiveMaxRows = maxRowsTouched ? longOrNull(patch.get("maxRows")) : current.getMaxRows();
    String effectiveMaxDuration = maxDurationTouched ? textOrNull(patch.get("maxDuration")) : current.getMaxDurationIso();

    if (maxRowsTouched && effectiveMaxRows != null && effectiveMaxRows <= 0) {
      Log.warnf("V2CONV-A4/sql-timeseries: rejected PATCH — invalid maxRows %d (must be > 0)", effectiveMaxRows);
      throw ConfigPatchException.badRequest(
        PROBLEM_TYPE_INVALID_MAX_ROWS,
        "Invalid maxRows",
        "maxRows must be greater than 0. Set to null to revert to the deploy-time default " +
        "(shepard.timeseries.sql.max-rows). Current deploy-time default: " + service.getDefaultMaxRows() + "."
      );
    }

    if (maxDurationTouched && effectiveMaxDuration != null) {
      try {
        Duration.parse(effectiveMaxDuration);
      } catch (Exception e) {
        Log.warnf("V2CONV-A4/sql-timeseries: rejected PATCH — invalid maxDuration '%s' (not ISO-8601)", effectiveMaxDuration);
        throw ConfigPatchException.badRequest(
          PROBLEM_TYPE_INVALID_MAX_DURATION,
          "Invalid maxDuration",
          "maxDuration must be a valid ISO-8601 duration (e.g. 'PT60S', 'PT2M30S', 'PT1H'). " +
          "Set to null to revert to the deploy-time default " +
          "(shepard.timeseries.sql.max-duration). Current deploy-time default: " +
          service.getDefaultMaxDuration() + "."
        );
      }
    }

    SqlTimeseriesConfig saved = service.patch(effectiveEnabled, effectiveMaxRows, effectiveMaxDuration);
    return toIO(saved);
  }

  private SqlTimeseriesConfigIO toIO(SqlTimeseriesConfig cfg) {
    return SqlTimeseriesConfigIO.from(cfg, service.getDefaultEnabled(), service.getDefaultMaxRows(), service.getDefaultMaxDuration());
  }

  private static Boolean boolOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asBoolean();
  }

  private static String textOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }

  private static Long longOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asLong();
  }
}
