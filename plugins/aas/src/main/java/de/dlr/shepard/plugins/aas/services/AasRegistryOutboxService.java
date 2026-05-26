package de.dlr.shepard.plugins.aas.services;

import de.dlr.shepard.plugins.aas.daos.AasRegistrationDAO;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import de.dlr.shepard.plugins.aas.entities.AasRegistration;
import de.dlr.shepard.plugins.aas.services.AasRegistryClient.RegistrationResult;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.plugins.aas.v2.io.AasShellDescriptorIO;
import de.dlr.shepard.plugins.aas.v2.io.AasShellDescriptorIO.AssetInformationIO;
import de.dlr.shepard.plugins.aas.v2.io.AasShellDescriptorIO.EndpointIO;
import de.dlr.shepard.plugins.aas.v2.io.AasShellDescriptorIO.ProtocolInformationIO;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * AAS1-reg outbox sync service.
 *
 * <p>On startup (and on demand via {@code POST /v2/admin/aas/registrations/sync}
 * in AAS1-reg Commit 3), {@link #syncAll()} walks every non-deleted Collection,
 * seeds a PENDING {@link AasRegistration} row for any not yet tracked, then
 * pushes all PENDING/FAILED rows to the configured external IDTA AAS Registry.
 *
 * <p>Registry registration is <em>best-effort</em>: failures flip the outbox
 * row to FAILED and are logged at WARN; they never block the shepard write-path
 * or startup sequence.
 *
 * <p>The startup sync runs on a virtual thread so it does not block the Quarkus
 * readiness probe. Concurrent invocations are serialised by {@code synchronized}
 * on {@link #syncAll()}.
 *
 * <p>AAS1l: {@link #configService} provides runtime-mutable config values
 * ({@code registryUrl}, {@code registryApiKey}, {@code baseUrl}) that override
 * the deploy-time {@code @ConfigProperty} fields. When the service is not
 * available (null, e.g. in unit tests), the deploy-time values are used.
 */
@ApplicationScoped
public class AasRegistryOutboxService {

  static final String AAS_INTERFACE = "AAS-3.1";
  static final String ENDPOINT_PROTOCOL = "HTTP";
  static final List<String> ENDPOINT_PROTOCOL_VERSIONS = List.of("1.1");

  @Inject
  AasRegistrationDAO registrationDAO;

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  AasRegistryClient registryClient;

  @Inject
  RequestContextController requestContextController;

  /**
   * AAS1l: runtime-mutable config. When the :AasConfig singleton is available,
   * its values take precedence over the deploy-time @ConfigProperty fallbacks.
   * Null in unit tests that set the @ConfigProperty fields directly.
   */
  @Inject
  AasConfigService configService;

  @ConfigProperty(name = "shepard.aas.registry.url")
  Optional<String> registryUrl;

  @ConfigProperty(name = "shepard.aas.registry.api-key")
  Optional<String> registryApiKey;

  @ConfigProperty(name = "shepard.aas.base-url")
  Optional<String> baseUrl;

  void onStart(@Observes StartupEvent event) {
    // AAS1l: prefer runtime :AasConfig over deploy-time @ConfigProperty
    Optional<String> effectiveUrl = effectiveRegistryUrl();
    if (effectiveUrl.isEmpty() || effectiveUrl.get().isBlank()) {
      Log.debug("AAS1-reg: no registry URL configured; skipping startup sync");
      return;
    }
    final String urlForLog = effectiveUrl.get();
    Thread.ofVirtual()
      .name("aas-registry-startup-sync")
      .start(() -> {
        boolean activated = requestContextController.activate();
        try {
          syncAll();
        } catch (RuntimeException e) {
          Log.warnf(e, "AAS1-reg: startup sync failed; shells not registered at %s", urlForLog);
        } finally {
          if (activated) {
            requestContextController.deactivate();
          }
        }
      });
  }

  /**
   * AAS1l: resolve the effective registry URL, preferring the
   * runtime-mutable :AasConfig singleton over the deploy-time
   * @ConfigProperty fallback.
   *
   * <p>Null-safe — when {@code configService} is null (unit tests that
   * inject the @ConfigProperty fields directly) the deploy-time
   * Optional is returned unchanged.
   */
  Optional<String> effectiveRegistryUrl() {
    if (configService != null) {
      try {
        AasConfig cfg = configService.current();
        if (cfg.getRegistryUrl() != null && !cfg.getRegistryUrl().isBlank()) {
          return Optional.of(cfg.getRegistryUrl());
        }
      } catch (Exception e) {
        Log.debugf("AAS1l: could not read :AasConfig for registryUrl; using deploy-time config: %s", e.getMessage());
      }
    }
    return registryUrl;
  }

  /**
   * AAS1l: resolve the effective registry API key, preferring the
   * runtime-mutable :AasConfig singleton over the deploy-time fallback.
   */
  Optional<String> effectiveRegistryApiKey() {
    if (configService != null) {
      try {
        AasConfig cfg = configService.current();
        if (cfg.getRegistryApiKey() != null && !cfg.getRegistryApiKey().isBlank()) {
          return Optional.of(cfg.getRegistryApiKey());
        }
      } catch (Exception e) {
        Log.debugf("AAS1l: could not read :AasConfig for registryApiKey; using deploy-time config: %s", e.getMessage());
      }
    }
    return registryApiKey;
  }

  /**
   * AAS1l: resolve the effective base URL, preferring the
   * runtime-mutable :AasConfig singleton over the deploy-time fallback.
   */
  Optional<String> effectiveBaseUrl() {
    if (configService != null) {
      try {
        AasConfig cfg = configService.current();
        if (cfg.getBaseUrl() != null && !cfg.getBaseUrl().isBlank()) {
          return Optional.of(cfg.getBaseUrl());
        }
      } catch (Exception e) {
        Log.debugf("AAS1l: could not read :AasConfig for baseUrl; using deploy-time config: %s", e.getMessage());
      }
    }
    return baseUrl;
  }

  /**
   * Seed the outbox for every non-deleted Collection then push all
   * PENDING/FAILED rows to the configured IDTA AAS Registry.
   *
   * <p>Idempotent — safe to call multiple times; existing SYNCED rows are
   * not touched.
   *
   * @return count of shells successfully registered in this invocation
   */
  public synchronized int syncAll() {
    Optional<String> effectiveUrl = effectiveRegistryUrl();
    if (effectiveUrl.isEmpty() || effectiveUrl.get().isBlank()) {
      Log.debug("AAS1-reg: syncAll() called with no registry URL configured; no-op");
      return 0;
    }
    String url = effectiveUrl.get();
    seedPendingRows(url);
    return pushPendingAndFailed(url);
  }

  /**
   * For every non-deleted Collection that has no outbox row for {@code url},
   * create a PENDING row so the next push attempt picks it up.
   */
  void seedPendingRows(String url) {
    List<String> collectionAppIds = registrationDAO.listNonDeletedCollectionAppIds();
    for (String shellAppId : collectionAppIds) {
      if (registrationDAO.findByShellAndRegistry(shellAppId, url) == null) {
        AasRegistration reg = new AasRegistration();
        reg.setShellAppId(shellAppId);
        reg.setRegistryUrl(url);
        reg.setStatus(AasRegistration.Status.PENDING);
        long now = System.currentTimeMillis();
        reg.setCreatedAt(now);
        reg.setUpdatedAt(now);
        registrationDAO.createOrUpdate(reg);
        Log.debugf("AAS1-reg: seeded PENDING row for shell=%s registry=%s", shellAppId, url);
      }
    }
  }

  /**
   * Push every PENDING/FAILED row for {@code url} to the registry.
   * Updates each row's status to SYNCED or FAILED in the outbox.
   *
   * @return count of shells successfully registered
   */
  int pushPendingAndFailed(String url) {
    List<AasRegistration> pending = registrationDAO.listPendingOrFailed(url);
    if (pending.isEmpty()) {
      return 0;
    }
    List<String> shellAppIds = pending.stream().map(AasRegistration::getShellAppId).toList();
    Map<String, Collection> collectionByAppId = loadCollectionsByAppIds(shellAppIds);

    int synced = 0;
    for (AasRegistration reg : pending) {
      Collection collection = collectionByAppId.get(reg.getShellAppId());
      if (collection == null) {
        markFailed(reg, "collection no longer exists");
        continue;
      }
      AasShellDescriptorIO descriptor = buildDescriptor(collection);
      long now = System.currentTimeMillis();
      reg.setLastAttemptAt(now);
      reg.setUpdatedAt(now);

      // AAS1l: use the runtime-mutable API key over the deploy-time fallback
      RegistrationResult result = registryClient.register(url, effectiveRegistryApiKey(), descriptor);
      if (result.success()) {
        reg.setStatus(AasRegistration.Status.SYNCED);
        reg.setErrorMessage(null);
        synced++;
        Log.infof("AAS1-reg: registered shell=%s at %s", reg.getShellAppId(), url);
      } else {
        reg.setStatus(AasRegistration.Status.FAILED);
        reg.setErrorMessage(result.error());
        Log.warnf("AAS1-reg: failed shell=%s at %s: %s", reg.getShellAppId(), url, result.error());
      }
      registrationDAO.createOrUpdate(reg);
    }
    return synced;
  }

  /**
   * Build the IDTA {@link AasShellDescriptorIO} for a given Collection.
   *
   * <p>The {@code endpoints} list contains one entry for the AAS Repository
   * interface ({@code AAS-3.1}) pointing to
   * {@code {baseUrl}/v2/aas/shells/{percent-encoded shellId}}.
   * When no base URL is configured (deploy-time or runtime), the endpoint
   * list is empty — the registry stores the descriptor but clients
   * cannot resolve it until the operator sets the base URL.
   */
  AasShellDescriptorIO buildDescriptor(Collection collection) {
    String appId = collection.getAppId();
    String shellId = AasShellMappingService.COLLECTION_URN_PREFIX + appId;
    String idShort = AasShellMappingService.sanitiseIdShort(collection.getName());
    String assetId = AasShellMappingService.ASSET_URN_PREFIX + appId;
    var assetInfo = new AssetInformationIO(AasShellMappingService.ASSET_KIND_INSTANCE, assetId);
    List<EndpointIO> endpoints = buildEndpoints(shellId);
    return new AasShellDescriptorIO(shellId, idShort, assetInfo, endpoints);
  }

  private List<EndpointIO> buildEndpoints(String shellId) {
    // AAS1l: prefer runtime :AasConfig baseUrl over deploy-time @ConfigProperty
    Optional<String> effectiveBase = effectiveBaseUrl();
    if (effectiveBase.isEmpty() || effectiveBase.get().isBlank()) {
      return List.of();
    }
    String encoded = URLEncoder.encode(shellId, StandardCharsets.UTF_8);
    String href = effectiveBase.get().stripTrailing() + "/v2/aas/shells/" + encoded;
    var proto = new ProtocolInformationIO(href, ENDPOINT_PROTOCOL, ENDPOINT_PROTOCOL_VERSIONS);
    return List.of(new EndpointIO(AAS_INTERFACE, proto));
  }

  private Map<String, Collection> loadCollectionsByAppIds(List<String> appIds) {
    if (appIds.isEmpty()) {
      return Map.of();
    }
    Iterable<Collection> found = collectionDAO.findByQuery(
      "MATCH (c:Collection) WHERE c.appId IN $appIds"
        + " AND (c.deleted IS NULL OR c.deleted = false) RETURN c",
      Map.of("appIds", appIds)
    );
    Map<String, Collection> result = new HashMap<>();
    found.forEach(c -> {
      if (c.getAppId() != null) result.put(c.getAppId(), c);
    });
    return result;
  }

  private void markFailed(AasRegistration reg, String reason) {
    long now = System.currentTimeMillis();
    reg.setStatus(AasRegistration.Status.FAILED);
    reg.setErrorMessage(reason);
    reg.setLastAttemptAt(now);
    reg.setUpdatedAt(now);
    registrationDAO.createOrUpdate(reg);
  }
}
