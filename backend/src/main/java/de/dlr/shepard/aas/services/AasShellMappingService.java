package de.dlr.shepard.aas.services;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.v2.aas.io.AasShellIO;
import de.dlr.shepard.v2.aas.io.AasShellIO.AssetInformationIO;
import de.dlr.shepard.v2.aas.io.AasShellIO.LangStringIO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Maps a shepard {@link Collection} to the IDTA AAS v3 {@link AasShellIO} wire shape.
 *
 * <p>AAS1a scope: id + idShort + assetInformation + optional description.
 * Submodels list stays empty until AAS1b maps DataObject payloads.
 */
@ApplicationScoped
public class AasShellMappingService {

  public static final String COLLECTION_URN_PREFIX = "urn:shepard:collection:";
  static final String ASSET_URN_PREFIX = "urn:shepard:asset:";
  static final String ASSET_KIND_INSTANCE = "Instance";

  /**
   * Converts a {@link Collection} to an {@link AasShellIO}.
   *
   * @param collection must have a non-null {@code appId}
   */
  public AasShellIO toShell(Collection collection) {
    String appId = collection.getAppId();
    String id = COLLECTION_URN_PREFIX + appId;
    String idShort = sanitiseIdShort(collection.getName());
    AssetInformationIO assetInfo = new AssetInformationIO(ASSET_KIND_INSTANCE, ASSET_URN_PREFIX + appId);
    List<LangStringIO> desc = buildDescription(collection.getDescription());
    return new AasShellIO(id, idShort, assetInfo, desc, List.of());
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
