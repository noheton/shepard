package de.dlr.shepard.v2.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * P13 — wire-format record for a single Collection change-feed event.
 *
 * <p>Sent as the JSON payload of an SSE {@code data:} line on
 * {@code GET /v2/collections/{appId}/events}. Clients parse
 * {@code event:} + {@code data:} per the EventSource wire format.
 *
 * <p>All fields are nullable — {@code HEARTBEAT} events carry only
 * {@code eventType} and {@code timestamp}; all entity-level fields
 * are null and omitted from serialisation via {@code @JsonInclude}.
 */
@Schema(
    name = "CollectionEvent",
    description =
        "SSE wire-format record for a single Collection change-feed event (P13). "
            + "Sent as the JSON payload of an SSE data: line on "
            + "GET /v2/collections/{appId}/events. "
            + "HEARTBEAT events carry only eventType and timestamp; all entity fields are null.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectionEventIO(
  /**
   * Discriminator for the event type.
   * One of: DATA_OBJECT_CREATED, DATA_OBJECT_UPDATED, DATA_OBJECT_DELETED,
   * COLLECTION_UPDATED, HEARTBEAT.
   */
  String eventType,

  /**
   * appId of the entity that changed (DataObject or Collection).
   * Null for HEARTBEAT events.
   */
  String entityAppId,

  /**
   * Kind of the changed entity: {@code "DataObject"} or {@code "Collection"}.
   * Null for HEARTBEAT events.
   */
  String entityKind,

  /**
   * appId of the Collection whose feed this event belongs to.
   * Null for HEARTBEAT events.
   */
  String collectionAppId,

  /**
   * Username of the user who triggered the change.
   * Null for HEARTBEAT events.
   */
  String actorUsername,

  /**
   * ISO 8601 UTC timestamp when the event was emitted
   * (e.g. {@code "2026-07-13T10:21:00Z"}).
   */
  @Schema(description = "ISO 8601 UTC timestamp when the event was emitted (e.g. \"2026-07-13T10:21:00Z\").")
  String timestamp
) {

  /** Converts an epoch-millisecond value to an ISO 8601 UTC string. */
  public static String toIso(long epochMs) {
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs));
  }
}
