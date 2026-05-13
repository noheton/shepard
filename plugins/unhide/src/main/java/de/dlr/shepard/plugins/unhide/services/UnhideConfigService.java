package de.dlr.shepard.plugins.unhide.services;

import de.dlr.shepard.plugins.unhide.daos.UnhideConfigDAO;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * UH1a — service layer for the {@code :UnhideConfig} singleton.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :UnhideConfig} node exists yet, one is minted from
 *       the {@code shepard.unhide.*} install-time defaults. Pre-UH1a
 *       installs upgrading in get the singleton minted on their
 *       next restart with the safe-default {@code enabled=false}.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth against a fresh DB
 *       that somehow missed the startup hook).</li>
 *   <li><b>Merge-patch.</b> {@link #patch} applies the runtime-mutable
 *       subset of the config; the harvest-key hash is
 *       deliberately not patchable via this path (admins use the
 *       rotate endpoint).</li>
 *   <li><b>Mint / revoke harvest key.</b>
 *       {@link #rotateHarvestKey()} mints a fresh UUID-v4 plaintext,
 *       stores its SHA-256 hex, and returns the plaintext exactly
 *       once. {@link #revokeHarvestKey()} clears the stored hash.</li>
 *   <li><b>Verify harvest key.</b> {@link #verifyHarvestKey(String)}
 *       constant-time-compares an incoming plaintext against the
 *       stored hash; used by the feed endpoint's API-key auth path.</li>
 * </ol>
 *
 * <p>Plaintext-handling discipline: the harvest-key plaintext is
 * never logged (we log only the fingerprint — the first 8 hex chars
 * of the stored SHA-256), never stored, and never serialised into
 * the activity log. The response body of
 * {@code POST /v2/admin/unhide/harvest-key/rotate} is the only path
 * the plaintext travels; the {@code ProvenanceCaptureFilter} captures
 * the request shape + status only, not the response body
 * (see {@code ProvenanceCaptureFilter} — no response-body capture
 * is wired in PROV1a), so the activity log carries the rotate event
 * but not the secret.
 */
@ApplicationScoped
public class UnhideConfigService {

  /** First 8 hex chars of the stored hash — for masked fingerprints. */
  static final int FINGERPRINT_LENGTH = 8;

  @Inject
  UnhideConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.unhide.enabled", defaultValue = "false")
  boolean installDefaultEnabled;

  @ConfigProperty(name = "shepard.unhide.feed.public", defaultValue = "false")
  boolean installDefaultFeedPublic;

  @ConfigProperty(name = "shepard.unhide.contact-email", defaultValue = "")
  String installDefaultContactEmail;

  /** {@link SecureRandom}-backed UUID source for harvest-key minting. */
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Seed the singleton on first startup. Idempotent — re-running
   * sees the existing row and returns. Logged at INFO with the
   * action taken so an operator can grep startup logs to confirm
   * UH1a came up correctly.
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
      // WARN, the feed simply returns 503 (enabled=false treated as
      // the implicit default) until the issue is sorted. Same fail-
      // soft posture as N1a's n10s bootstrap (ADR-0014).
      Log.warnf(e, "UH1a: could not seed :UnhideConfig on startup; admin actions will retry on first read");
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
   * @return the freshly-seeded or pre-existing {@link UnhideConfig}.
   */
  public synchronized UnhideConfig seedIfNeeded() {
    UnhideConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("UH1a: :UnhideConfig already present (appId=%s, enabled=%s)", existing.getAppId(), existing.isEnabled());
      return existing;
    }
    UnhideConfig seed = new UnhideConfig();
    seed.setEnabled(installDefaultEnabled);
    seed.setFeedPublic(installDefaultFeedPublic);
    seed.setContactEmail(emptyToNull(installDefaultContactEmail));
    // harvestApiKeyHash + harvestApiKeyLastRotatedAt left null —
    // admin mints one via POST /v2/admin/unhide/harvest-key/rotate.
    UnhideConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "UH1a: seeded :UnhideConfig singleton (appId=%s, enabled=%s, feedPublic=%s)",
      saved.getAppId(),
      saved.isEnabled(),
      saved.isFeedPublic()
    );
    return saved;
  }

  /**
   * Return the current singleton, seeding from install defaults if
   * absent. Callers should treat the return value as a live OGM
   * entity (mutations via the DAO).
   */
  public UnhideConfig current() {
    UnhideConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Apply a runtime merge-patch to the singleton. Only the
   * runtime-mutable fields per {@code aidocs/67 §5.1} flow through;
   * the harvest-key hash is deliberately rejected here (admins use
   * the rotate endpoint, never a raw PATCH).
   *
   * <p>{@code null} fields on the patch are interpreted per RFC 7396:
   * a {@code null} {@code enabled} / {@code feedPublic} means "leave
   * the field alone" (because the IO type carries them as boxed
   * {@code Boolean}). A {@code null} {@code contactEmail} would
   * normally mean "delete the field"; we accept that and store
   * {@code null} (clearing the email).
   *
   * @throws ReadOnlyFieldException when the caller tried to patch
   *     {@code harvestApiKeyHash} directly (the rotate-endpoint is
   *     the only legitimate path).
   */
  public synchronized UnhideConfig patch(UnhidePatch patch) {
    if (patch.harvestApiKeyHashTouched) {
      throw new ReadOnlyFieldException("harvestApiKeyHash");
    }
    UnhideConfig cfg = current();
    if (patch.enabled != null) {
      cfg.setEnabled(patch.enabled);
    }
    if (patch.feedPublic != null) {
      cfg.setFeedPublic(patch.feedPublic);
    }
    if (patch.contactEmailTouched) {
      cfg.setContactEmail(emptyToNull(patch.contactEmail));
    }
    UnhideConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "UH1a: :UnhideConfig patched (enabled=%s, feedPublic=%s, contactEmail-present=%s)",
      saved.isEnabled(),
      saved.isFeedPublic(),
      saved.getContactEmail() != null
    );
    return saved;
  }

  /**
   * Mint a fresh harvest API key (UUID v4 via {@link SecureRandom}),
   * store its SHA-256 hex on the singleton, bump
   * {@code harvestApiKeyLastRotatedAt}, and return the
   * <b>plaintext exactly once</b>. The plaintext is not logged, not
   * stored, and not echoed anywhere except the response body of the
   * calling endpoint.
   *
   * @return {@link MintResult} carrying the plaintext + a copy of
   *     the post-rotate config (for the response IO).
   */
  public synchronized MintResult rotateHarvestKey() {
    String plaintext = mintPlaintext();
    String hash = sha256Hex(plaintext);
    UnhideConfig cfg = current();
    cfg.setHarvestApiKeyHash(hash);
    cfg.setHarvestApiKeyLastRotatedAt(System.currentTimeMillis());
    UnhideConfig saved = dao.createOrUpdate(cfg);
    // Log only the fingerprint, never the plaintext.
    Log.infof(
      "UH1a: harvest API key rotated (fingerprint=%s, rotatedAtMillis=%d)",
      fingerprint(hash),
      saved.getHarvestApiKeyLastRotatedAt()
    );
    return new MintResult(plaintext, saved);
  }

  /**
   * Revoke the current harvest API key. Clears the stored hash and
   * bumps the rotated-at timestamp (so the audit trail records the
   * revoke even though no key replaces it). Feed access falls back
   * to {@code instance-admin}-only when {@link UnhideConfig#isFeedPublic()}
   * is also {@code false}.
   *
   * @return the post-revoke config.
   */
  public synchronized UnhideConfig revokeHarvestKey() {
    UnhideConfig cfg = current();
    cfg.setHarvestApiKeyHash(null);
    cfg.setHarvestApiKeyLastRotatedAt(System.currentTimeMillis());
    UnhideConfig saved = dao.createOrUpdate(cfg);
    Log.infof("UH1a: harvest API key revoked (rotatedAtMillis=%d)", saved.getHarvestApiKeyLastRotatedAt());
    return saved;
  }

  /**
   * Verify an incoming plaintext API key against the stored hash.
   * Constant-time comparison (via {@link MessageDigest#isEqual}) so
   * a timing attack can't extract the hash byte-by-byte.
   *
   * @return {@code true} when the plaintext hashes to the stored
   *     value and a stored value exists; {@code false} otherwise
   *     (including the no-key-minted state).
   */
  public boolean verifyHarvestKey(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      return false;
    }
    UnhideConfig cfg = current();
    String stored = cfg.getHarvestApiKeyHash();
    if (stored == null || stored.isBlank()) {
      return false;
    }
    String candidate = sha256Hex(plaintext);
    // Constant-time hex comparison.
    return MessageDigest.isEqual(
      stored.getBytes(StandardCharsets.US_ASCII),
      candidate.getBytes(StandardCharsets.US_ASCII)
    );
  }

  /**
   * Compute the public fingerprint of the harvest-key hash for
   * display in {@code GET /v2/admin/unhide/config}. {@code null}
   * when no key has been minted yet.
   */
  public static String fingerprint(String hashHex) {
    if (hashHex == null || hashHex.length() < FINGERPRINT_LENGTH) {
      return null;
    }
    return hashHex.substring(0, FINGERPRINT_LENGTH);
  }

  /** SHA-256 hex digest of a UTF-8 string. Package-visible for tests. */
  static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandated in every Java SE — this is unreachable.
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private String mintPlaintext() {
    // UUID v4 via SecureRandom — 122 bits of cryptographic random,
    // matching the Phase 1 ApiKey minting pattern. Encoded canonical
    // hyphenated UUID so an operator can recognise the shape.
    byte[] buf = new byte[16];
    secureRandom.nextBytes(buf);
    // v4 variant bits per RFC 4122 §4.4.
    buf[6] = (byte) ((buf[6] & 0x0F) | 0x40);
    buf[8] = (byte) ((buf[8] & 0x3F) | 0x80);
    long msb = 0L;
    long lsb = 0L;
    for (int i = 0; i < 8; i++) msb = (msb << 8) | (buf[i] & 0xFF);
    for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (buf[i] & 0xFF);
    return new UUID(msb, lsb).toString();
  }

  private static String emptyToNull(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  // ─── Inner types ────────────────────────────────────────────────

  /**
   * Patch DTO for {@link #patch}. Carries boxed {@code Boolean} so
   * RFC 7396 "absent ≠ false" semantics are preserved + an explicit
   * "touched" flag for {@code contactEmail} since {@code null}
   * (clear) is a legitimate value distinct from "leave alone".
   */
  public static final class UnhidePatch {

    public Boolean enabled;
    public Boolean feedPublic;
    public boolean contactEmailTouched;
    public String contactEmail;
    /** Sentinel — when {@code true}, the call is rejected. */
    public boolean harvestApiKeyHashTouched;
  }

  /** Return shape of {@link #rotateHarvestKey()}. */
  public record MintResult(String plaintext, UnhideConfig config) {}

  /** Raised when a caller tries to PATCH a read-only field. */
  public static final class ReadOnlyFieldException extends RuntimeException {

    private final String field;

    public ReadOnlyFieldException(String field) {
      super("Field '" + field + "' is read-only via PATCH; use the dedicated endpoint to mutate it.");
      this.field = field;
    }

    public String field() {
      return field;
    }
  }
}
