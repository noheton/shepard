package de.dlr.shepard.v2.collection.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2CONV-B4 — wire shape for the Collection ↔ hero-view link.
 *
 * <p>Replaces {@code CollectionSceneGraphLinkIO}. The hero "scene-graph" is now
 * a MAPPING_RECIPE {@code ShepardTemplate} (aidocs/platform/191 decision #2),
 * so the link carries the template appId + its identity tuple rather than a
 * {@code :DigitalTwinScene}'s frame/joint counts.
 *
 * <p>The JSON field stays {@code sceneGraphAppId} (unchanged from the prior
 * shape) so existing frontend callers that read that key keep working; its
 * value is now a MAPPING_RECIPE template appId. {@code templateName} /
 * {@code templateKind} replace the old scene-specific fields.
 */
@Schema(description = "Collection hero-view link: the MAPPING_RECIPE template appId that renders as the Collection's hero 3D view.")
public class CollectionHeroViewLinkIO {

  @Schema(description = "appId of the linked MAPPING_RECIPE template (the hero view).")
  private String sceneGraphAppId;

  @Schema(description = "Name of the linked template (decorative; null when the template was deleted).")
  private String templateName;

  @Schema(description = "Description of the linked template.")
  private String templateDescription;

  @Schema(description = "templateKind of the linked template — always MAPPING_RECIPE for a valid hero view.")
  private String templateKind;

  public String getSceneGraphAppId() {
    return sceneGraphAppId;
  }

  public void setSceneGraphAppId(String sceneGraphAppId) {
    this.sceneGraphAppId = sceneGraphAppId;
  }

  public String getTemplateName() {
    return templateName;
  }

  public void setTemplateName(String templateName) {
    this.templateName = templateName;
  }

  public String getTemplateDescription() {
    return templateDescription;
  }

  public void setTemplateDescription(String templateDescription) {
    this.templateDescription = templateDescription;
  }

  public String getTemplateKind() {
    return templateKind;
  }

  public void setTemplateKind(String templateKind) {
    this.templateKind = templateKind;
  }
}
