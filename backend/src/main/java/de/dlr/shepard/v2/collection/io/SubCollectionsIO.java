package de.dlr.shepard.v2.collection.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire response shape for
 * {@code GET /v2/collections/{collectionAppId}/sub-collections}.
 *
 * <p>Returns a combined view of the parent Collection's project / programme
 * annotations and the list of child Collections that carry a
 * {@code urn:shepard:partOf} semantic annotation pointing at the parent's
 * {@code appId}.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code parentAppId} — the queried parent Collection's appId.</li>
 *   <li>{@code parentIsProject} — {@code true} when the parent carries a
 *       {@code urn:shepard:project = "true"} annotation.</li>
 *   <li>{@code programmes} — all {@code value} strings from
 *       {@code urn:shepard:programme} annotations on the parent; empty when
 *       none exist.</li>
 *   <li>{@code subCollections} — ordered list of child entries in the default
 *       trimmed shape. Empty when no child Collection has a
 *       {@code urn:shepard:partOf} annotation pointing at this parent.</li>
 * </ul>
 *
 * <p>Spec: {@code aidocs/integrations/121 §3.1}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SubCollections",
  description = "Parent Collection context plus its sub-collection list.")
public class SubCollectionsIO {

  @Schema(required = true,
    description = "AppId of the queried parent Collection (mirrors the path parameter).")
  private String parentAppId;

  @Schema(required = true,
    description = "True when the parent Collection carries a " +
    "urn:shepard:project = \"true\" semantic annotation.")
  private boolean parentIsProject;

  @Schema(required = true,
    description = "Values of all urn:shepard:programme annotations on the parent. " +
    "Empty when none are set.")
  private List<String> programmes;

  @Schema(required = true,
    description = "Sub-collections: Collections that carry a urn:shepard:partOf " +
    "annotation pointing at this parent's appId. Empty when no such child exists.")
  private List<SubCollectionEntryIO> subCollections;
}
