package de.dlr.shepard.v2.project.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROJ-REST-1 — response envelope for
 * {@code GET /v2/projects/{appId}/sub-collections}.
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3.2}.
 */
@Schema(description = "Sub-Collections of a Project, with the Project's programme strip.")
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubCollectionsIO {

  @Schema(description = "appId of the parent Project.", required = true)
  private String projectAppId;

  @Schema(description = "Programme labels on the parent Project (urn:shepard:programme values).")
  private List<String> programmes = new ArrayList<>();

  @Schema(description = "Child Collections of the Project. May be empty.")
  private List<SubCollectionItemIO> subCollections = new ArrayList<>();
}
