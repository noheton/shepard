package de.dlr.shepard.data.timeseries.sql;

/**
 * P10a — allow-list of columns in {@code timeseries_data_points} addressable via the JSON DSL.
 *
 * <p>User-supplied column names are never written into SQL directly; they are validated here
 * and only the enum's {@link #sqlName()} is emitted. See
 * {@code aidocs/platform/29-p10-implementation-design.md §3}.
 */
public enum AllowedColumn {
  TIMESERIES_ID("timeseries_id"),
  TIME("time"),
  VALUE_DOUBLE("value_double"),
  VALUE_LONG("value_long"),
  VALUE_STRING("value_string"),
  VALUE_BOOLEAN("value_boolean");

  private final String sqlName;

  AllowedColumn(String sqlName) {
    this.sqlName = sqlName;
  }

  public String sqlName() {
    return sqlName;
  }

  /**
   * Resolves a DSL column name to the corresponding {@link AllowedColumn}.
   *
   * @throws IllegalArgumentException if {@code name} is not an allow-listed column
   */
  public static AllowedColumn fromDsl(String name) {
    for (AllowedColumn c : values()) {
      if (c.sqlName.equals(name)) {
        return c;
      }
    }
    throw new IllegalArgumentException("Column not allowed: " + name);
  }

  /**
   * Resolves an alias string to a known column, for use in ORDER BY validation when the ORDER BY
   * column may refer to an alias defined in SELECT or GROUP BY.
   *
   * <p>Note: aliases are query-local, not enum-carried. This method tries {@link #fromDsl} so that
   * ordering by a real column name still works. Ordering by an alias is validated by the compiler
   * against the alias map it builds; this method is a fallback for the non-alias case.
   *
   * @throws IllegalArgumentException if {@code alias} is not a valid column name
   */
  public static AllowedColumn fromAlias(String alias) {
    return fromDsl(alias);
  }

  /**
   * Aggregation functions allowed in {@code select.agg} entries.
   */
  public enum AggFunction {
    AVG("AVG"),
    MIN("MIN"),
    MAX("MAX"),
    SUM("SUM"),
    COUNT("COUNT"),
    FIRST("first"),
    LAST("last");

    private final String sqlName;

    AggFunction(String sqlName) {
      this.sqlName = sqlName;
    }

    public String sqlName() {
      return sqlName;
    }

    /**
     * Resolves a DSL aggregation name (case-insensitive) to the corresponding {@link AggFunction}.
     *
     * @throws IllegalArgumentException if the name is not allow-listed
     */
    public static AggFunction fromDsl(String name) {
      if (name == null) {
        throw new IllegalArgumentException("Aggregation function name must not be null");
      }
      for (AggFunction f : values()) {
        if (f.name().equalsIgnoreCase(name)) {
          return f;
        }
      }
      throw new IllegalArgumentException("Aggregation function not allowed: " + name);
    }
  }
}
