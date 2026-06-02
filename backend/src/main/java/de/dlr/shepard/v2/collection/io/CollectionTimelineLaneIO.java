package de.dlr.shepard.v2.collection.io;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * COLL-TIMELINE-1 — a single swimlane in the Collection timeline.
 *
 * <p>The lane key is derived from the distinct {@code valueName} of the
 * {@code urn:shepard:mffd:process-type} SemanticAnnotation on the lane's
 * DataObjects. DataObjects without that annotation fall into the synthetic
 * {@code "unclassified"} lane so the empty-state is informative rather than
 * misleading.
 */
@Schema(description = "Single swimlane (process-type) in the Collection timeline response.")
public class CollectionTimelineLaneIO {

  @Schema(
    description =
      "Stable slug for the lane (e.g. \"afp-layup\", \"ndt-inspection\", " +
      "\"unclassified\"). Derived from the annotation valueName.",
    example = "afp-layup")
  private String key;

  @Schema(
    description =
      "Human-readable label for the lane (e.g. \"AFP Layup\"). Equals the raw " +
      "valueName for ordinary lanes; \"Unclassified\" for the catch-all lane.",
    example = "AFP Layup")
  private String label;

  @Schema(description = "Day-bins ordered ascending by day; empty when the lane has no DataObjects.")
  private List<CollectionTimelineBinIO> bins = new ArrayList<>();

  public CollectionTimelineLaneIO() {}

  public CollectionTimelineLaneIO(String key, String label, List<CollectionTimelineBinIO> bins) {
    this.key = key;
    this.label = label;
    this.bins = bins != null ? bins : new ArrayList<>();
  }

  public String getKey() { return key; }
  public void setKey(String key) { this.key = key; }

  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }

  public List<CollectionTimelineBinIO> getBins() { return bins; }
  public void setBins(List<CollectionTimelineBinIO> bins) { this.bins = bins; }
}
