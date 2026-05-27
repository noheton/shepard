package de.dlr.shepard.data.spatialdata.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the CSV serialisation helpers in {@link SpatialProfileRepository}.
 *
 * <p>These are plain JUnit 5 tests — no Quarkus, no DI, no DB.
 * They test the static helpers in isolation so they execute during {@code mvn test}
 * without a live PostGIS container (unlike the integration tests in
 * {@link SpatialDataPointRepositoryTest} which are excluded from surefire).
 */
public class SpatialProfileRepositoryTest {

    // ─────────────────────────────────────────────────────────────────────
    // toEwkt
    // ─────────────────────────────────────────────────────────────────────

    @Test
    public void toEwkt_integers_formatsCorrectly() {
        String result = SpatialProfileRepository.toEwkt(1.0, 2.0, 3.0);
        assertEquals("POINT Z (1.0000000000 2.0000000000 3.0000000000)", result);
    }

    @Test
    public void toEwkt_decimals_formatsCorrectly() {
        String result = SpatialProfileRepository.toEwkt(1.5, 2.5, 3.5);
        assertEquals("POINT Z (1.5000000000 2.5000000000 3.5000000000)", result);
    }

    @Test
    public void toEwkt_negative_formatsCorrectly() {
        String result = SpatialProfileRepository.toEwkt(-1.0, -2.5, 0.0);
        assertEquals("POINT Z (-1.0000000000 -2.5000000000 0.0000000000)", result);
    }

    @Test
    public void toEwkt_usesDecimalPoint_notComma() {
        // Verify that locale is US (no comma as decimal separator).
        String result = SpatialProfileRepository.toEwkt(1.5, 2.5, 3.5);
        assertFalse(result.contains(","), "EWKT must use '.' not ',' as decimal separator");
    }

    // ─────────────────────────────────────────────────────────────────────
    // appendCsvString
    // ─────────────────────────────────────────────────────────────────────

    @Test
    public void appendCsvString_simple_wrapsInQuotes() {
        var sb = new StringBuilder();
        SpatialProfileRepository.appendCsvString(sb, "point");
        assertEquals("\"point\"", sb.toString());
    }

    @Test
    public void appendCsvString_containsDoubleQuote_escapesCorrectly() {
        var sb = new StringBuilder();
        SpatialProfileRepository.appendCsvString(sb, "say \"hello\"");
        assertEquals("\"say \"\"hello\"\"\"", sb.toString());
    }

    // ─────────────────────────────────────────────────────────────────────
    // appendCsvRow — point kind (null profileWkt)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    public void appendCsvRow_pointKind_nullProfileWktRenderedAsBackslashN() {
        var row = new ProfileRow(
            42L,
            1_000_000_000L,
            "point",
            1.0, 2.0, 3.0,
            null,               // profileWkt: null for point kind
            Map.of("accel_x", 9.81),
            Map.of("layer", 5),
            Map.of()
        );
        var sb = new StringBuilder();
        SpatialProfileRepository.appendCsvRow(sb, row);
        String csv = sb.toString();

        // Row must end with newline.
        assertTrue(csv.endsWith("\n"), "CSV row must end with newline");

        String[] fields = splitCsvRow(csv.trim());
        assertEquals(8, fields.length, "Expected 8 CSV fields");

        // container_id and time are plain numerics.
        assertEquals("42", fields[0]);
        assertEquals("1000000000", fields[1]);

        // profile_kind is quoted.
        assertEquals("\"point\"", fields[2]);

        // anchor EWKT is quoted.
        assertTrue(fields[3].startsWith("\"POINT Z"), "anchor field must be quoted EWKT");

