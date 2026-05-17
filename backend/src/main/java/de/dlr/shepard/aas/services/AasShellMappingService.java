package de.dlr.shepard.aas.services;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.v2.aas.io.AasReferenceIO;
import de.dlr.shepard.v2.aas.io.AasReferenceIO.AasKeyIO;
import de.dlr.shepard.v2.aas.io.AasShellIO;
import de.dlr.shepard.v2.aas.io.AasShellIO.AssetInformationIO;
import de.dlr.shepard.v2.aas.io.AasShellIO.LangStringIO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Maps a shepard {@link Collection} to the IDTA AAS v3 {@link AasShellIO} wire shape.
 *
 * <p>AAS1a: id + idShort + assetInformation + optional description + empty submodels.
 * AAS1b: {@link #toShell(Collection, List)} overload populates submodels from top-level
 * DataObjects; {@link #toSubmodelRefs(List)} returns just the submodel reference list.
 */
@ApplicationScoped
public class AasShellMappingService {

  public static final String COLLECTION_URN_PREFIX = "urn:shepard:collection:";
  public static final String DATAOBJECT_URN_PREFIX = "urn:shepard:dataobject:";
  static final String ASSET_URN_PREFIX = "urn:shepard:asset:";
  static final String ASSET_KIND_INSTANCE = "Instance";

  /**
   * Converts a {@link Collection} to an {@link AasShellIO} with empty {@code submodels}.
   * Used by the listing endpoint (AAS1a) where fetching per-Shell DataObjects is too costly.
   *
   * @param collection must have a non-null {@code appId}
   */
  public AasShellIO toShell(Collection collection) {
    return toShell(collection, List.of());
  }

  /**
   * Converts a {@link Collection} and its top-level DataObjects to an {@link AasShellIO}
   * with a populated {@code submodels} list. Used by the single-Shell GET endpoint (AAS1b).
   *
   * @param collection          must have a non-null {@code appId}
   * @param topLevelDataObjects top-level DataObjects (no parent DataObject) for this Collection
   */
  public AasShellIO toShell(Collection collection, List<DataObject> topLevelDataObjects) {
    String appId = collection.getAppId();
    String id = COLLECTION_URN_PREFIX + appId;
    String idShort = sanitiseIdShort(collection.getName());
    AssetInformationIO assetInfo = new AssetInformationIO(ASSET_KIND_INSTANCE, ASSET_URN_PREFIX + appId);
    List<LangStringIO> desc = buildDescription(collection.getDescription());
    List<AasReferenceIO> submodels = toSubmodelRefs(topLevelDataObjects);
    return new AasShellIO(id, idShort, assetInfo, desc, submodels);
  }

  /**
   * Converts a list of top-level DataObjects to IDTA AAS v3 Submodel {@link AasReferenceIO}s.
   * Used directly by the {@code GET /v2/aas/shells/{aasId}/submodels} endpoint.
   *
   * @param topLevelDataObjects DataObjects with no parent DataObject in this Collection
   * @return list of ExternalReference objects, one per DataObject
   */
  public List<AasReferenceIO> toSubmodelRefs(List<DataObject> topLevelDataObjects) {
    return topLevelDataObjects.stream()
        .map(d -> new AasReferenceIO(
            "ExternalReference",
            List.of(new AasKeyIO("Submodel", DATAOBJECT_URN_PREFIX + d.getAppId()))))
        .toList();
  }

  /**
   * Sanitises a Collection name to a valid IDTA AAS {@code idShort}.
   *
   * <p>AAS idShort rules: matches {@code [a-zA-Z][a-zA-Z0-9_]*}, non-empty.
   * Strategy: replace any non-alphanumeric/underscore char with {@code _};
   * prepend {@code c_} when the result starts with a digit or is empty.
   */
  static String sanitiseIdShort(String name) {
    if (name == null || name.isBlank()) {
      return "c_unnamed";
    }
    String sanitised = name.replaceAll("[^a-zA-Z0-9_]", "_");
    if (sanitised.isEmpty() || Character.isDigit(sanitised.charAt(0))) {
      sanitised = "c_" + sanitised;
    }
    return sanitised;
  }

  private List<LangStringIO> buildDescription(String description) {
    if (description == null || description.isBlank()) {
      return null;
    }
    return List.of(new LangStringIO("en", description));
  }
}
