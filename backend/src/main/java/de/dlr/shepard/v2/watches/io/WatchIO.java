package de.dlr.shepard.v2.watches.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.watches.entities.Watch;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

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
@Schema(description = "A watched-container subscription entry, including the container kind, appId, resolved name, and availability status.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatchIO(
  @Schema(description = "Stable application-level identifier for this watch record (UUID v7).", readOnly = true)
  String watchAppId,
  @Schema(description = "The kind of container being watched (e.g. TIMESERIES, FILE, STRUCTURED_DATA).")
  Watch.Kind containerKind,
  @Schema(description = "Stable application-level identifier of the watched container (UUID v7).")
  String containerAppId,
  @Schema(description = "Human-readable name of the watched container, resolved at query time. Null when availability is 'forbidden' or 'error'.", nullable = true)
  String containerName,
  @Schema(description = "Availability of the watched container for the current caller. One of: 'available', 'forbidden', 'deleted', 'error'.", nullable = true)
  String containerAvailability,
  @Schema(description = "Epoch-milliseconds when this watch was created.", example = "1751400000000")
  Long since,
  @Schema(description = "Username of the user who created this watch.")
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
      w.getSince(),
      w.getAddedBy()
    );
  }

  /** Builder helper for resolved-container case. */
  public WatchIO withResolution(String containerName, String availability) {
    return new WatchIO(
      watchAppId,
      containerKind,
      containerAppId,
      containerName,
      availability,
      since,
      addedBy
    );
  }
}
