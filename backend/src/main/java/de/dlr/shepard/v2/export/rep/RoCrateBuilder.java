package de.dlr.shepard.v2.export.rep;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles an <strong>RO-Crate 1.1</strong> {@code ro-crate-metadata.json}
 * document for a Shepard {@link Collection}.
 *
 * <p>RO-Crate spec: <a href="https://www.researchobject.org/ro-crate/1.1/">
 * https://www.researchobject.org/ro-crate/1.1/</a>
 *
 * <p>The produced document satisfies the mandatory structure:
 * <ul>
 *   <li>{@code @context} — RO-Crate 1.1 canonical URL.</li>
 *   <li>Root {@code Dataset} with {@code @id: "./"}, {@code name},
 *       {@code description}, {@code datePublished}, {@code license}.</li>
 *   <li>One {@code Dataset} entity per DataObject, linked via
 *       {@code hasPart} from the root.</li>
 *   <li>{@code ROCrateMetadata} descriptor node (as required by §2.1).</li>
 * </ul>
 *
 * <p>TPL14 — part of the Regulatory Evidence Pack feature.
 */
public class RoCrateBuilder {

  private static final String RO_CRATE_CONTEXT = "https://w3id.org/ro/crate/1.1/context";

  /**
   * Build the {@code ro-crate-metadata.json} content as a plain
   * {@link Map} that Jackson can serialise to JSON.
   *
   * @param collection the Collection including its {@code dataObjects} list
   * @return a Map representing the RO-Crate document
   */
  public Map<String, Object> build(Collection collection) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("@context", RO_CRATE_CONTEXT);

    List<Map<String, Object>> graph = new ArrayList<>();

    // §2.1 ROCrateMetadata descriptor node (mandatory).
    Map<String, Object> descriptor = new LinkedHashMap<>();
    descriptor.put("@id", "ro-crate-metadata.json");
    descriptor.put("@type", "CreativeWork");
    descriptor.put("conformsTo", Map.of("@id", "https://w3id.org/ro/crate/1.1"));
    descriptor.put("about", Map.of("@id", "./"));
    graph.add(descriptor);

    // Root Dataset.
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("@id", "./");
    root.put("@type", "Dataset");
    root.put("name", nullToEmpty(collection.getName()));
    root.put("description", nullToEmpty(collection.getDescription()));
    root.put("datePublished", exportTimestamp());
    if (collection.getLicense() != null) {
      root.put("license", collection.getLicense());
    }
    if (collection.getAccessRights() != null) {
      root.put("accessRights", collection.getAccessRights());
    }
    // shepard:appId extension property — lets a downstream tool round-trip to the API.
    root.put("identifier", "shepard:" + collection.getAppId());

    // hasPart links and per-DataObject nodes.
    List<Map<String, Object>> hasPart = new ArrayList<>();
    List<DataObject> dataObjects = collection.getDataObjects();
    if (dataObjects != null) {
      for (DataObject dobj : dataObjects) {
        if (dobj == null || dobj.getAppId() == null) continue;
        String doId = "dataobjects/" + dobj.getAppId();
        hasPart.add(Map.of("@id", doId));
        graph.add(buildDataObjectNode(dobj, doId));
      }
    }
    root.put("hasPart", hasPart);
    graph.add(root);

    doc.put("@graph", graph);
    return doc;
  }

  private Map<String, Object> buildDataObjectNode(DataObject dobj, String doId) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("@id", doId);
    node.put("@type", "Dataset");
    node.put("name", nullToEmpty(dobj.getName()));
    if (dobj.getDescription() != null) {
      node.put("description", dobj.getDescription());
    }
    if (dobj.getCreatedAt() != null) {
      node.put("dateCreated", dobj.getCreatedAt().toInstant().toString());
    }
    if (dobj.getCreatedBy() != null && dobj.getCreatedBy().getUsername() != null) {
      node.put("creator", Map.of("@id", "person:" + dobj.getCreatedBy().getUsername()));
    }
    if (dobj.getLicense() != null) {
      node.put("license", dobj.getLicense());
    }
    if (dobj.getAccessRights() != null) {
      node.put("accessRights", dobj.getAccessRights());
    }
    if (dobj.getStatus() != null) {
      node.put("creativeWorkStatus", dobj.getStatus());
    }
    // Shepherd-specific identifier.
    node.put("identifier", "shepard:" + dobj.getAppId());
    return node;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String exportTimestamp() {
    // ISO-8601 date (no time) per schema.org datePublished recommendation.
    return Instant.now().toString().substring(0, 10);
  }
}
