package de.dlr.shepard.v2.mappings.io;

import de.dlr.shepard.spi.transform.TransformResult;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2CONV-B3 — response body for a successful
 * {@code POST /v2/mappings/{templateAppId}/materialize}.
 *
 * <p>Mirrors the SPI {@link TransformResult} onto the wire. The {@code outputKind}
 * discriminates which payload field is meaningful:
 * <ul>
 *   <li>{@code REFERENCE} → {@code derivedReferenceAppId} carries the derived
 *       reference's appId.</li>
 *   <li>{@code VIEW} → {@code viewModel} carries a JSON-serialisable projection
 *       (a played/rendered envelope).</li>
 * </ul>
 *
 * @param templateAppId         echo of the path param — the MAPPING_RECIPE template
 * @param outputKind            REFERENCE | VIEW
 * @param derivedReferenceAppId appId of the derived reference (REFERENCE) or null
 * @param viewModel             view-model map (VIEW) or null
 * @param executor              name of the TransformExecutor that produced this
 */
@Schema(description = "Result of a MAPPING_RECIPE materialization.")
public record MaterializeResponseIO(
  @Schema(description = "appId of the MAPPING_RECIPE template that was materialized.")
  String templateAppId,

  @Schema(description = "REFERENCE when a derived reference appId is returned; VIEW for a played/rendered projection.")
  String outputKind,

  @Schema(description = "appId of the derived reference (set when outputKind=REFERENCE).", nullable = true)
  String derivedReferenceAppId,

  @Schema(description = "JSON-serialisable view-model (set when outputKind=VIEW).", nullable = true)
  Map<String, Object> viewModel,

  @Schema(description = "Name of the TransformExecutor that produced this result.")
  String executor
) {
  /** Map an SPI {@link TransformResult} onto the wire shape. */
  public static MaterializeResponseIO from(String templateAppId, TransformResult result) {
    return new MaterializeResponseIO(
      templateAppId,
      result.kind().name(),
      result.derivedReferenceAppId(),
      result.viewModel(),
      result.executorName()
    );
  }
}
