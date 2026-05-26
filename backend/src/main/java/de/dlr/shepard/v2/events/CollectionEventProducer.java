package de.dlr.shepard.v2.events;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * P13 — emits {@link CollectionEventIO} events to the {@link CollectionEventBus}
 * on behalf of service-layer write operations.
 *
 * <p>Service beans that perform writes inject this bean and call the
 * appropriate emit method after a successful persist. All calls are
 * best-effort: exceptions are caught and logged so that a broken SSE sink
 * never causes a write to fail.
 *
 * <p>The {@link Sse} factory is NOT required here — it is captured at subscribe
 * time inside each {@link CollectionEventBus.SinkEntry} and used when
 * {@link CollectionEventBus#emit(CollectionEventIO)} delivers events to each
 * registered sink.
 *
 * <p>Currently wired to emit for:
 * <ul>
 *   <li>{@code DATA_OBJECT_CREATED} — from {@link de.dlr.shepard.context.collection.services.DataObjectService#createDataObject}</li>
 *   <li>{@code DATA_OBJECT_UPDATED} — from {@link de.dlr.shepard.context.collection.services.DataObjectService#updateDataObject}</li>
 *   <li>{@code DATA_OBJECT_DELETED} — from {@link de.dlr.shepard.context.collection.services.DataObjectService#deleteDataObject}</li>
 *   <li>{@code COLLECTION_UPDATED} — from {@link de.dlr.shepard.context.collection.services.CollectionService#updateCollectionByShepardId}</li>
 * </ul>
 *
 * <p>ANNOTATION_CREATED is deferred: {@link de.dlr.shepard.context.semantic.services.SemanticAnnotationService}
 * receives an entity shepardId but not a collection context; resolving the
 * collection appId would require an additional DAO query per annotation write.
 * See {@code aidocs/16-dispatcher-backlog.md} P13-defer row for the tracking note.
 */
@ApplicationScoped
public class CollectionEventProducer {

  @Inject
  CollectionEventBus eventBus;

  /**
   * Emit a DATA_OBJECT_CREATED event.
   *
   * @param collectionAppId appId of the Collection the DataObject was created in
   * @param dataObjectAppId appId of the newly created DataObject (may be null pre-backfill)
   * @param actorUsername   username of the user who created it
   */
  public void dataObjectCreated(
    String collectionAppId,
    String dataObjectAppId,
    String actorUsername
  ) {
    emitSafely(collectionAppId, new CollectionEventIO(
      "DATA_OBJECT_CREATED",
      dataObjectAppId,
      "DataObject",
      collectionAppId,
      actorUsername,
      System.currentTimeMillis()
    ));
  }

  /**
   * Emit a DATA_OBJECT_UPDATED event.
   *
   * @param collectionAppId appId of the containing Collection
   * @param dataObjectAppId appId of the updated DataObject
   * @param actorUsername   username of the user who updated it
   */
  public void dataObjectUpdated(
    String collectionAppId,
    String dataObjectAppId,
    String actorUsername
  ) {
    emitSafely(collectionAppId, new CollectionEventIO(
      "DATA_OBJECT_UPDATED",
      dataObjectAppId,
      "DataObject",
      collectionAppId,
      actorUsername,
      System.currentTimeMillis()
    ));
  }

  /**
   * Emit a DATA_OBJECT_DELETED event.
   *
   * @param collectionAppId appId of the containing Collection
   * @param dataObjectAppId appId of the deleted DataObject (may be null pre-backfill)
   * @param actorUsername   username of the user who deleted it
   */
  public void dataObjectDeleted(
    String collectionAppId,
    String dataObjectAppId,
    String actorUsername
  ) {
    emitSafely(collectionAppId, new CollectionEventIO(
      "DATA_OBJECT_DELETED",
      dataObjectAppId,
      "DataObject",
      collectionAppId,
      actorUsername,
      System.currentTimeMillis()
    ));
  }

  /**
   * Emit a COLLECTION_UPDATED event.
   *
   * @param collectionAppId appId of the updated Collection
   * @param actorUsername   username of the user who updated it
   */
  public void collectionUpdated(
    String collectionAppId,
    String actorUsername
  ) {
    emitSafely(collectionAppId, new CollectionEventIO(
      "COLLECTION_UPDATED",
      collectionAppId,
      "Collection",
      collectionAppId,
      actorUsername,
      System.currentTimeMillis()
    ));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void emitSafely(String collectionAppId, CollectionEventIO event) {
    if (collectionAppId == null) {
      Log.debugf("P13: skipping emit — collectionAppId is null");
      return;
    }
    try {
      eventBus.emit(event);
    } catch (Exception e) {
      Log.warnf("P13: emit failed for collection %s: %s", collectionAppId, e.getMessage());
    }
  }
}
