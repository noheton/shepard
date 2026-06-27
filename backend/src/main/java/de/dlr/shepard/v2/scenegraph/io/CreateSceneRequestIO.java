package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-REST-1 — {@code POST /v2/scene-graphs} request body.
 *
 * <p>All fields optional — the server mints {@code appId} and seeds an
 * empty scene; the caller may set {@code name}, {@code description},
 * and {@code sourceFileAppId} at the same time. {@code rootFrameAppId}
 * is set later via {@code POST /v2/scene-graphs/{appId}/frames} (the
 * first frame added is the root).
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateSceneRequestIO {

  @Schema(description = "Short human-readable name.") private String name;
  @Schema(description = "Free-form description.") private String description;
  @Schema(description = "appId of the source file (.rdk, .urdf) this scene was parsed from, if any.")
  private String sourceFileAppId;
}
