package de.dlr.shepard.v2.admin.ror.services;

import de.dlr.shepard.v2.admin.ror.daos.InstanceRorConfigDAO;
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * ROR1 — service layer for the {@code :InstanceRorConfig} singleton.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :InstanceRorConfig} node exists yet, a blank one is
 *       minted. Pre-ROR1 installs upgrading get the singleton minted
 *       on their next restart with both fields {@code null}.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth against a fresh DB that
 *       somehow missed the startup hook).</li>
 *   <li><b>Merge-patch.</b> {@link #patch(String, String)} applies the
 *       runtime-mutable subset of the config per RFC 7396 semantics
 *       (null = clear, absent = leave alone — handled by the REST
 *       layer before calling this method).</li>
 * </ol>
 *
 * <p>Mirrors the {@code UnhideConfigService} startup pattern exactly.
 */
@ApplicationScoped
public class InstanceRorConfigService {

  @Inject
  InstanceRorConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  /**
   * Seed the singleton on first startup. Idempotent — re-running sees
   * the existing row and returns. Logged at INFO so an operator can
   * grep startup logs to confirm ROR1 came up correctly.
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
      // Seeding failure must not block startup — operators get a WARN.
      // The admin REST returns a reasonable 200 with null fields until
      // the issue is sorted (same fail-soft posture as UH1a / N1c2).
      Log.warnf(e, "ROR1: could not seed :InstanceRorConfig on startup; will retry on first admin read");
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
   * @return the freshly-seeded or pre-existing {@link InstanceRorConfig}.
   */
  public synchronized InstanceRorConfig seedIfNeeded() {
    InstanceRorConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("ROR1: :InstanceRorConfig already present (appId=%s)", existing.getAppId());
      return existing;
    }
    InstanceRorConfig seed = new InstanceRorConfig();
    // Both rorId and organizationName start null — operator populates
    // them via PATCH /v2/admin/instance/ror.
    InstanceRorConfig saved = dao.createOrUpdate(seed);
    Log.infof("ROR1: seeded :InstanceRorConfig singleton (appId=%s)", saved.getAppId());
    return saved;
  }

  /**
   * Return the current singleton, seeding from install defaults if
   * absent. Callers should treat the return value as a live OGM
   * entity (mutations via the DAO).
   */
  public InstanceRorConfig current() {
    InstanceRorConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Apply a runtime merge-patch to the singleton. Null arguments
   * are interpreted as "clear the field" (RFC 7396 semantics);
   * the REST layer is responsible for distinguishing "absent" from
   * "null" before calling here.
   *
   * @param rorId           new ROR identifier suffix, or {@code null} to clear
   * @param organizationName new org name, or {@code null} to clear
   * @return the post-patch {@link InstanceRorConfig}.
   */
  public synchronized InstanceRorConfig patch(String rorId, String organizationName) {
    InstanceRorConfig cfg = current();
    cfg.setRorId(emptyToNull(rorId));
    cfg.setOrganizationName(emptyToNull(organizationName));
    InstanceRorConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "ROR1: :InstanceRorConfig patched (rorId-present=%s, orgName-present=%s)",
      saved.getRorId() != null,
      saved.getOrganizationName() != null
    );
    return saved;
  }

  private static String emptyToNull(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
