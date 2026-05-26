package de.dlr.shepard.plugins.aas.services;

import de.dlr.shepard.plugins.aas.daos.AasConfigDAO;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * AAS1l — service layer for the {@code :AasConfig} singleton.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :AasConfig} node exists yet, one is minted from
 *       the {@code shepard.aas.*} install-time defaults. Pre-AAS1l
 *       installs upgrading get the singleton minted on their
 *       next restart with the safe-default posture
 *       ({@code enabled=false} unless {@code shepard.aas.enabled}
 *       was set to {@code true}).</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth against a fresh DB
 *       that somehow missed the startup hook).</li>
 *   <li><b>Merge-patch.</b> {@link #patch(AasPatch)} applies the
 *       runtime-mutable subset of the config.</li>
 * </ol>
 *
 * <p><b>Precedence.</b> Runtime field values win; deploy-time
 * {@code shepard.aas.*} properties are install defaults that seed
 * the singleton on first start. The deploy-time key stays valid so
 * an operator can ship a baked-in default in their IaC, but it
 * doesn't override a runtime flip. Same precedence model as
 * UH1a {@code :UnhideConfig}.
 *
 * <p><b>Registry-client integration.</b> {@link AasRegistryOutboxService}
 * injects this service and calls {@link #current()} to read the
 * runtime-mutable config values instead of reading
 * {@code @ConfigProperty} directly. This means runtime PATCHes
 * are picked up on the next {@code syncAll()} call without a restart.
 */
@ApplicationScoped
public class AasConfigService {

  @Inject
  AasConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.aas.enabled", defaultValue = "false")
  boolean installDefaultEnabled;

  @ConfigProperty(name = "shepard.aas.registry.url")
  Optional<String> installDefaultRegistryUrl;

  @ConfigProperty(name = "shepard.aas.registry.api-key")
  Optional<String> installDefaultRegistryApiKey;

  @ConfigProperty(name = "shepard.aas.base-url")
  Optional<String> installDefaultBaseUrl;

  /**
   * Seed the singleton on first startup. Idempotent — re-running
   * sees the existing row and returns. Logged at INFO with the
   * action taken so an operator can grep startup logs to confirm
   * AAS1l came up correctly.
   *
   * <p>The seed runs lazily-scoped (Arc's
   * {@link RequestContextController}) so the DAO's request-scoped
   * machinery has a context to bind to even though the
   * {@code StartupEvent} fires outside a JAX-RS request.
   */
  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      // Seeding failure must not block startup — operators get a
      // WARN, the AAS surface simply operates from the deploy-time
      // config values until the issue is sorted. Same fail-soft
      // posture as UH1a.
      Log.warnf(e, "AAS1l: could not seed :AasConfig on startup; admin actions will retry on first read");
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  /**
   * Seed the singleton if it doesn't exist yet. Public for tests +
   * defence-in-depth callers (the service's other entry points all
   * call {@link #current()} which delegates here).
   *
   * @return the freshly-seeded or pre-existing {@link AasConfig}.
   */
  public synchronized AasConfig seedIfNeeded() {
    AasConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("AAS1l: :AasConfig already present (appId=%s, enabled=%s)", existing.getAppId(), existing.isEnabled());
      return existing;
    }
    AasConfig seed = new AasConfig();
    seed.setEnabled(installDefaultEnabled);
    seed.setRegistryUrl(emptyToNull(installDefaultRegistryUrl.orElse(null)));
    seed.setRegistryApiKey(emptyToNull(installDefaultRegistryApiKey.orElse(null)));
    seed.setBaseUrl(emptyToNull(installDefaultBaseUrl.orElse(null)));
    AasConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "AAS1l: seeded :AasConfig singleton (appId=%s, enabled=%s, registryUrl-present=%s, baseUrl-present=%s)",
      saved.getAppId(),
      saved.isEnabled(),
      saved.getRegistryUrl() != null,
      saved.getBaseUrl() != null
    );
    return saved;
  }

  /**
   * Return the current singleton, seeding from install defaults if
   * absent. Callers should treat the return value as a live OGM
   * entity (mutations via the DAO).
   */
  public AasConfig current() {
    AasConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Apply a runtime merge-patch to the singleton. All fields are
   * runtime-mutable per the AAS1l design.
   *
   * <p>{@code null} fields on the patch are interpreted per RFC 7396:
   * a {@code null} {@code enabled} (boxed) means "leave the field
   * alone". {@code null} string fields with a {@code *Touched} flag
   * mean "clear the field".
   *
   * @param patch the merge-patch DTO from {@link AasPatch}.
   * @return the updated {@link AasConfig} entity.
   */
  public synchronized AasConfig patch(AasPatch patch) {
    AasConfig cfg = current();
    if (patch.enabled != null) {
      cfg.setEnabled(patch.enabled);
    }
    if (patch.registryUrlTouched) {
      cfg.setRegistryUrl(emptyToNull(patch.registryUrl));
    }
    if (patch.registryApiKeyTouched) {
      cfg.setRegistryApiKey(emptyToNull(patch.registryApiKey));
    }
    if (patch.baseUrlTouched) {
      cfg.setBaseUrl(emptyToNull(patch.baseUrl));
    }
    AasConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "AAS1l: :AasConfig patched (enabled=%s, registryUrl-present=%s, apiKey-present=%s, baseUrl-present=%s)",
      saved.isEnabled(),
      saved.getRegistryUrl() != null,
      saved.getRegistryApiKey() != null,
      saved.getBaseUrl() != null
    );
    return saved;
  }

  private static String emptyToNull(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  // ─── Inner types ────────────────────────────────────────────────

  /**
   * Patch DTO for {@link #patch(AasPatch)}. Carries boxed
   * {@code Boolean} so RFC 7396 "absent ≠ false" semantics are
   * preserved for the toggle; explicit {@code *Touched} flags for
   * the string fields since {@code null} (clear) is a legitimate
   * value distinct from "leave alone".
   */
  public static final class AasPatch {

    public Boolean enabled;
    public boolean registryUrlTouched;
    public String registryUrl;
    public boolean registryApiKeyTouched;
    public String registryApiKey;
    public boolean baseUrlTouched;
    public String baseUrl;
  }
}
