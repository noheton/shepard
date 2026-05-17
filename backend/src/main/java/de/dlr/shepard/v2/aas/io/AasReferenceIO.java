package de.dlr.shepard.v2.aas.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * IDTA AAS v3 {@code Reference} — used in the Shell's {@code submodels} list
 * and in the {@code GET /v2/aas/shells/{aasId}/submodels} response.
 *
 * <p>AAS1b scope: {@code type} is always {@code "ExternalReference"};
 * {@code keys} holds a single {@code Submodel} key whose {@code value} is
 * {@code urn:shepard:dataobject:{appId}}.
 *
 * @param type AAS Reference type — {@code "ModelReference"} or {@code "ExternalReference"}.
 * @param keys ordered key list. Always one key for Submodel references.
 */
@Schema(name = "AasReference",
    description = "IDTA AAS v3 Reference — a typed key path pointing to a Submodel.")
public record AasReferenceIO(

    @Schema(required = true,
        description = "Reference type: 'ModelReference' (same repository) or 'ExternalReference'.")
    String type,

    @Schema(required = true,
        description = "Ordered key sequence identifying the referenced element.")
    List<AasKeyIO> keys

) {

  /**
   * One element of an IDTA AAS v3 key sequence.
   *
   * @param type  AAS model-element type, e.g. {@code "Submodel"}.
   * @param value The IRI / appId string identifying the element.
   */
  @Schema(name = "AasKey",
      description = "One key in an IDTA AAS v3 Reference key sequence.")
  public record AasKeyIO(

      @Schema(required = true,
          description = "AAS model-element type, e.g. 'Submodel', 'SubmodelElementCollection'.")
      String type,

      @Schema(required = true,
          description = "Identifier value — the global IRI or appId of the referenced element.")
      String value

  ) {}
}
