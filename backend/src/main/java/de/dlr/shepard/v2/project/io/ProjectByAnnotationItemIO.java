package de.dlr.shepard.v2.project.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROJ-REST-2 — one DataObject row in the
 * {@code GET /v2/projects/{appId}/by-annotation/{predicate}/{value}} response.
 *
 * <p>The {@code matchedAnnotations} field is only populated when the caller
 * passes {@code ?include=annotations}; otherwise it stays null and is omitted
 * from the wire response per
 * {@link com.fasterxml.jackson.annotation.JsonInclude.Include#NON_NULL}.
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3.3}.
 */
@Schema(description = "One DataObject matching the by-annotation predicate query within a Project.")
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectByAnnotationItemIO {

  @Schema(description = "appId of the DataObject.", required = true)
  private String appId;

  @Schema(description = "Legacy long id of the DataObject.")
  private Long id;

  @Schema(description = "DataObject name.", required = true)
  private String name;

  @Schema(description = "Always 'DataObject' in the current implementation.")
  private String kind = "DataObject";

  @Schema(description = "appId of the Collection that holds this DataObject.")
  private String collectionAppId;

  @Schema(description = "Name of the Collection that holds this DataObject.")
  private String collectionName;

  @Schema(description = "Matched annotation details — only populated when include=annotations.")
  private List<MatchedAnnotation> matchedAnnotations;

  public void addMatched(MatchedAnnotation m) {
    if (matchedAnnotations == null) matchedAnnotations = new ArrayList<>();
    matchedAnnotations.add(m);
  }

  /**
   * Single matched-annotation row. {@code source} is {@code "direct"} when the
   * annotation lives on the DataObject itself, {@code "inherited"} when the
   * walk found it on a parent.
   */
  @Schema(description = "Matched annotation details.")
  @Data
  @NoArgsConstructor
  public static class MatchedAnnotation {
    @Schema(description = "Predicate IRI.")
    private String predicate;

    @Schema(description = "Object literal or IRI.")
    private String value;

    @Schema(description = "'direct' or 'inherited'.")
    private String source;
  }
}
