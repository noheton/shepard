package de.dlr.shepard.v2.quality.io;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TPL11 — response body for
 * {@code POST /v2/quality/independence-proof}.
 *
 * <p>{@code independent = true} when both {@code sharedAncestors} and
 * {@code sharedAnnotations} are empty — i.e. neither provenance overlap
 * nor identical annotation key-value pairs were found between the two sets.
 *
 * <p>The check is best-effort within a 10-hop ancestor window.
 * See {@link IndependenceProofQuery#SHARED_ANCESTORS} for the cap rationale.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Independence check result for two DataObject sets.")
public class IndependenceProofResultIO {

  @Schema(
    description = "True when no shared ancestors and no shared annotation key-value pairs were found.",
    example = "true"
  )
  private boolean independent;

  @Schema(
    description = "List of shared provenance ancestor appIds (empty when independent). " +
      "Each entry represents a DataObject that is an ancestor (within 10 hops) of at least " +
      "one member of setA and at least one member of setB.",
    example = "[]"
  )
  private List<SharedAncestorIO> sharedAncestors;

  @Schema(
    description = "List of annotation key-value pairs appearing on at least one DataObject in " +
      "each set (empty when independent). " +
      "Shared annotations may indicate the sets were derived from the same experimental context.",
    example = "[]"
  )
  private List<SharedAnnotationIO> sharedAnnotations;

  @Schema(
    description = "ISO-8601 timestamp when the check was performed.",
    example = "2026-05-26T12:34:56Z"
  )
  private Instant checkedAt;

  /**
   * A single shared provenance ancestor entry.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "A DataObject that is a provenance ancestor of members from both sets.")
  public static class SharedAncestorIO {

    @Schema(description = "The appId of the shared ancestor DataObject.")
    private String ancestorAppId;

    @Schema(description = "AppIds from setA that have this ancestor within 10 hops.")
    private List<String> reachableFromA;

    @Schema(description = "AppIds from setB that have this ancestor within 10 hops.")
    private List<String> reachableFromB;
  }

  /**
   * A single shared annotation key-value pair found in both sets.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "An annotation key-value pair shared between the two sets.")
  public static class SharedAnnotationIO {

    @Schema(description = "The annotation key (attribute name).", example = "propellant")
    private String key;

    @Schema(description = "The shared annotation value.", example = "LOX/LH2")
    private String value;

    @Schema(description = "AppIds from setA that carry this annotation.")
    private List<String> fromA;

    @Schema(description = "AppIds from setB that carry this annotation.")
    private List<String> fromB;
  }
}
