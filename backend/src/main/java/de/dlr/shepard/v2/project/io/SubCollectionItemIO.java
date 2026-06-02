package de.dlr.shepard.v2.project.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROJ-REST-1 — one child-Collection row in the
 * {@code GET /v2/projects/{appId}/sub-collections} response.
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3.2}.
 */
@Schema(description = "One sub-Collection of a Project.")
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubCollectionItemIO {

  @Schema(description = "appId of the child Collection.", required = true)
  private String appId;

  @Schema(description = "Legacy long id of the child Collection.")
  private Long id;

  @Schema(description = "Display name.", required = true)
  private String name;

  @Schema(description = "Owner UserGroup, when set.")
  private String ownerGroup;

  @Schema(description = "DataObject count on this child Collection.")
  private long doCount;

  @Schema(description = "Maximum {created/updated} timestamp on this child Collection; epoch millis.")
  private Long lastActivityMillis;

  @Schema(description = "Other Projects this Collection is also a member of (urn:shepard:partOf appIds, excluding the queried parent).")
  private List<String> alsoMemberOf = new ArrayList<>();
}
