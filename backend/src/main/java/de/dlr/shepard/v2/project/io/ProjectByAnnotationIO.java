package de.dlr.shepard.v2.project.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROJ-REST-2 — response envelope for
 * {@code GET /v2/projects/{appId}/by-annotation/{predicate}/{value}}.
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3.3}.
 */
@Schema(description = "Cross-Collection by-annotation roll-up for a Project.")
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectByAnnotationIO {

  @Schema(description = "appId of the parent Project.", required = true)
  private String projectAppId;

  @Schema(description = "Predicate IRI used for the filter.", required = true)
  private String predicate;

  @Schema(description = "Value to match.", required = true)
  private String value;

  @Schema(description = "Total number of matching DataObjects across the Project.")
  private long totalCount;

  @Schema(description = "Current page (zero-based).")
  private int page;

  @Schema(description = "Page size.")
  private int pageSize;

  @Schema(description = "Matching DataObjects on this page.")
  private List<ProjectByAnnotationItemIO> results = new ArrayList<>();
}
