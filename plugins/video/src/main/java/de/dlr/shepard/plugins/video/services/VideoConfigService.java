package de.dlr.shepard.plugins.video.services;

import de.dlr.shepard.plugins.video.daos.VideoConfigDAO;
import de.dlr.shepard.plugins.video.entities.VideoConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * VID1c — service layer for the {@code :VideoConfig} singleton.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :VideoConfig} node exists yet, one is minted from
 *       the {@code shepard.plugins.video.*} install-time defaults.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth against a fresh DB
 *       that somehow missed the startup hook).</li>
 *   <li><b>Merge-patch.</b> {@link #patch(VideoPatch)} applies the
 *       runtime-mutable subset of the config following RFC 7396
 *       semantics (absent = leave alone, null = clear, value = set).</li>
 * </ol>
 *
 * <p><b>Precedence.</b> Runtime field values win; deploy-time
 * {@code shepard.plugins.video.*} properties are install defaults
 * that seed the singleton on first start.
 */
@ApplicationScoped
public class VideoConfigService {

  @Inject
  VideoConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.plugins.video.ffprobe.enabled", defaultValue = "true")
  boolean installDefaultFfprobeEnabled;

  // maxFileSizeMb is intentionally not a deploy-time property —
  // operators who need a cap set it at runtime via PATCH.

  /**
   * Seed the singleton on first startup. Idempotent — re-running
   * sees the existing row and returns. Logged at INFO with the
   * action taken so an operator can grep startup logs to confirm
   * VID1c came up correctly.
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
      // Same fail-soft posture as UH1a / AAS1c.
      Log.warnf(e, "VID1c: could not seed :VideoConfig on startup; admin actions will retry on first read");
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
   * @return the freshly-seeded or pre-existing {@link VideoConfig}.
   */
  public synchronized VideoConfig seedIfNeeded() {
    VideoConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("VID1c: :VideoConfig already present (appId=%s, ffprobeEnabled=%s)", existing.getAppId(), existing.isFfprobeEnabled());
      return existing;
    }
    VideoConfig seed = new VideoConfig();
    seed.setFfprobeEnabled(installDefaultFfprobeEnabled);
    // maxFileSizeMb defaults to null (unlimited) — no deploy-time property.
    VideoConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "VID1c: seeded :VideoConfig singleton (appId=%s, ffprobeEnabled=%s, maxFileSizeMb=%s)",
      saved.getAppId(),
      saved.isFfprobeEnabled(),
      saved.getMaxFileSizeMb()
    );
    return saved;
  }

  /**
   * Return the current singleton, seeding from install defaults if
   * absent. Callers should treat the return value as a live OGM
   * entity (mutations via the DAO).
   */
  public VideoConfig current() {
    VideoConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Apply a runtime merge-patch to the singleton following RFC 7396
   * semantics:
   *
   * <ul>
   *   <li>Absent field ({@code null} boxed {@code Boolean} for
   *       {@code ffprobeEnabled}) → leave the current value.</li>
   *   <li>Present field → replace. {@code maxFileSizeMb = null}
   *       clears the cap (unlimited); a positive value sets the cap.</li>
   * </ul>
   *
   * @param patch the patch DTO produced by the REST layer
   * @return the post-patch config
   */
  public synchronized VideoConfig patch(VideoPatch patch) {
    VideoConfig cfg = current();
    if (patch.ffprobeEnabled != null) {
      cfg.setFfprobeEnabled(patch.ffprobeEnabled);
    }
    if (patch.maxFileSizeMbTouched) {
      cfg.setMaxFileSizeMb(patch.maxFileSizeMb);
    }
    VideoConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "VID1c: :VideoConfig patched (ffprobeEnabled=%s, maxFileSizeMb=%s)",
      saved.isFfprobeEnabled(),
      saved.getMaxFileSizeMb()
    );
    return saved;
  }

  // ─── Inner types ────────────────────────────────────────────────────────

  /**
   * Patch DTO for {@link #patch(VideoPatch)}. Carries boxed
   * {@code Boolean} so RFC 7396 "absent ≠ false" semantics are
   * preserved for {@code ffprobeEnabled}, plus an explicit
   * "touched" flag for {@code maxFileSizeMb} since {@code null}
   * (clear the cap) is a legitimate value distinct from "leave alone".
   */
  public static final class VideoPatch {

    /** {@code null} = leave alone; {@code true/false} = set. */
    public Boolean ffprobeEnabled;

    /**
     * {@code true} when the caller included {@code "maxFileSizeMb"}
     * in the JSON body (even if the value is {@code null}).
     */
    public boolean maxFileSizeMbTouched;

    /**
     * The new cap value: {@code null} = clear (unlimited),
     * positive = new MiB cap.
     */
    public Long maxFileSizeMb;
  }

  /** Raised when a caller tries to PATCH a read-only field. */
  public static final class ReadOnlyFieldException extends RuntimeException {

    private final String field;

    public ReadOnlyFieldException(String field) {
      super("Field '" + field + "' is read-only via PATCH.");
      this.field = field;
    }

    public String field() {
      return field;
    }
  }
}
