package de.dlr.shepard.spi.analytics;

/**
 * AT1 — one detected contiguous anomalous run within a timeseries.
 *
 * <p>SPI-side mirror of the in-tree {@code AnomalyIntervalIO} (which is the
 * wire-shape returned by the AI1b REST endpoint). The dispatcher in the
 * backend maps from this SPI record to the wire IO. The two are kept
 * structurally identical on purpose: a future "extract everything to the
 * plugin" PR can collapse the duplication once the wire-shape proof says
 * it's safe.
 *
 * @param startNs    epoch-nanos of the first anomalous point in the run
 * @param endNs      epoch-nanos of the last anomalous point in the run
 *                   ({@code startNs == endNs} for a single-point run)
 * @param peakValue  measurement value at the interval's max |Z-score|
 * @param maxZScore  largest |Z-score| observed within the run
 */
public record AnomalyInterval(long startNs, long endNs, double peakValue, double maxZScore) {}
