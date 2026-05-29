package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-REST-1 — wire shape for a full {@link DigitalTwinScene}
 * (header + all frames + all joints).
 *
 * <p>JSON-LD support is opt-in: when the client requests
 * {@code Accept: application/ld+json}, the response body adds
 * {@code @context} and {@code @type} so the same payload doubles as a
 * lightweight linked-data document. The shape is unchanged otherwise.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description =
  "A DigitalTwinScene with its frame tree + joints. Carries the scalar " +
  "rootFrameAppId pointer (per aidocs/data/85 §5) plus arrays of all " +
  "frames and joints in the scene.")
public class SceneGraphIO {

  @JsonProperty("@context")
  @Schema(description = "JSON-LD context — present only when the response is served as application/ld+json.")
  private String context;

  @JsonProperty("@type")
  @Schema(description = "JSON-LD type — present only when the response is served as application/ld+json.")
  private String type;

  @Schema(description = "UUID v7 appId of this scene.") private String appId;
  @Schema(description = "Short human-readable name.") private String name;
  @Schema(description = "Free-form description.") private String description;

  @Schema(description = "appId of the source file (.rdk, .urdf) this scene was parsed from, if any.")
  private String sourceFileAppId;

  @Schema(description = "appId of the root CoordinateFrame in the frame tree.")
  private String rootFrameAppId;

  @Schema(description = "All CoordinateFrames belonging to this scene.")
  private List<FrameIO> frames;

  @Schema(description = "All Joints belonging to this scene.")
  private List<JointIO> joints;

  public SceneGraphIO(DigitalTwinScene scene, List<FrameIO> frames, List<JointIO> joints) {
    this.appId = scene.getAppId();
    this.name = scene.getName();
    this.description = scene.getDescription();
    this.sourceFileAppId = scene.getSourceFileAppId();
    this.rootFrameAppId = scene.getRootFrameAppId();
    this.frames = frames;
    this.joints = joints;
  }

  /** Attach JSON-LD framing tokens before serialisation. */
  public SceneGraphIO withJsonLd() {
    this.context = "https://schema.shepard.dlr.de/v2/scene-graph";
    this.type = "DigitalTwinScene";
    return this;
  }
}
