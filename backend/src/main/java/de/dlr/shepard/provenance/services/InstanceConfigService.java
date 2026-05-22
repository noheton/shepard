package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.daos.InstanceConfigDAO;
import de.dlr.shepard.provenance.entities.InstanceConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.security.SecureRandom;
import java.util.Base64;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * PR-3 — singleton-management for {@link InstanceConfig}, the entity
 * that carries the per-instance HMAC signing key.
 *
 * <p>Follows the A3b / N1c2 / UH1a precedent: seed on first start,
 * get-or-seed on every read, runtime-mutable via the
 * {@link #rotate()} entrypoint (admin REST + CLI parity is a
 * follow-up PR; this slice ships the substrate the chain depends on).
 *
 * <p><b>Seeding precedence.</b>
 * <ol>
 *   <li>If a {@code :InstanceConfig} row exists, it wins.</li>
 *   <li>Otherwise, if the environment variable
 *       {@code SHEPARD_INSTANCE_SECRET} (or its Quarkus-config
 *       equivalent {@code shepard.audit.instance-secret}) is set,
 *       its value seeds {@code instanceSecret} with
 *       {@code secretVersion = 1}.</li>
 *   <li>Otherwise, a fresh 32-byte {@link SecureRandom}-derived
 *       value is base64-encoded and used. A WARN is logged so an
 *       operator can wire in their preferred bootstrap secret on
 *       the next restart.</li>
 * </ol>
 *
 * <p><b>Why a SecureRandom fallback rather than fail-fast.</b> The
 * audit chain is observability; failing startup on a missing
 * deploy-time secret would block the whole shepard instance for an
 * additive feature. The WARN is the discovery channel — same shape
 * as {@code N10sBootstrapHook} which logs and degrades on missing
 * n10s.
 */
@ApplicationScoped
public class InstanceConfigService {

  @Inject
  InstanceConfigDAO dao;

  /**
   * RequestContextController so {@link #onStart} can open a request
   * context for the OGM session — the startup observer runs outside
   * a JAX-RS request scope where the OGM session-per-request would
   * otherwise be absent. Mirrors the UnhideConfigService precedent.
   */
  @Inject
  RequestContextController requestContext;

  @ConfigProperty(name = "shepard.audit.instance-secret", defaultValue = "")
  String configuredSecret;

  /**
   * Quarkus startup hook — seed the singleton if absent.
   *
   * <p>Runs after {@code N10sBootstrapHook} via the standard
   * {@link StartupEvent} ordering (no explicit priority is needed
   * since {@code InstanceConfig} doesn't depend on n10s).
   */
  void onStart(@Observes StartupEvent ev) {
    if (!requestContext.activate()) {
      Log.warn("InstanceConfigService.onStart: could not activate request context; seeding deferred to first read.");
      return;
    }
    try {
      current(); // get-or-seed
    } catch (RuntimeException ex) {
      Log.warnf(
        "InstanceConfigService.onStart: seed failed (%s); chain disabled until next read.",
        ex.getClass().getSimpleName()
      );
    } finally {
      requestContext.deactivate();
    }
  }

  /**
   * Get the singleton, seeding it if absent. The mutation path for
   * rotation is {@link #rotate()}; everyone else reads through here.
   */
  public synchronized InstanceConfig current() {
    InstanceConfig cfg = dao.findSingleton();
    if (cfg != null) return cfg;
    return seed();
  }

  /**
   * Rotate the {@code instanceSecret} — mint a new 32-byte
   * SecureRandom-derived value and increment {@code secretVersion}.
   * The runbook on the operator side is responsible for archiving
   * the prior key (see {@code docs/reference/audit-trail.md}).
   *
   * @return the rotated singleton (fields updated in place + persisted).
   */
  public synchronized InstanceConfig rotate() {
    InstanceConfig cfg = current();
    cfg.setInstanceSecret(generateSecret());
    Integer prior = cfg.getSecretVersion() == null ? 0 : cfg.getSecretVersion();
    cfg.setSecretVersion(prior + 1);
    cfg.setLastRotatedAtMillis(System.currentTimeMillis());
    return dao.createOrUpdate(cfg);
  }

  // ─── Internals ────────────────────────────────────────────────────

  private InstanceConfig seed() {
    InstanceConfig cfg = new InstanceConfig();
    String secret;
    String source;
    if (configuredSecret != null && !configuredSecret.isBlank()) {
      secret = configuredSecret;
      source = "config (SHEPARD_INSTANCE_SECRET or shepard.audit.instance-secret)";
    } else {
      secret = generateSecret();
      source = "SecureRandom (no SHEPARD_INSTANCE_SECRET set — set one in deployment for stable rotation)";
      Log.warnf(
        "InstanceConfigService: seeding audit-chain secret from %s. " +
        "On next deploy, set SHEPARD_INSTANCE_SECRET to a stable, archived value.",
        source
      );
    }
    cfg.setInstanceSecret(secret);
    cfg.setSecretVersion(1);
    cfg.setCreatedAtMillis(System.currentTimeMillis());
    cfg.setLastRotatedAtMillis(cfg.getCreatedAtMillis());
    InstanceConfig saved = dao.createOrUpdate(cfg);
    Log.infof("InstanceConfigService: seeded :InstanceConfig appId=%s secretVersion=%d (source: %s).",
      saved.getAppId(), saved.getSecretVersion(), source);
    return saved;
  }

  private static String generateSecret() {
    byte[] bytes = new byte[32]; // 256-bit key, parity with SHA-256
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
