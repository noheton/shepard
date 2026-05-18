package de.dlr.shepard.v2.integrity;

import java.util.List;

/**
 * DI1 — body returned by the {@code /v2/} container safe-delete endpoints when
 * a delete is refused because the container still has active references.
 *
 * <p>Wire shape:
 * <pre>
 *   {
 *     "referenceCount": 3,
 *     "sampleDataObjectAppIds": ["01HX...", "01HY...", "01HZ..."]
 *   }
 * </pre>
 *
 * <p>The sample list is capped at {@link #SAMPLE_LIMIT} entries so a container
 * with thousands of links doesn't return a 100 kB response.
 *
 * <p>Clients that have already informed the user and still want to proceed
 * should retry the same DELETE with {@code ?force=true}, which skips the
 * reference check and orphans the surviving references (matching the
 * upstream {@code /shepard/api/} behaviour).
 */
public record SafeDeleteConflict(
  long referenceCount,
  List<String> sampleDataObjectAppIds
) {
  /** Maximum number of appIds to return in the sample list. */
  public static final int SAMPLE_LIMIT = 10;
}
