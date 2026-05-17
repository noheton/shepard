package de.dlr.shepard.plugins.minter.epic.services;

import de.dlr.shepard.plugins.minter.epic.daos.EpicMinterConfigDAO;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * KIP1c — service layer for the {@code :EpicMinterConfig}
 * singleton. Mirrors the UH1a / KIP1d shape:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes Quarkus's {@code StartupEvent}; if no
 *       {@code :EpicMinterConfig} node exists, one is minted
 *       from the {@code shepard.minters.epic.*} install-time
 *       defaults.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the
 *       singleton (seeding if absent).</li>
 *   <li><b>Merge-patch.</b> {@link #patch(EpicPatch, String)} applies
 *       the runtime-mutable subset; credential fields are read-only here
 *       (admins use the credential endpoint).</li>
 *   <li><b>Set / clear credential.</b>
 *       {@link #setCredential(String, String)} encrypts via
 *       {@link CredentialCipher}, stores the cipher + SHA-256 hash,
 *       and bumps the audit shape. {@link #clearCredential(String)}
 *       wipes both.</li>
 *   <li><b>Resolve plaintext.</b> {@link #resolvePlaintext()}
 *       decrypts the stored cipher for {@code EpicMinter} mint-time
 *       use; never logged, never serialised through admin REST.</li>
 * </ol>
 *
 * <p>Plaintext-handling discipline: the ePIC credential plaintext is
 * logged exactly never; we log only the SHA-256 fingerprint (first 8
 * hex chars). The {@code ProvenanceCaptureFilter} captures the
 * {@code POST .../credential} request shape + status but not the body,
 * so the audit trail records "who set credentials when" without the
 * secret leaking into {@code :Activity} rows.
 */
@ApplicationScoped
public class EpicMinterConfigService {

  /** First 8 hex chars of the credential hash — for masked fingerprint display. */
  static final int FINGERPRINT_LENGTH = 8;

  @Inject
  EpicMinterConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.minters.epic.enabled", defaultValue = "false")
  boolean installDefaultEnabled;

  @ConfigProperty(name = "shepard.minters.epic.api-base-url", defaultValue = "")
  String installDefaultApiBaseUrl;

  @ConfigProperty(name = "shepard.minters.epic.handle-prefix", defaultValue = "")
  String installDefaultHandlePrefix;

  /**
   * Per-instance id used to derive the credential-encryption key.
   * Defaults to the literal {@code "shepard"} so a fresh install with
   * no {@code shepard.instance.id} pinned still functions for dev,
   * but operators should pin a stable id in production.
   */
  @ConfigProperty(name = "shepard.instance.id", defaultValue = "shepard")
  String instanceId;

  /**
   * Cipher — lazily built post-injection so the {@link #instanceId}
   * config value is available.
   */
  private volatile CredentialCipher cipher;

  /**
   * Seed the singleton on first startup. Idempotent. Fail-soft on
   * runtime errors (operator sees a WARN; the service's other
   * entry points retry on first read).
   */
  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(
        e,
        "KIP1c: could not seed :EpicMinterConfig on startup; admin actions will retry on first read"
      );
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  /** Seed the singleton if it doesn't exist yet. Public for tests. */
  public synchronized EpicMinterConfig seedIfNeeded() {
    EpicMinterConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf(
        "KIP1c: :EpicMinterConfig already present (appId=%s, enabled=%s)",
        existing.getAppId(),
        existing.isEnabled()
      );
      return existing;
    }
    EpicMinterConfig seed = new EpicMinterConfig();
    seed.setEnabled(installDefaultEnabled);
    seed.setApiBaseUrl(emptyToNull(installDefaultApiBaseUrl));
    seed.setHandlePrefix(emptyToNull(installDefaultHandlePrefix));
    seed.setUpdatedAt(System.currentTimeMillis());
    seed.setUpdatedBy("system:seed");
    // credentialKey + credentialHash left null — operator sets via
    // POST /v2/admin/minters/epic/credential.
    EpicMinterConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "KIP1c: seeded :EpicMinterConfig singleton (appId=%s, enabled=%s, apiBaseUrl=%s)",
      saved.getAppId(),
      saved.isEnabled(),
      saved.getApiBaseUrl()
    );
    return saved;
  }

  /** Return the current singleton, seeding from install defaults if absent. */
  public EpicMinterConfig current() {
    EpicMinterConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Apply a runtime merge-patch. Only the non-credential fields are
   * patchable here; credential-set goes through
   * {@link #setCredential(String, String)}.
   *
   * @throws ReadOnlyFieldException when the caller tried to patch
   *     {@code credentialHash} or {@code credentialKey} directly.
   */
  public synchronized EpicMinterConfig patch(EpicPatch patch, String updatedBy) {
    if (patch.credentialHashTouched || patch.credentialKeyTouched) {
      throw new ReadOnlyFieldException(patch.credentialHashTouched ? "credentialHash" : "credentialKey");
    }
    EpicMinterConfig cfg = current();
    if (patch.enabled != null) cfg.setEnabled(patch.enabled);
    if (patch.apiBaseUrlTouched) cfg.setApiBaseUrl(emptyToNull(patch.apiBaseUrl));
    if (patch.handlePrefixTouched) cfg.setHandlePrefix(emptyToNull(patch.handlePrefix));
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    EpicMinterConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "KIP1c: :EpicMinterConfig patched (enabled=%s, apiBaseUrl=%s, prefix-set=%s, by=%s)",
      saved.isEnabled(),
      saved.getApiBaseUrl(),
      saved.getHandlePrefix() != null,
      saved.getUpdatedBy()
    );
    return saved;
  }

  /**
   * Store the ePIC credential. The plaintext is encrypted via
   * {@link CredentialCipher} and stored on {@code credentialKey};
   * its SHA-256 hex is stored on {@code credentialHash} for fingerprint
   * display. Plaintext is never logged.
   */
  public synchronized EpicMinterConfig setCredential(String plaintext, String updatedBy) {
    if (plaintext == null || plaintext.isBlank()) {
      throw new IllegalArgumentException("credential plaintext must not be null/blank");
    }
    EpicMinterConfig cfg = current();
    String cipherText = cipher().encrypt(plaintext);
    String hash = sha256Hex(plaintext);
    cfg.setCredentialKey(cipherText);
    cfg.setCredentialHash(hash);
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    EpicMinterConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "KIP1c: ePIC credential set (fingerprint=%s, by=%s)",
      fingerprint(hash),
      saved.getUpdatedBy()
    );
    return saved;
  }

  /** Clear the stored credential. */
  public synchronized EpicMinterConfig clearCredential(String updatedBy) {
    EpicMinterConfig cfg = current();
    cfg.setCredentialKey(null);
    cfg.setCredentialHash(null);
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    EpicMinterConfig saved = dao.createOrUpdate(cfg);
    Log.infof("KIP1c: ePIC credential cleared (by=%s)", saved.getUpdatedBy());
    return saved;
  }

  /**
   * Decrypt the stored credential for mint-time HTTP Basic auth.
   * Returns {@code null} when no credential is set; throws
   * {@link IllegalStateException} when decryption fails (tampered /
   * key-mismatch).
   */
  public String resolvePlaintext() {
    EpicMinterConfig cfg = current();
    String c = cfg.getCredentialKey();
    if (c == null || c.isBlank()) return null;
    return cipher().decrypt(c);
  }

  /** Public fingerprint of the credential hash. {@code null} when no credential. */
  public String credentialFingerprint() {
    return fingerprint(current().getCredentialHash());
  }

  /** Static fingerprint helper (first 8 hex chars). */
  public static String fingerprint(String hashHex) {
    if (hashHex == null || hashHex.length() < FINGERPRINT_LENGTH) {
      return null;
    }
    return hashHex.substring(0, FINGERPRINT_LENGTH);
  }

  /** Re-derive the cipher lazily. Visible for tests. */
  CredentialCipher cipher() {
    CredentialCipher local = cipher;
    if (local == null) {
      synchronized (this) {
        local = cipher;
        if (local == null) {
          local = new CredentialCipher(emptyToDefault(instanceId, "shepard"));
          cipher = local;
        }
      }
    }
    return local;
  }

  /** SHA-256 hex digest of a UTF-8 string. */
  static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static String emptyToNull(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String emptyToDefault(String s, String defaultValue) {
    if (s == null) return defaultValue;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  // ─── Inner types ────────────────────────────────────────────────

  /**
   * Patch DTO for {@link #patch(EpicPatch, String)}. Carries
   * "touched" flags so the service can distinguish "field absent"
   * (leave alone) from "explicit null" (clear).
   */
  public static final class EpicPatch {

    public Boolean enabled;
    public boolean apiBaseUrlTouched;
    public String apiBaseUrl;
    public boolean handlePrefixTouched;
    public String handlePrefix;
    /** Sentinel — when {@code true}, the call is rejected. */
    public boolean credentialHashTouched;
    /** Sentinel — when {@code true}, the call is rejected. */
    public boolean credentialKeyTouched;
  }

  /** Raised when a caller tries to PATCH a read-only field. */
  public static final class ReadOnlyFieldException extends RuntimeException {

    private final String field;

    public ReadOnlyFieldException(String field) {
      super("Field '" + field + "' is read-only via PATCH; use the dedicated credential endpoint.");
      this.field = field;
    }

    public String field() {
      return field;
    }
  }
}
