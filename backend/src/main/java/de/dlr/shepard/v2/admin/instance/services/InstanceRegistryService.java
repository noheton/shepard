package de.dlr.shepard.v2.admin.instance.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.admin.instance.daos.InstanceRegistryDAO;
import de.dlr.shepard.v2.admin.instance.entities.InstanceRegistry;
import de.dlr.shepard.v2.admin.instance.io.InstanceRegistryIO;
import de.dlr.shepard.v2.admin.instance.io.RegisteredInstanceIO;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;

/**
 * FE-PROV-INSTANCE-REGISTRY — service layer for the {@code :InstanceRegistry} singleton.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)} observes
 *       the Quarkus {@code StartupEvent}; if no {@code :InstanceRegistry} node
 *       exists yet, a blank one is minted with an empty instances list.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the typed registry
 *       (seeding if absent — defence-in-depth against a fresh DB that somehow
 *       missed the startup hook).</li>
 *   <li><b>Merge-patch.</b> {@link #patch(List)} replaces the instances list
 *       atomically (RFC 7396 array semantics — no element-level merge).</li>
 * </ol>
 *
 * <p>JSON serialisation: the typed {@code List<RegisteredInstanceIO>} is
 * stored as a JSON string in
 * {@link InstanceRegistry#getInstancesJson()} (same pattern as
 * {@code ImportPlan.summaryJson}). The entity never exposes the typed list.
 *
 * <p>Mirrors the {@code InstanceRorConfigService} startup pattern exactly.
 */
@ApplicationScoped
public class InstanceRegistryService {

  /** TypeReference reused for deserialisation (avoids per-call allocation). */
  private static final TypeReference<List<RegisteredInstanceIO>> LIST_TYPE =
    new TypeReference<>() {};

  /** Empty JSON array sentinel used when the stored JSON is null or blank. */
  private static final String EMPTY_JSON_ARRAY = "[]";

  @Inject
  InstanceRegistryDAO dao;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  RequestContextController requestContextController;

  /**
   * Seed the singleton on first startup. Idempotent — re-running sees the
   * existing row and returns. Logged at INFO so an operator can grep startup
   * logs to confirm instance-registry came up correctly.
   *
   * <p>The seed runs with an explicit {@link RequestContextController} scope
   * so the DAO's request-scoped machinery has a context even though the
   * {@code StartupEvent} fires outside a JAX-RS request.
   */
  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(
        e,
        "FE-PROV-INSTANCE-REGISTRY: could not seed :InstanceRegistry on startup; " +
        "will retry on first admin read"
      );
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  /**
   * Seed the singleton if it doesn't exist yet.
   *
   * @return the freshly-seeded or pre-existing {@link InstanceRegistry}.
   */
  public synchronized InstanceRegistry seedIfNeeded() {
    InstanceRegistry existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf(
        "FE-PROV-INSTANCE-REGISTRY: :InstanceRegistry already present (appId=%s)",
        existing.getAppId()
      );
      return existing;
    }
    InstanceRegistry seed = new InstanceRegistry();
    seed.setInstancesJson(EMPTY_JSON_ARRAY);
    InstanceRegistry saved = dao.createOrUpdate(seed);
    Log.infof(
      "FE-PROV-INSTANCE-REGISTRY: seeded :InstanceRegistry singleton (appId=%s)",
      saved.getAppId()
    );
    return saved;
  }

  /**
   * Return the current registry as a typed IO shape, seeding if absent.
   *
   * @return current {@link InstanceRegistryIO}, never {@code null}.
   */
  public InstanceRegistryIO current() {
    InstanceRegistry entity = dao.findSingleton();
    if (entity == null) {
      entity = seedIfNeeded();
    }
    return toIO(entity);
  }

  /**
   * Replace the instances list atomically (RFC 7396 array semantics).
   * A {@code null} argument is treated as "clear the list" (set to empty).
   *
   * @param instances new list of registered instances, or {@code null} to clear.
   * @return the post-patch {@link InstanceRegistryIO}.
   */
  public synchronized InstanceRegistryIO patch(List<RegisteredInstanceIO> instances) {
    InstanceRegistry entity = dao.findSingleton();
    if (entity == null) {
      entity = seedIfNeeded();
    }

    List<RegisteredInstanceIO> effective =
      (instances == null) ? Collections.emptyList() : instances;

    String json;
    try {
      json = objectMapper.writeValueAsString(effective);
    } catch (Exception e) {
      Log.errorf(e, "FE-PROV-INSTANCE-REGISTRY: failed to serialise instances list");
      json = EMPTY_JSON_ARRAY;
    }

    entity.setInstancesJson(json);
    InstanceRegistry saved = dao.createOrUpdate(entity);
    Log.infof(
      "FE-PROV-INSTANCE-REGISTRY: :InstanceRegistry patched (instanceCount=%d)",
      effective.size()
    );
    return toIO(saved);
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private InstanceRegistryIO toIO(InstanceRegistry entity) {
    String json = entity.getInstancesJson();
    if (json == null || json.isBlank()) {
      return new InstanceRegistryIO(Collections.emptyList());
    }
    try {
      List<RegisteredInstanceIO> list = objectMapper.readValue(json, LIST_TYPE);
      return new InstanceRegistryIO(list == null ? Collections.emptyList() : list);
    } catch (Exception e) {
      Log.warnf(
        e,
        "FE-PROV-INSTANCE-REGISTRY: failed to deserialise instancesJson — returning empty list"
      );
      return new InstanceRegistryIO(Collections.emptyList());
    }
  }
}
