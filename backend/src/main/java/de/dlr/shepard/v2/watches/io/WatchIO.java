package de.dlr.shepard.v2.watches.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.watches.entities.Watch;

/**
 * Wire shape for GET /v2/collections/{collectionAppId}/watched-containers
 * and the create/delete responses.
 *
 * <p>{@code containerName} and {@code containerAvailability} are
 * filled in by the service layer at serialisation time — they mirror
 * the existing {@code DataReference.referencedContainerName /
 * referencedContainerAvailability} pattern so the frontend's
 * container-display chrome works unchanged.
 *
 * <p>{@code containerAvailability} values:
 * <ul>
 *   <li>{@code "available"} — caller has Read; name resolved.</li>
 *   <li>{@code "forbidden"} — caller lacks Read on the container.</li>
 *   <li>{@code "deleted"} — container was soft-deleted (DI1).</li>
 *   <li>{@code "error"} — fetch failed for another reason.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatchIO(
  String watchAppId,
  Watch.Kind containerKind,
  String containerAppId,
  /**
   * Container's legacy Neo4j long id, when resolvable. Container detail
   * pages (`/containers/timeseries/{containerOgmId}`, etc.) are still
   * on the v1 long-id route shape; until they accept appId, the
   * frontend needs both ids to render the "open container" link
   * without a 404. Null when the container is missing or the caller
   * lacks Read.
   */
  Long containerOgmId,
  String containerName,
  String containerAvailability,
  Long since,
  String addedBy
) {
  /** Build the wire shape without the container resolution (service layer fills the rest). */
  public static WatchIO from(Watch w) {
    return new WatchIO(
      w.getAppId(),
      w.getContainerKind(),
      w.getContainerAppId(),
      null,
      null,
      null,
      w.getSince(),
      w.getAddedBy()
    );
  }

  /** Builder helper for resolved-container case. */
  public WatchIO withResolution(Long ogmId, String containerName, String availability) {
    return new WatchIO(
      watchAppId,
      containerKind,
      containerAppId,
      ogmId,
      containerName,
      availability,
      since,
      addedBy
    );
  }
}
