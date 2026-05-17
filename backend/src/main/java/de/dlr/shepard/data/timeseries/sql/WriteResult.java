package de.dlr.shepard.data.timeseries.sql;

/**
 * P10b — result returned by format writers ({@link CsvWriter}, {@link NdjsonWriter},
 * {@link JsonWriter}) indicating how many rows were written and whether the result was
 * truncated by the row cap.
 *
 * <p>The {@code truncated} flag drives the {@code x-shepard-truncated: true} HTTP trailer
 * emitted by {@link SqlTimeseriesRest} when the row cap is reached before the cursor is
 * exhausted.
 */
public record WriteResult(long rowsWritten, boolean truncated) {}
