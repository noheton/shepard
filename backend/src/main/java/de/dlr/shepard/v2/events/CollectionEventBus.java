package de.dlr.shepard.v2.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P13 — in-process SSE event bus for Collection change-feed.
 *
 * <p>Maintains a registry of {@link SinkEntry} objects (sink + sse factory pair)
 * keyed by {@code collectionAppId}. The {@link Sse} factory is captured at
 * subscribe time so that {@link #emit(CollectionEventIO)} can build SSE events
 * without needing access to a request-scoped context. This keeps the call chain
 * from service methods (DataObjectService, CollectionService) free of JAX-RS
 * types.
 *
 * <p>Thread-safety: both the outer map and each inner {@link Set} are
 * {@link ConcurrentHashMap}-backed. Emit takes a snapshot of the entry set
 * before iterating so that concurrent (un)subscribe calls do not race with
 * delivery. Dead sinks prune themselves via the {@code exceptionally} handler.
 *
 * <p>Heartbeats are emitted every 30 s via {@link Scheduled} to keep connections
 * alive through NAT/proxy timeouts (most proxies have a 60 s idle timeout).
 * Collections with no subscribers are skipped entirely.
 */
@ApplicationScoped
public class CollectionEventBus {

  @Inject
  ObjectMapper objectMapper;

  /**
   * Captures the SSE sink + factory pair registered at subscribe time.
   * {@code Sse} is effectively stateless (it is a factory); storing it here
   * is safe even though it is injected as {@code @Context} in a request-scoped
   * resource — the factory itself has no request-scoped lifecycle.
   */
  record SinkEntry(SseEventSink sink, Sse sse) {}

  /**
   * collectionAppId → set of active (sink, sse) entries for that collection.
   * Inner sets use {@link ConcurrentHashMap#newKeySet()} to avoid
   * concurrent-modification races between emit iteration and subscribe/
   * unsubscribe calls.
   */
  private final Map<String, Set<SinkEntry>> subscribers = new ConcurrentHashMap<>();

  /**
   * Register a new sink for the given Collection's event feed.
   * An immediate {@code HEARTBEAT} event is sent on subscribe so the client
   * knows the connection is live before the first real event arrives.
   *
   * @param collectionAppId the Collection appId the caller is subscribing to
   * @param sink            the SSE sink opened by the REST resource
   * @param sse             the JAX-RS {@link Sse} factory (from {@code @Context})
   */
  public void subscribe(String collectionAppId, SseEventSink sink, Sse sse) {
    SinkEntry entry = new SinkEntry(sink, sse);
    subscribers
      .computeIfAbsent(collectionAppId, k -> ConcurrentHashMap.newKeySet())
      .add(entry);
    // Send an immediate heartbeat so the client sees a response right away.
    CollectionEventIO hb = new CollectionEventIO(
      "HEARTBEAT", null, null, null, null, CollectionEventIO.toIso(System.currentTimeMillis())
    );
    sendToEntry(entry, hb, collectionAppId);
  }

  /**
   * Remove a sink entry from the registry. Called when the client disconnects
   * or from the dead-sink cleanup path.
   *
   * @param collectionAppId the Collection appId
   * @param entry           the entry to remove
   */
  public void unsubscribe(String collectionAppId, SinkEntry entry) {
    Set<SinkEntry> entries = subscribers.get(collectionAppId);
    if (entries != null) {
      entries.remove(entry);
      if (entries.isEmpty()) {
        subscribers.remove(collectionAppId, entries);
      }
    }
  }

  /**
   * Emit a change event to all subscribers of the event's collection.
   * Best-effort: if a sink is closed or throws, it is pruned and the
   * next subscriber still receives the event.
   *
   * @param event the event to emit (must have non-null {@code collectionAppId})
   */
  public void emit(CollectionEventIO event) {
    if (event.collectionAppId() == null) return;
    Set<SinkEntry> entries = subscribers.getOrDefault(
      event.collectionAppId(), Collections.emptySet()
    );
    if (entries.isEmpty()) return;

    // Snapshot before iterating to avoid CME with concurrent subscribe/unsubscribe.
    Set<SinkEntry> snapshot = Set.copyOf(entries);
    for (SinkEntry entry : snapshot) {
      sendToEntry(entry, event, event.collectionAppId());
    }
  }

  /**
   * Periodic heartbeat: emits a {@code HEARTBEAT} event to every subscriber of
   * every active Collection. Skips collections with no subscribers.
   * Runs every 30 s via {@link Scheduled}.
   */
  @Scheduled(every = "30s")
  public void heartbeat() {
    if (subscribers.isEmpty()) return;
    String now = CollectionEventIO.toIso(System.currentTimeMillis());
    for (Map.Entry<String, Set<SinkEntry>> mapEntry : subscribers.entrySet()) {
      Set<SinkEntry> entries = mapEntry.getValue();
      if (entries.isEmpty()) continue;
      CollectionEventIO hb = new CollectionEventIO(
        "HEARTBEAT", null, null, null, null, now
      );
      Set<SinkEntry> snapshot = Set.copyOf(entries);
      for (SinkEntry entry : snapshot) {
        if (entry.sink().isClosed()) {
          unsubscribe(mapEntry.getKey(), entry);
          continue;
        }
        sendToEntry(entry, hb, mapEntry.getKey());
      }
    }
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void sendToEntry(SinkEntry entry, CollectionEventIO event, String collectionAppId) {
    if (entry.sink().isClosed()) {
      unsubscribe(collectionAppId, entry);
      return;
    }
    try {
      String json = objectMapper.writeValueAsString(event);
      OutboundSseEvent sseEvent = entry.sse().newEventBuilder()
        .name(event.eventType())
        .data(json)
        .mediaType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE)
        .build();
      entry.sink().send(sseEvent).exceptionally(t -> {
        Log.debugf(
          "P13: SSE sink closed or errored for collection %s — pruning: %s",
          collectionAppId, t.getMessage()
        );
        unsubscribe(collectionAppId, entry);
        return null;
      });
    } catch (JsonProcessingException e) {
      Log.warnf(
        "P13: Failed to serialize CollectionEventIO for collection %s: %s",
        collectionAppId, e.getMessage()
      );
    }
  }
}
