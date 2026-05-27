/**
 * TM1b — Wall-clock time overlay utilities.
 *
 * When a TimeseriesReference has timeReference = EXPERIMENT_RELATIVE and a
 * wallClockOffset, timestamps stored in the DB are nanoseconds-from-experiment-t=0.
 * The absolute UTC time of a sample is:
 *
 *   wall_clock_ns = wallClockOffset + sample_t_ns
 *
 * These helpers format the dual-label strings shown in chart tooltips.
 */

/**
 * Format a stored timestamp (epoch-ms or t=0-relative-ms, depending on mode)
 * as both an experiment-relative "t+Xs" string and an absolute UTC ISO string.
 *
 * @param tMs     - The timestamp in milliseconds as stored in the chart series
 *                  (for EXPERIMENT_RELATIVE this is nanoseconds-from-t0 / 1e6)
 * @param offsetMs - wallClockOffset in milliseconds (wallClockOffset / 1e6).
 *                  When provided, absolute = tMs + offsetMs (epoch ms).
 * @returns       { relative: string, absolute: string }
 *   - relative: "t=0.000s" … "t=8.234s" (seconds from experiment start)
 *   - absolute: "2024-06-02 14:30:08.234 UTC" (full UTC timestamp)
 */
export function formatDualTime(
  tMs: number,
  offsetMs: number,
): { relative: string; absolute: string } {
  const relativeSec = tMs / 1000;
  const sign = relativeSec < 0 ? "−" : "+";
  const absSec = Math.abs(relativeSec);
  const relative = `t${sign}${absSec.toFixed(3)}s`;

  const absoluteMs = tMs + offsetMs;
  const absolute = new Date(absoluteMs)
    .toISOString()
    .replace("T", " ")
    .slice(0, 23) + " UTC";

  return { relative, absolute };
}

/**
 * Format only the relative part: "t+8.234s".
 * Used when only a short label is needed (e.g. axis tick formatter).
 */
export function formatRelativeTime(tMs: number): string {
  const relativeSec = tMs / 1000;
  const sign = relativeSec < 0 ? "−" : "+";
  const absSec = Math.abs(relativeSec);
  return `t${sign}${absSec.toFixed(3)}s`;
}

/**
 * Format only the absolute UTC part: "2024-06-02 14:30:08.234 UTC".
 * @param tMs - experiment-relative timestamp in ms.
 * @param offsetMs - wallClockOffset in ms.
 */
export function formatAbsoluteTime(tMs: number, offsetMs: number): string {
  const absoluteMs = tMs + offsetMs;
  return (
    new Date(absoluteMs).toISOString().replace("T", " ").slice(0, 23) + " UTC"
  );
}
