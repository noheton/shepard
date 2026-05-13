package de.dlr.shepard.plugins.minter.datacite.services;

import de.dlr.shepard.plugins.minter.datacite.daos.DataciteMinterConfigDAO;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
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
 * KIP1d — service layer for the {@code :DataciteMinterConfig}
 * singleton. Mirrors the UH1a {@code UnhideConfigService} shape:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes Quarkus's {@code StartupEvent}; if no
 *       {@code :DataciteMinterConfig} node exists, one is minted
 *       from the {@code shepard.minters.datacite.*} install-time
 *       defaults.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the
 *       singleton (seeding if absent).</li>
 *   <li><b>Merge-patch.</b> {@link #patch} applies the
 *       runtime-mutable subset; password fields are read-only here
 *       (admins use the credential endpoint).</li>
 *   <li><b>Set / clear credential.</b>
 *       {@link #setCredential(String, String)} encrypts via
 *       {@link CredentialCipher}, stores the cipher + SHA-256 hash,
 *       and bumps the audit shape. {@link #clearCredential(String)}
 *       wipes both.</li>
 *   <li><b>Resolve plaintext.</b> {@link #resolvePlaintext()}
 *       decrypts the stored cipher for {@code DataciteMinter} mint-
 *       time use; never logged, never serialised through admin REST.</li>
 * </ol>
 *
 * <p>Plaintext-handling discipline: the DataCite password plaintext
 * is logged exactly never; we log only the SHA-256 fingerprint
 * (first 8 hex chars). The {@code ProvenanceCaptureFilter} captures
 * the {@code POST .../credential} request shape + status but not
 * the body, so the audit trail records "who set credentials when"
 * without the secret leaking into {@code :Activity} rows.
 */
@ApplicationScoped
public class DataciteMinterConfigService {

  /** First 8 hex chars of the password hash — for masked fingerprint display. */
  static final int FINGERPRINT_LENGTH = 8;

  @Inject
  DataciteMinterConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.minters.datacite.enabled", defaultValue = "false")
  boolean installDefaultEnabled;

  @ConfigProperty(name = "shepard.minters.datacite.api-base-url", defaultValue = "https://api.test.datacite.org")
  String installDefaultApiBaseUrl;

  @ConfigProperty(name = "shepard.minters.datacite.handle-prefix", defaultValue = "")
  String installDefaultHandlePrefix;

  @ConfigProperty(name = "shepard.minters.datacite.repository-id", defaultValue = "")
  String installDefaultRepositoryId;

  @ConfigProperty(name = "shepard.minters.datacite.publisher", defaultValue = "")
  String installDefaultPublisher;

  @ConfigProperty(name = "shepard.minters.datacite.landing-page-base", defaultValue = "")
  String installDefaultLandingPageBase;

  @ConfigProperty(name = "shepard.minters.datacite.default-state", defaultValue = "draft")
  String installDefaultState;

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
        "KIP1d: could not seed :DataciteMinterConfig on startup; admin actions will retry on first read"
      );
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  /** Seed the singleton if it doesn't exist yet. Public for tests. */
  public synchronized DataciteMinterConfig seedIfNeeded() {
    DataciteMinterConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf(
        "KIP1d: :DataciteMinterConfig already present (appId=%s, enabled=%s)",
        existing.getAppId(),
        existing.isEnabled()
      );
      return existing;
    }
    DataciteMinterConfig seed = new DataciteMinterConfig();
    seed.setEnabled(installDefaultEnabled);
    seed.setApiBaseUrl(emptyToDefault(installDefaultApiBaseUrl, "https://api.test.datacite.org"));
    seed.setHandlePrefix(emptyToNull(installDefaultHandlePrefix));
    seed.setRepositoryId(emptyToNull(installDefaultRepositoryId));
    seed.setPublisher(emptyToNull(installDefaultPublisher));
    seed.setLandingPageBase(emptyToNull(installDefaultLandingPageBase));
    seed.setDefaultState(normaliseState(installDefaultState));
    seed.setUpdatedAt(System.currentTimeMillis());
    seed.setUpdatedBy("system:seed");
    // passwordCipher + passwordHash left null — operator sets via
    // POST /v2/admin/minters/datacite/credential.
    DataciteMinterConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "KIP1d: seeded :DataciteMinterConfig singleton (appId=%s, enabled=%s, apiBaseUrl=%s, state=%s)",
      saved.getAppId(),
      saved.isEnabled(),
      saved.getApiBaseUrl(),
      saved.getDefaultState()
    );
    return saved;
  }

  /** Return the current singleton, seeding from install defaults if absent. */
  public DataciteMinterConfig current() {
    DataciteMinterConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Apply a runtime merge-patch. Only the non-credential fields are
   * patchable here; password-set goes through
   * {@link #setCredential(String, String)}.
   *
   * @throws ReadOnlyFieldException when the caller tried to patch
   *     {@code passwordHash} or {@code passwordCipher} directly.
   * @throws IllegalArgumentException when {@code defaultState} is
   *     not one of the three allowed values.
   */
  public synchronized DataciteMinterConfig patch(DatacitePatch patch, String updatedBy) {
    if (patch.passwordHashTouched || patch.passwordCipherTouched) {
      throw new ReadOnlyFieldException(patch.passwordHashTouched ? "passwordHash" : "passwordCipher");
    }
    DataciteMinterConfig cfg = current();
    if (patch.enabled != null) cfg.setEnabled(patch.enabled);
    if (patch.apiBaseUrlTouched) {
      cfg.setApiBaseUrl(emptyToDefault(patch.apiBaseUrl, "https://api.test.datacite.org"));
    }
    if (patch.handlePrefixTouched) cfg.setHandlePrefix(emptyToNull(patch.handlePrefix));
    if (patch.repositoryIdTouched) cfg.setRepositoryId(emptyToNull(patch.repositoryId));
    if (patch.publisherTouched) cfg.setPublisher(emptyToNull(patch.publisher));
    if (patch.landingPageBaseTouched) cfg.setLandingPageBase(emptyToNull(patch.landingPageBase));
    if (patch.defaultStateTouched) {
      cfg.setDefaultState(normaliseState(patch.defaultState));
    }
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    DataciteMinterConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "KIP1d: :DataciteMinterConfig patched (enabled=%s, apiBaseUrl=%s, prefix-set=%s, state=%s, by=%s)",
      saved.isEnabled(),
      saved.getApiBaseUrl(),
      saved.getHandlePrefix() != null,
      saved.getDefaultState(),
      saved.getUpdatedBy()
    );
    return saved;
  }

  /**
   * Store the DataCite credential. The plaintext is encrypted via
   * {@link CredentialCipher} and stored on {@code passwordCipher};
   * its SHA-256 hex is stored on {@code passwordHash} for fingerprint
   * display. Plaintext is never logged.
   */
  public synchronized DataciteMinterConfig setCredential(String plaintext, String updatedBy) {
    if (plaintext == null || plaintext.isBlank()) {
      throw new IllegalArgumentException("password plaintext must not be null/blank");
    }
    DataciteMinterConfig cfg = current();
    String cipherText = cipher().encrypt(plaintext);
    String hash = sha256Hex(plaintext);
    cfg.setPasswordCipher(cipherText);
    cfg.setPasswordHash(hash);
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    DataciteMinterConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "KIP1d: DataCite credential set (fingerprint=%s, by=%s)",
      fingerprint(hash),
      saved.getUpdatedBy()
    );
    return saved;
  }

  /** Clear the stored credential. */
  public synchronized DataciteMinterConfig clearCredential(String updatedBy) {
    DataciteMinterConfig cfg = current();
    cfg.setPasswordCipher(null);
    cfg.setPasswordHash(null);
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    DataciteMinterConfig saved = dao.createOrUpdate(cfg);
    Log.infof("KIP1d: DataCite credential cleared (by=%s)", saved.getUpdatedBy());
    return saved;
  }

  /**
   * Decrypt the stored password for mint-time HTTP Basic auth.
   * Returns {@code null} when no credential is set; throws
   * {@link IllegalStateException} when decryption fails (tampered /
   * key-mismatch).
   */
  public String resolvePlaintext() {
    DataciteMinterConfig cfg = current();
    String c = cfg.getPasswordCipher();
    if (c == null || c.isBlank()) return null;
    return cipher().decrypt(c);
  }

  /** Public fingerprint of the password hash. {@code null} when no credential. */
  public String passwordFingerprint() {
    return fingerprint(current().getPasswordHash());
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

  /** Normalise an incoming state to one of draft / registered / findable. */
  static String normaliseState(String raw) {
    if (raw == null || raw.isBlank()) return DataciteMinterConfig.STATE_DRAFT;
    String trimmed = raw.trim().toLowerCase();
    return switch (trimmed) {
      case DataciteMinterConfig.STATE_DRAFT,
        DataciteMinterConfig.STATE_REGISTERED,
        DataciteMinterConfig.STATE_FINDABLE -> trimmed;
      default -> throw new IllegalArgumentException(
        "defaultState must be one of draft/registered/findable; got '" + raw + "'"
      );
    };
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
   * Patch DTO for {@link #patch(DatacitePatch, String)}. Carries
   * "touched" flags so the service can distinguish "field absent"
   * (leave alone) from "explicit null" (clear).
   */
  public static final class DatacitePatch {

    public Boolean enabled;
    public boolean apiBaseUrlTouched;
    public String apiBaseUrl;
    public boolean handlePrefixTouched;
    public String handlePrefix;
    public boolean repositoryIdTouched;
    public String repositoryId;
    public boolean publisherTouched;
    public String publisher;
    public boolean landingPageBaseTouched;
    public String landingPageBase;
    public boolean defaultStateTouched;
    public String defaultState;
    /** Sentinel — when {@code true}, the call is rejected. */
    public boolean passwordHashTouched;
    /** Sentinel — when {@code true}, the call is rejected. */
    public boolean passwordCipherTouched;
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
