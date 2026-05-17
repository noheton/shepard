package de.dlr.shepard.common.configuration.feature.toggles;

/**
 * P10a — feature toggle for the SQL-over-HTTP timeseries endpoint
 * ({@code aidocs/platform/29-p10-implementation-design.md §11}).
 *
 * <p>Default OFF. When OFF, {@code POST /v2/sql/timeseries} returns 404 immediately.
 * Operators flip the toggle on once they have reviewed the endpoint's permission model and
 * are ready to expose bulk timeseries read access — see {@code docs/reference/sql-timeseries.md}.
 *
 * <p>Phase P10a ships JSON-only output. CSV and NDJSON content negotiation land in P10b.
 * The flag default will flip to {@code true} in P10c once streaming caps and integration
 * tests are green.
 *
 * <p>Mirrors the shape of {@link HdfFeatureToggle}.
 */
public class SqlTimeseriesFeatureToggle {

  private static final String SQL_TIMESERIES_ENABLED_PROPERTY = "shepard.timeseries.sql.enabled";

  private SqlTimeseriesFeatureToggle() {
    // utility class — no instances
  }

  public static boolean isActive() {
    return TogglePropertyUtil.isToggleEnabled(SQL_TIMESERIES_ENABLED_PROPERTY);
  }
}
