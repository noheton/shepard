package de.dlr.shepard.v2.export.rep;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around {@link ProvJsonLdRenderer#renderProvO(List)} that
 * fetches the collection's provenance activities via {@link ActivityDAO}
 * and returns the assembled PROV-O JSON-LD graph.
 *
 * <p>Activities are NOT linked to Collections via Neo4j relationship edges.
 * The correct retrieval path is {@code ActivityDAO.list(null, "Collection",
 * collectionAppId, null, null, 1000)} — filtering by the {@code targetAppId}
 * property on each {@code :Activity} node.
 *
 * <p>TPL14 — part of the Regulatory Evidence Pack feature.
 */
public class ProvOBuilder {

  private final ActivityDAO activityDAO;
  private final ProvJsonLdRenderer renderer;

  public ProvOBuilder(ActivityDAO activityDAO, ProvJsonLdRenderer renderer) {
    this.activityDAO = activityDAO;
    this.renderer = renderer;
  }

  /**
   * Fetch up to 1000 activities targeting {@code collectionAppId} and
   * render them as a PROV-O JSON-LD {@link Map}.
   *
   * @param collectionAppId the UUID-v7 appId of the Collection
   * @return the PROV-O JSON-LD document as a Map (ready for Jackson serialisation)
   */
  public Map<String, Object> build(String collectionAppId) {
    List<Activity> activities = activityDAO.list(null, "Collection", collectionAppId, null, null, 1000);
    return renderer.renderProvO(activities);
  }
}
