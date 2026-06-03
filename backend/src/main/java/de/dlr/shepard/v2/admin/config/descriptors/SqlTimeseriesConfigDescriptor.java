package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.ConfigRegistry;
import de.dlr.shepard.v2.admin.config.ConfigValidationException;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import de.dlr.shepard.v2.admin.sqltimeseries.io.SqlTimeseriesConfigIO;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Duration;

/**
 * V2CONV-A4 — {@link ConfigDescriptor} for the SQL-timeseries config singleton.
 *
 * <p>Registers under feature name {@code "sql-timeseries"} so
 * {@code GET|PATCH /v2/admin/config/sql-timeseries} dispatches here.
 * Mirrors the validation logic in {@code SqlTimeseriesConfigRest}.
 */
@ApplicationScoped
public class SqlTimeseriesConfigDescriptor implements ConfigDescriptor {

  static final String FEATURE = "sql-timeseries";
  static final String PROBLEM_MAX_ROWS = "/problems/sql-timeseries.config.invalid-max-rows";
  static final String PROBLEM_MAX_DURATION = "/problems/sql-timeseries.config.invalid-max-duration";

  @Inject
  SqlTimeseriesConfigService service;

  void onStart(@Observes StartupEvent event, ConfigRegistry registry) {
    registry.register(this);
  }

  @Override
  public String featureName() {
    return FEATURE;
  }

  @Override
  public Object read() {
    return SqlTimeseriesConfigIO.from(
      service.current(),
      service.getDefaultMaxRows(),
      service.getDefaultMaxDuration()
    );
  }

  @Override
  public Object patch(JsonNode node) throws ConfigValidationException {
    SqlTimeseriesConfig current = service.current();
    Long effectiveMaxRows = current.getMaxRows();
    String effectiveMaxDuration = current.getMaxDurationIso();

    if (node != null && node.has("maxRows")) {
      JsonNode v = node.get("maxRows");
      if (v.isNull()) {
        effectiveMaxRows = null;
      } else {
        long val = v.asLong();
        if (val <= 0) {
          throw new ConfigValidationException(
            PROBLEM_MAX_ROWS,
            "Invalid maxRows",
            "maxRows must be greater than 0. Set to null to revert to the deploy-time default (" +
              service.getDefaultMaxRows() + ")."
          );
        }
        effectiveMaxRows = val;
      }
    }

    if (node != null && node.has("maxDuration")) {
      JsonNode v = node.get("maxDuration");
      if (v.isNull()) {
        effectiveMaxDuration = null;
      } else {
        String val = v.asText();
        try {
          Duration.parse(val);
        } catch (Exception e) {
          throw new ConfigValidationException(
            PROBLEM_MAX_DURATION,
            "Invalid maxDuration",
            "maxDuration must be a valid ISO-8601 duration (e.g. PT60S, PT2M30S, PT1H). " +
              "Set to null to revert to the deploy-time default (" + service.getDefaultMaxDuration() + ")."
          );
        }
        effectiveMaxDuration = val;
      }
    }

    SqlTimeseriesConfig saved = service.patch(effectiveMaxRows, effectiveMaxDuration);
    return SqlTimeseriesConfigIO.from(saved, service.getDefaultMaxRows(), service.getDefaultMaxDuration());
  }
}
