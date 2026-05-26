package de.dlr.shepard.data.timeseries.model.enums;

/**
 * Controls which storage tier is used when fetching timeseries data points
 * for chart / LTTB queries (TS-OPT3).
 *
 * <ul>
 *   <li>{@link #AUTO} — the default. Uses the 1-hour continuous aggregate
 *       ({@code timeseries_hourly}) when the window-per-pixel exceeds one hour;
 *       otherwise falls back to the raw-point pre-aggregation path (TS-OPT1).</li>
 *   <li>{@link #RAW} — always queries {@code timeseries_data_points} directly.
 *       Use for high-fidelity zoom or when the CAgg may not yet be populated
 *       (e.g. within the 1-hour refresh lag window).</li>
 *   <li>{@link #CAGG} — always queries the continuous aggregate. The caller accepts
 *       that data in the last ~1 hour may be absent or stale.</li>
 * </ul>
 */
public enum QueryStrategy {
  AUTO,
  RAW,
  CAGG
}