        // NULL profile → \N unquoted.
        assertEquals("\\N", fields[4], "null profileWkt must serialize as \\N");
    }

    @Test
    public void appendCsvRow_lineKind_nonNullProfileWktRendered() {
        var row = new ProfileRow(
            99L,
            2_000_000_000L,
            "line",
            0.0, 0.0, 0.0,
            "LINESTRING Z (0 0 0, 1 1 1)",
            Map.of(),
            Map.of(),
            Map.of("beam_angle", 45.0)
        );
        var sb = new StringBuilder();
        SpatialProfileRepository.appendCsvRow(sb, row);
        String csv = sb.toString();

        String[] fields = splitCsvRow(csv.trim());
        assertEquals(8, fields.length);

        // profile_kind.
        assertEquals("\"line\"", fields[2]);

        // profile field must NOT be \N.
        assertFalse("\\N".equals(fields[4]), "non-null profileWkt must not serialize as \\N");
        assertTrue(fields[4].contains("LINESTRING"), "profile field must contain the WKT");
    }

    @Test
    public void appendCsvRow_jsonFieldsAreDoubleQuoted() {
        var row = new ProfileRow(
            1L,
            1L,
            "point",
            0.0, 0.0, 0.0,
            null,
            Map.of("key", "value with, comma"),
            Map.of(),
            Map.of()
        );
        var sb = new StringBuilder();
        SpatialProfileRepository.appendCsvRow(sb, row);
        String csv = sb.toString();

        // measurements field is field index 5; verify it starts with '"'.
        String[] fields = splitCsvRow(csv.trim());
        assertTrue(fields[5].startsWith("\""), "JSONB measurements field must be double-quoted in CSV");
    }

    @Test
    public void appendCsvRow_nullMaps_renderedAsEmptyJsonObject() {
        var row = new ProfileRow(
            1L,
            1L,
            "point",
            0.0, 0.0, 0.0,
            null,
            null,   // measurements null
            null,   // metadata null
            null    // orientation null
        );
        var sb = new StringBuilder();
        SpatialProfileRepository.appendCsvRow(sb, row);
        String csv = sb.toString();

        String[] fields = splitCsvRow(csv.trim());
        // measurements = fields[5], metadata = fields[6], orientation = fields[7]
        assertEquals("\"{}\"", fields[5], "null measurements must serialize as empty JSON object");
        assertEquals("\"{}\"", fields[6], "null metadata must serialize as empty JSON object");
        assertEquals("\"{}\"", fields[7], "null orientation must serialize as empty JSON object");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Threshold dispatch (size boundary)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    public void copyThreshold_boundaryValue_is1000() {
        // Verify that the published constant matches the expected value.
        assertEquals(1000, SpatialProfileRepository.COPY_THRESHOLD);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper: split a single CSV row into raw field tokens.
    // Handles quoted fields containing commas; does NOT handle escaped quotes
    // inside quoted fields (sufficient for our assertions above).
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Splits a raw CSV row into its field tokens, respecting double-quoted fields.
     * Good enough for the assertions in this test — not a general CSV parser.
     */
    private static String[] splitCsvRow(String row) {
        var result = new java.util.ArrayList<String>();
        int i = 0;
        while (i <= row.length()) {
            if (i == row.length()) {
                break;
            }
            if (row.charAt(i) == '"') {
                // Quoted field: scan to closing quote.
                int start = i;
                i++; // skip opening "
                while (i < row.length()) {
                    if (row.charAt(i) == '"') {
                        if (i + 1 < row.length() && row.charAt(i + 1) == '"') {
                            i += 2; // escaped quote
                        } else {
                            i++; // closing quote
                            break;
                        }
                    } else {
                        i++;
                    }
                }
                result.add(row.substring(start, i));
            } else {
                // Unquoted field: scan to comma.
                int start = i;
                while (i < row.length() && row.charAt(i) != ',') {
                    i++;
                }
                result.add(row.substring(start, i));
            }
            // Skip the comma.
            if (i < row.length() && row.charAt(i) == ',') {
                i++;
            }
        }
        return result.toArray(new String[0]);
    }
}
