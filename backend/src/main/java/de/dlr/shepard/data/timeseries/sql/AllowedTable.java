package de.dlr.shepard.data.timeseries.sql;

/**
 * P10a — allow-list of TimescaleDB tables/views addressable via the JSON DSL.
 *
 * <p>Column / table names are never taken from user input raw — they are looked up here
 * and the enum's {@link #sqlName()} is the only string ever written into the SQL template.
 * Adding a new table requires a code-review (and usually a migration); see
 * {@code aidocs/platform/29-p10-implementation-design.md §3}.
 */
public enum AllowedTable {
  TIMESERIES_DATA_POINTS("timeseries_data_points");

  private final String sqlName;

  AllowedTable(String sqlName) {
    this.sqlName = sqlName;
  }

  public String sqlName() {
    return sqlName;
  }

  /**
   * Resolves a DSL {@code from} string to the corresponding {@link AllowedTable}.
   *
   * @throws IllegalArgumentException if {@code name} is not an allow-listed table
   */
  public static AllowedTable fromDsl(String name) {
    for (AllowedTable t : values()) {
      if (t.sqlName.equals(name)) {
        return t;
      }
    }
    throw new IllegalArgumentException("Table not allowed: " + name);
  }
}
