package de.dlr.shepard.v2.collection.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * COLL-SCENE-1 — wire shape for
 * {@code GET /v2/collections/{appId}/scene-graph} responses and for
 * {@code PUT} request bodies.
 *
 * <p>On GET, the response carries the scene's identity tuple
 * ({@code sceneGraphAppId}, {@code name}, {@code description},
 * {@code rootFrameAppId}, {@code sourceFileAppId}, {@code frameCount},
 * {@code jointCount}) so the frontend can render a viewer header
 * without a follow-up {@code GET /v2/scene-graphs/{appId}} round-trip.
 *
 * <p>On PUT, the {@code sceneGraphAppId} field is the only required
 * input; everything else is ignored server-side. The server returns
 * the GET shape after a successful link.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
  name = "CollectionSceneGraphLink",
  description =
    "Wire shape for the Collection ↔ DigitalTwinScene link. On GET, "
    + "carries the linked scene's identity tuple (appId + name + counts) "
    + "so the frontend can render a viewer header without a follow-up "
    + "scene fetch. On PUT, only `sceneGraphAppId` is read; the rest is "
    + "ignored server-side."
)
public class CollectionSceneGraphLinkIO {

  @Schema(description = "UUID v7 appId of the linked :DigitalTwinScene.", required = true)
  private String sceneGraphAppId;

  @Schema(description = "Short human-readable scene name.", readOnly = true)
  private String name;

  @Schema(description = "Free-form scene description (may be null).", readOnly = true)
  private String description;

  @Schema(description = "appId of the root :CoordinateFrame, if a root has been added.", readOnly = true)
  private String rootFrameAppId;

  @Schema(description = "appId of the source file (URDF / RDK) the scene was parsed from, if any.", readOnly = true)
  private String sourceFileAppId;

  @Schema(description = "Number of :CoordinateFrame nodes in the scene.", readOnly = true)
  private Long frameCount;

  @Schema(description = "Number of :Joint nodes in the scene.", readOnly = true)
  private Long jointCount;
}
