package de.dlr.shepard.v2.quality.io;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TPL11 — request body for
 * {@code POST /v2/quality/independence-proof}.
 *
 * <p>Two sets of DataObject appIds. The service checks whether:
 * <ol>
 *   <li>The two sets share any common provenance ancestor (within a 10-hop window).</li>
 *   <li>The two sets share any annotation key-value pair (same key, same value).</li>
 * </ol>
 *
 * <p>Either set may contain DataObjects from different Collections; the caller
 * is responsible for ensuring the appIds are valid and accessible.
 */
@Data
@NoArgsConstructor
@Schema(description = "Two sets of DataObject appIds to check for independence.")
public class IndependenceProofRequestIO {

  @Schema(
    description = "AppIds of DataObjects in set A (e.g. training set). Must contain at least 1 element.",
    example = "[\"01907a2b-0000-7000-8000-000000000001\"]"
  )
  private List<String> setA;

  @Schema(
    description = "AppIds of DataObjects in set B (e.g. test set). Must contain at least 1 element.",
    example = "[\"01907a2b-0000-7000-8000-000000000002\"]"
  )
  private List<String> setB;
}
