package de.dlr.shepard.data.spatialdata.repositories;

import de.dlr.shepard.common.util.JsonConverter;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.annotation.Timed;
import io.quarkus.agroal.DataSource;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

/**
 * Repository for the v6 {@code shepard_spatial.profile} hypertable (SPATIAL-V6-002).
 *
 * <p>Write strategy:
 * <ul>
 *   <li>Batches {@code > 1000} rows use the PostgreSQL COPY protocol — zero
 *       SQL injection risk, high throughput for large AFP/NDT sweeps.
 *   <li>Batches {@code ≤ 1000} rows use parameterised single-row native INSERTs
 *       — simpler transaction handling for interactive/incremental writes.
 * </ul>
 *
 * <p>This class ships preemptively so no future developer is tempted to replicate
 * the TS-AUDIT-002 antipattern ({@code String.format} with literal values) that
 * lives in the V1 {@link SpatialDataPointRepository}.
 */
@RequestScoped
public class SpatialProfileRepository {

    /** Batches larger than this threshold use COPY; smaller use parameterised INSERT. */
    static final int COPY_THRESHOLD = 1000;

    private static final String COPY_SQL =
        "COPY shepard_spatial.profile " +
        "(container_id, time, profile_kind, anchor, profile, measurements, metadata, orientation) " +
        "FROM STDIN WITH (FORMAT csv, DELIMITER ',', NULL '\\N', QUOTE '\"')";

    private static final String INSERT_SQL =
        "INSERT INTO shepard_spatial.profile " +
        "(container_id, time, profile_kind, anchor, profile, measurements, metadata, orientation) " +
        "VALUES (?1, ?2, ?3, ST_GeomFromEWKT(?4), " +
        "CASE WHEN ?5::text IS NULL THEN NULL ELSE ST_GeomFromEWKT(?5) END, " +
        "CAST(?6 AS JSONB), CAST(?7 AS JSONB), CAST(?8 AS JSONB))";

    @Inject
    @DataSource("spatial")
    AgroalDataSource spatialDataSource;

    @PersistenceUnit("spatial")
    EntityManager entityManager;

    /**
     * Insert a batch of profile rows into {@code shepard_spatial.profile}.
     *
     * <p>Dispatches to the COPY path when {@code rows.size() > 1000};
     * otherwise uses individual parameterised INSERTs.
     *
     * @param rows rows to insert; must not be null
     * @throws SQLException   propagated from the COPY path on DB error
     * @throws RuntimeException wraps {@link IOException} from the COPY stream
     */
    @Timed(value = "shepard.spatial-profile.insert-many")
    public void insertMany(List<ProfileRow> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (rows.size() > COPY_THRESHOLD) {
            insertWithCopy(rows);
        } else {
            insertWithParameters(rows);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // COPY path (> 1000 rows)
    // ─────────────────────────────────────────────────────────────────────

    private void insertWithCopy(List<ProfileRow> rows) throws SQLException {
        var sb = new StringBuilder();
        for (ProfileRow row : rows) {
            appendCsvRow(sb, row);
        }

        try (Connection conn = spatialDataSource.getConnection()) {
            PGConnection pgConn = conn.unwrap(PGConnection.class);
            CopyManager copyManager = pgConn.getCopyAPI();
            InputStream input = new ByteArrayInputStream(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            copyManager.copyIn(COPY_SQL, input);
        } catch (IOException ex) {
            Log.errorf("IOException during spatial profile COPY insert: %s", ex.getMessage());
            throw new RuntimeException("IO error while inserting spatial profile rows via COPY", ex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Parameterised path (≤ 1000 rows)
    // ─────────────────────────────────────────────────────────────────────

    private void insertWithParameters(List<ProfileRow> rows) {
        for (ProfileRow row : rows) {
            String anchorEwkt = toEwkt(row.anchorX(), row.anchorY(), row.anchorZ());
            String measurementsJson = toJsonOrEmpty(row.measurements());
            String metadataJson     = toJsonOrEmpty(row.metadata());
            String orientationJson  = toJsonOrEmpty(row.orientation());

            entityManager.createNativeQuery(INSERT_SQL)
                .setParameter(1, row.containerId())
                .setParameter(2, row.time())
                .setParameter(3, row.profileKind())
                .setParameter(4, anchorEwkt)
                .setParameter(5, row.profileWkt())   // null accepted — maps to SQL NULL via CASE WHEN
                .setParameter(6, measurementsJson)
                .setParameter(7, metadataJson)
                .setParameter(8, orientationJson)
                .executeUpdate();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CSV serialisation helpers (package-private for unit testing)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Appends one CSV row (ending with {@code \n}) to {@code sb}.
     *
     * <p>Column order matches the COPY target list:
     * {@code container_id, time, profile_kind, anchor, profile, measurements, metadata, orientation}
     */
    static void appendCsvRow(StringBuilder sb, ProfileRow row) {
        // Integer columns: plain numeric, no quoting.
        sb.append(row.containerId()).append(',');
        sb.append(row.time()).append(',');

        // Text column: double-quote, escaping interior quotes.
        appendCsvString(sb, row.profileKind());
        sb.append(',');

        // Geometry columns: pass EWKT; PostgreSQL sends to the PostGIS input function.
        appendCsvString(sb, toEwkt(row.anchorX(), row.anchorY(), row.anchorZ()));
        sb.append(',');

        // Nullable profile geometry: use \N (unquoted) for NULL.
        if (row.profileWkt() == null) {
            sb.append("\\N");
        } else {
            appendCsvString(sb, row.profileWkt());
        }
        sb.append(',');

        // JSONB columns: must be double-quoted because JSON contains commas.
        appendJsonCsvField(sb, row.measurements());
        sb.append(',');
        appendJsonCsvField(sb, row.metadata());
        sb.append(',');
        appendJsonCsvField(sb, row.orientation());
        sb.append('\n');
    }

    /**
     * Returns an EWKT POINTZ string for the given coordinates.
     *
     * <p>Format: {@code POINT Z (x y z)} with 10 decimal places,
     * {@link Locale#US} to guarantee {@code .} as the decimal separator.
     */
    static String toEwkt(double x, double y, double z) {
        return String.format(Locale.US, "POINT Z (%.10f %.10f %.10f)", x, y, z);
    }

    /**
     * Appends {@code value} double-quoted in CSV style, with interior {@code "}
     * escaped as {@code ""}.
     */
    static void appendCsvString(StringBuilder sb, String value) {
        sb.append('"').append(value.replace("\"", "\"\"")).append('"');
    }

    /**
     * Serialises {@code map} to JSON and appends it as a double-quoted CSV field.
     * An empty map is used when {@code map} is {@code null}.
     */
    static void appendJsonCsvField(StringBuilder sb, Map<String, Object> map) {
        String json = JsonConverter.convertToString(map == null ? Map.of() : map);
        sb.append('"').append(json.replace("\"", "\"\"")).append('"');
    }

    private static String toJsonOrEmpty(Map<String, Object> map) {
        return JsonConverter.convertToString(map == null ? Map.of() : map);
    }
}
