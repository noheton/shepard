package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-CREATE-FROM-URDF-1 — request body for
 * {@code POST /v2/scene-graphs/from-urdf/{fileReferenceAppId}}.
 *
 * <p>Both fields optional — when omitted, the server derives the
 * scene's {@code name} from the URDF's {@code <robot name="...">}
 * attribute (falling back to the FileReference's display name) and
 * leaves {@code description} blank.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateSceneFromUrdfRequestIO {

  @Schema(description = "Optional scene name; defaults to the URDF <robot name=\"...\"> attribute.")
  private String name;

  @Schema(description = "Optional free-form description.")
  private String description;
}
