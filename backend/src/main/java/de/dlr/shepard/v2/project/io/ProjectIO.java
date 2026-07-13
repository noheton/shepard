package de.dlr.shepard.v2.project.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROJ-REST-1 — Project envelope returned by {@code GET /v2/projects/{appId}}.
 *
 * <p>A Project is implemented as a {@link de.dlr.shepard.context.collection.entities.Collection}
 * that carries the {@code urn:shepard:project = "true"} semantic annotation, but
 * the wire shape here is intentionally Project-flavoured: it surfaces the
 * Project-only fields ({@code programmes}, {@code subCollectionCount},
 * {@code aggregateDoCount}, {@code lastActivity}, {@code isProject}) and
 * omits the Collection-internal bag (containers, DataObject pagination cursors,
 * etc.). Callers who need the raw Collection shape stay on
 * {@code /v2/collections/{appId}}.
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3.1}.
 */
@Schema(description = "Project envelope — a Collection that bundles non-exclusive child Collections.")
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectIO {

  @Schema(description = "Stable UUID v7 identifier of the underlying Collection.", required = true)
  private String appId;

  @Schema(description = "Display name of the Project.", required = true)
  private String name;

  @Schema(description = "Free-text description, CommonMark + GFM.")
  private String description;

  @Schema(description = "Owner UserGroup, when set.")
  private String ownerGroup;

  @Schema(description = "Programme labels declared on this Project (urn:shepard:programme).")
  private List<String> programmes = new ArrayList<>();

  @Schema(description = "Number of Collections that carry urn:shepard:partOf = <this appId>.")
  private long subCollectionCount;

  @Schema(description = "Sum of DataObject counts across all sub-Collections.")
  private long aggregateDoCount;

  @Schema(description = "ISO 8601 UTC timestamp of the maximum {created/updated} across all sub-Collections.")
  private String lastActivity;

  @Schema(description = "Always true for endpoints under /v2/projects/{appId}; included for cross-API parity.")
  private boolean isProject = true;
}
