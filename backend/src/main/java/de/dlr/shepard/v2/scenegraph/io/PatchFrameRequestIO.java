package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-REST-1 — {@code PATCH /v2/scene-graphs/{appId}/frames/{frameAppId}}
 * request body.
 *
 * <p>Every field optional. Fields present overwrite the corresponding
 * entity field; fields absent are left unchanged. To clear a value,
 * send it as JSON {@code null} explicitly — Jackson's default ABSENT
 * treatment is preserved at the service layer via per-field
 * presence checks.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatchFrameRequestIO {

  @Schema(description = "New frame label, when set.") private String name;
  @Schema(description = "New parent appId; pass empty string to make this a root frame.")
  private String parentFrameAppId;

  @Schema(description = "Translation x.") private Double x;
  @Schema(description = "Translation y.") private Double y;
  @Schema(description = "Translation z.") private Double z;
  @Schema(description = "Rotation rx.") private Double rx;
  @Schema(description = "Rotation ry.") private Double ry;
  @Schema(description = "Rotation rz.") private Double rz;

  @Schema(description = "New FrameKind discriminator.") private FrameKind kind;
}
