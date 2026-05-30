package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.SceneListRow;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-LIST-1 — list-item wire shape for {@code GET /v2/scene-graphs}.
 *
 * <p>Trimmed down from the full {@link SceneGraphIO} for index browsing — no
 * frame or joint arrays, just the scalar identity + the two count fields
 * that drive the dashboard's "N frames / M joints" badge.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "SceneGraphListItem",
  description = "Compact DigitalTwinScene row for the /v2/scene-graphs list. "
    + "Carries scalar identity + frame and joint counts; full frame and joint "
    + "arrays are returned by GET /v2/scene-graphs/{appId} instead.")
public class SceneGraphListItemIO {

  @Schema(description = "UUID v7 appId of this scene.")
  private String appId;

  @Schema(description = "Short human-readable name.")
  private String name;

  @Schema(description = "Free-form description (may be null).")
  private String description;

  @Schema(description = "appId of the source file this scene was parsed from, if any.")
  private String sourceFileAppId;

  @Schema(description = "appId of the root :CoordinateFrame, if a root has been added.")
  private String rootFrameAppId;

  @Schema(description = "Number of :CoordinateFrame nodes attached via :HAS_FRAME.")
  private long frameCount;

  @Schema(description = "Number of :Joint nodes attached via :HAS_JOINT.")
  private long jointCount;

  @Schema(description = "Epoch-millis timestamp of first save; null for pre-SCENEGRAPH-LIST-1 rows.")
  private Long createdAt;

  @Schema(description = "Epoch-millis timestamp of most recent save; null for pre-SCENEGRAPH-LIST-1 rows.")
  private Long updatedAt;

  public SceneGraphListItemIO(SceneListRow row) {
    this.appId = row.appId();
    this.name = row.name();
    this.description = row.description();
    this.sourceFileAppId = row.sourceFileAppId();
    this.rootFrameAppId = row.rootFrameAppId();
    this.frameCount = row.frameCount();
    this.jointCount = row.jointCount();
    this.createdAt = row.createdAt();
    this.updatedAt = row.updatedAt();
  }
}
