package de.dlr.shepard.context.collection.services;

import de.dlr.shepard.common.neo4j.NeoConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import org.neo4j.ogm.model.Result;

/**
 * #27-ARCHIVED — write-gate that returns HTTP 409 when a write is attempted
 * on a child of an {@code ARCHIVED} Collection or against payload owned by an
 * {@code ARCHIVED} Container.
 *
 * <p>The check is direct Cypher (one round-trip, no OGM load) keyed by the
 * OGM Long id — the same id the calling service layers already hold. Null
 * {@code status} on the parent is treated as effectively {@code READY}
 * (unblocked) — pre-feature rows behave exactly as before.
 *
 * <p>Both methods are idempotent and side-effect-free. A non-existent id
 * returns without throwing (the calling service handles the 404 via its
 * existing not-found paths).
 *
 * <p>Wire points (callers):
 * <ul>
 *   <li>{@link DataObjectService#createDataObject} — gate on Collection
 *       being non-archived before creating a child DataObject.</li>
 *   <li>{@code FileContainerService.createFile} / {@code deleteFile} — gate
 *       on FileContainer being non-archived.</li>
 *   <li>{@code TimeseriesContainerService} write paths — gate on TS container.</li>
 *   <li>{@code StructuredDataContainerService} write paths — gate on SD container.</li>
 * </ul>
 *
 * <p>Backlog: {@code #27-ARCHIVED-01/02/03} +
 * {@code #27-CONTAINER-STATUS-01} in {@code aidocs/16}.
 */
@ApplicationScoped
public class ArchiveStateGuard {

  /** Sentinel value treated as "frozen, prune-only" by the guard. */
  public static final String ARCHIVED = "ARCHIVED";

  /**
   * Throw HTTP 409 if the {@code :Collection} with the given OGM id has
   * {@code status = "ARCHIVED"}. Null status, missing node, or any other
   * status value all pass silently.
   *
   * @param collectionOgmId Neo4j OGM Long id of the Collection
   */
  public void assertCollectionNotArchived(long collectionOgmId) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", collectionOgmId);
    Result r = NeoConnector.getInstance()
      .getNeo4jSession()
      .query(
        "MATCH (c:Collection) WHERE id(c) = $id RETURN c.status AS s LIMIT 1",
        params
      );
    String status = readStatus(r);
    rejectIfArchived(status, "Collection");
  }

  /**
   * Throw HTTP 409 if the {@code :BasicContainer} (or any subclass) with the
   * given OGM id has {@code status = "ARCHIVED"}. Null status, missing node,
   * or any other status value all pass silently.
   *
   * @param containerOgmId Neo4j OGM Long id of the container node
   */
  public void assertContainerNotArchived(long containerOgmId) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", containerOgmId);
    // Match any node carrying any of the container labels; rely on id() for selectivity.
    Result r = NeoConnector.getInstance()
      .getNeo4jSession()
      .query(
        "MATCH (c) WHERE id(c) = $id AND " +
        "(c:FileContainer OR c:TimeseriesContainer OR c:StructuredDataContainer OR c:HdfContainer) " +
        "RETURN c.status AS s LIMIT 1",
        params
      );
    String status = readStatus(r);
    rejectIfArchived(status, "Container");
  }

  private static String readStatus(Result r) {
    for (Map<String, Object> row : r.queryResults()) {
      Object s = row.get("s");
      return s == null ? null : s.toString();
    }
    return null;
  }

  private static void rejectIfArchived(String status, String entityKind) {
    if (ARCHIVED.equals(status)) {
      throw new WebApplicationException(
        entityKind + " is ARCHIVED — writes to its children are blocked. " +
        "PATCH /v2/.../publication-state to unarchive (owner or instance-admin).",
        Response.Status.CONFLICT
      );
    }
  }
}
