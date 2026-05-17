package de.dlr.shepard.data.timeseries.sql;

import java.util.List;

/**
 * P10a — the compiled output of {@link SqlQueryCompiler}: a parameterised SQL string and the
 * ordered list of values to bind as {@code ?} placeholders.
 *
 * <p>The SQL string contains only structural SQL (keywords, allow-listed column/table names,
 * {@code ?} placeholders). No user-supplied content is ever inlined into the SQL string — all
 * user values travel in {@link #params()}.
 */
public record PreparedStatementSpec(String sql, List<Object> params) {}
