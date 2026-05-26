package de.dlr.shepard.v2.events;

import com.fasterxml.jackson.annotation.JsonInclude;

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
   * Epoch-millis timestamp when the event was emitted.
   */
  long timestamp
) {}
