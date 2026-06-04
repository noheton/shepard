package de.dlr.shepard.v2.containers.services;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * V2CONV-A3 — the dispatcher behind the unified {@code /v2/containers}
 * surface. CDI-discovers every {@link ContainerKindHandler} on the classpath
 * (core kinds in-tree; plugin kinds in their own module) and routes
 * create/get/patch/delete/list by kind. The direct sibling of
 * {@code ReferencesV2Service} (V2CONV-A2).
 *
 * <h2>Discovery + fail-fast</h2>
 *
 * <p>Handlers are injected as {@code @Any Instance<ContainerKindHandler>} and
 * indexed by {@link ContainerKindHandler#kind()} on first use. Two handlers
 * claiming the same kind token is a packaging defect — the dispatcher throws
 * {@link IllegalStateException} naming both, mirroring the
 * {@code ReferencesV2Service} duplicate-kind fail-fast posture.
 *
 * <h2>Entity → kind resolution</h2>
 *
 * <p>For appId-addressed operations (get/patch/delete) the kind is derived from
 * the loaded entity: each handler's {@link ContainerKindHandler#owns} is
 * consulted in turn. The first owner wins.
 */
@ApplicationScoped
public class ContainersV2Service {

  @Inject
  @Any
  Instance<ContainerKindHandler> handlers;

  /**
   * Test seam — non-null only on the unit-test constructor path so a test can
   * stage a plain {@code List.of(stubA, stubB)} without a CDI container.
   */
  private final Iterable<ContainerKindHandler> testHandlers;

  /** kind → handler, built lazily + memoised. */
  private volatile Map<String, ContainerKindHandler> byKind;

  /** CDI constructor. */
  public ContainersV2Service() {
    this.testHandlers = null;
  }

  /**
   * Test-only constructor — bypasses CDI {@code Instance<>} discovery so a unit
   * test can stage synthetic handlers via {@code List.of(...)}.
   *
   * @param handlers the handlers the index should be built from.
   */
  public ContainersV2Service(Iterable<ContainerKindHandler> handlers) {
    this.testHandlers = handlers;
  }

  private Map<String, ContainerKindHandler> index() {
    Map<String, ContainerKindHandler> local = byKind;
    if (local != null) return local;
    synchronized (this) {
      if (byKind != null) return byKind;
      Map<String, ContainerKindHandler> map = new LinkedHashMap<>();
      Iterable<ContainerKindHandler> source = testHandlers != null ? testHandlers : handlers;
      for (ContainerKindHandler h : source) {
        if (h == null) continue;
        String k = h.kind();
        if (k == null || k.isBlank()) {
          Log.warnf("V2CONV-A3: ContainerKindHandler %s declares a null/blank kind — skipping", h.getClass().getName());
          continue;
        }
        ContainerKindHandler prior = map.putIfAbsent(k, h);
        if (prior != null) {
          String msg = String.format(
            "V2CONV-A3: duplicate container kind '%s' claimed by %s and %s. A kind token must be uniquely owned.",
            k,
            prior.getClass().getName(),
            h.getClass().getName()
          );
          Log.error(msg);
          throw new IllegalStateException(msg);
        }
        Log.infof("V2CONV-A3: ContainerKindHandler '%s' → %s", k, h.getClass().getName());
      }
      byKind = Map.copyOf(map);
      return byKind;
    }
  }

  /** The set of kind tokens with a registered handler (for admin/diagnostics). */
  public List<String> registeredKinds() {
    return List.copyOf(index().keySet());
  }

  /**
   * Resolve the handler for a kind token, or empty when none is registered
   * (e.g. a plugin kind whose module is not installed).
   */
  public Optional<ContainerKindHandler> handlerForKind(String kind) {
    if (kind == null || kind.isBlank()) return Optional.empty();
    return Optional.ofNullable(index().get(kind.toLowerCase(Locale.ROOT)));
  }

  private ContainerKindHandler requireHandlerForKind(String kind) {
    return handlerForKind(kind)
      .orElseThrow(() ->
        new BadRequestException(
          "unknown or uninstalled container kind '" + kind + "'. Registered kinds: " + registeredKinds()
        )
      );
  }

  /**
   * Resolve the entity at {@code appId} and the handler that owns it. Returns
   * empty when no registered handler can find/own a container with that appId.
   */
  public Optional<ResolvedContainer> resolveByAppId(String appId) {
    if (appId == null || appId.isBlank()) return Optional.empty();
    for (ContainerKindHandler h : index().values()) {
      BasicContainer found;
      try {
        found = h.findByAppId(appId);
      } catch (RuntimeException ex) {
        Log.debugf("V2CONV-A3: handler %s threw resolving appId %s — continuing", h.kind(), appId);
        continue;
      }
      if (found != null && h.owns(found)) {
        return Optional.of(new ResolvedContainer(h, found));
      }
    }
    return Optional.empty();
  }

  // ─── dispatch ──────────────────────────────────────────────────────────

  public ContainerV2IO create(String kind, Map<String, Object> body) {
    return requireHandlerForKind(kind).create(body);
  }

  public ContainerV2IO getByAppId(String appId) {
    ResolvedContainer r = resolveByAppId(appId)
      .orElseThrow(() -> new NotFoundException("No container with appId " + appId));
    return r.handler().toIO(r.container());
  }

  public ContainerV2IO patchByAppId(String appId, Map<String, Object> patch) {
    ResolvedContainer r = resolveByAppId(appId)
      .orElseThrow(() -> new NotFoundException("No container with appId " + appId));
    return r.handler().patch(appId, patch);
  }

  public void deleteByAppId(String appId) {
    ResolvedContainer r = resolveByAppId(appId)
      .orElseThrow(() -> new NotFoundException("No container with appId " + appId));
    r.handler().delete(appId);
  }

  public List<ContainerV2IO> list(String kind, String nameFilter) {
    return requireHandlerForKind(kind).list(nameFilter);
  }

  /** The (handler, entity) pair an appId resolves to. */
  public record ResolvedContainer(ContainerKindHandler handler, BasicContainer container) {}
}
