package de.dlr.shepard.plugins.references.dbpediadatabus.services;

import de.dlr.shepard.plugins.references.dbpediadatabus.daos.DbpediaDatabusConfigDAO;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * REF1c — service layer for the {@code :DbpediaDatabusConfig}
 * singleton. Same shape as {@code UnhideConfigService} (UH1a):
 * seed-on-first-start, get-or-seed, merge-patch, set/clear OAuth
 * credential, host allowlist check.
 *
 * <p>Precedence: runtime values win; install-time
 * {@code shepard.references.dbpedia-databus.*} keys seed the singleton
 * on first startup only — same rule as A3b + N1c2 + UH1a per
 * CLAUDE.md "admin-configurable at runtime".
 */
@ApplicationScoped
public class DbpediaDatabusConfigService {

  @Inject
  DbpediaDatabusConfigDAO dao;

  @Inject
  DbpediaDatabusCredentialService credentialService;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.references.dbpedia-databus.enabled", defaultValue = "false")
  boolean installDefaultEnabled;

  @ConfigProperty(
    name = "shepard.references.dbpedia-databus.default-endpoint",
    defaultValue = DbpediaDatabusConfig.DEFAULT_ENDPOINT
  )
  String installDefaultEndpoint;

  @ConfigProperty(
    name = "shepard.references.dbpedia-databus.allowed-hosts",
    defaultValue = "databus.dbpedia.org"
  )
  String installDefaultAllowedHosts;

  @ConfigProperty(
    name = "shepard.references.dbpedia-databus.cache-ttl",
    defaultValue = "PT24H"
  )
  Duration installDefaultCacheTtl;

  @ConfigProperty(
    name = "shepard.references.dbpedia-databus.auth-mode",
    defaultValue = DbpediaDatabusConfig.AUTH_MODE_NONE
  )
  String installDefaultAuthMode;

  @ConfigProperty(name = "shepard.references.dbpedia-databus.oauth-token-url", defaultValue = "")
  String installDefaultOauthTokenUrl;

  @ConfigProperty(name = "shepard.references.dbpedia-databus.oauth-client-id", defaultValue = "")
  String installDefaultOauthClientId;

  public DbpediaDatabusConfigService() {}

  DbpediaDatabusConfigService(
    DbpediaDatabusConfigDAO dao,
    DbpediaDatabusCredentialService credentialService,
    boolean installDefaultEnabled,
    String installDefaultEndpoint,
    String installDefaultAllowedHosts,
    Duration installDefaultCacheTtl,
    String installDefaultAuthMode,
    String installDefaultOauthTokenUrl,
    String installDefaultOauthClientId
  ) {
    this.dao = dao;
    this.credentialService = credentialService;
    this.installDefaultEnabled = installDefaultEnabled;
    this.installDefaultEndpoint = installDefaultEndpoint;
    this.installDefaultAllowedHosts = installDefaultAllowedHosts;
    this.installDefaultCacheTtl = installDefaultCacheTtl;
    this.installDefaultAuthMode = installDefaultAuthMode;
    this.installDefaultOauthTokenUrl = installDefaultOauthTokenUrl;
    this.installDefaultOauthClientId = installDefaultOauthClientId;
  }

  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(e, "REF1c: could not seed :DbpediaDatabusConfig on startup; admin actions will retry on first read");
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  public synchronized DbpediaDatabusConfig seedIfNeeded() {
    DbpediaDatabusConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf(
        "REF1c: :DbpediaDatabusConfig already present (appId=%s, enabled=%s)",
        existing.getAppId(),
        existing.isEnabled()
      );
      return existing;
    }
    DbpediaDatabusConfig seed = new DbpediaDatabusConfig();
    seed.setEnabled(installDefaultEnabled);
    seed.setDefaultEndpoint(emptyToDefault(installDefaultEndpoint, DbpediaDatabusConfig.DEFAULT_ENDPOINT));
    seed.setAllowedHosts(parseAllowedHosts(installDefaultAllowedHosts));
    seed.setCacheTtlSeconds(
      installDefaultCacheTtl == null
        ? DbpediaDatabusConfig.DEFAULT_CACHE_TTL_SECONDS
        : installDefaultCacheTtl.getSeconds()
    );
    seed.setAuthMode(normaliseAuthMode(installDefaultAuthMode));
    seed.setOauthTokenUrl(emptyToNull(installDefaultOauthTokenUrl));
    seed.setOauthClientId(emptyToNull(installDefaultOauthClientId));
    seed.setOauthClientSecretSet(false);
    seed.setUpdatedAtMillis(System.currentTimeMillis());
    DbpediaDatabusConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "REF1c: seeded :DbpediaDatabusConfig singleton (appId=%s, enabled=%s, endpoint=%s, hosts=%s, ttlSeconds=%d, authMode=%s)",
      saved.getAppId(),
      saved.isEnabled(),
      saved.getDefaultEndpoint(),
      saved.getAllowedHosts(),
      saved.getCacheTtlSeconds(),
      saved.getAuthMode()
    );
    return saved;
  }

  public DbpediaDatabusConfig current() {
    DbpediaDatabusConfig existing = dao.findSingleton();
    if (existing != null) return existing;
    return seedIfNeeded();
  }

  public synchronized DbpediaDatabusConfig patch(DatabusPatch patch, String actorUsername) {
    DbpediaDatabusConfig cfg = current();
    if (patch.enabled != null) cfg.setEnabled(patch.enabled);
    if (patch.defaultEndpointTouched) {
      String endpoint = patch.defaultEndpoint;
      if (endpoint == null || endpoint.isBlank()) {
        throw new IllegalArgumentException("defaultEndpoint must be non-blank");
      }
      cfg.setDefaultEndpoint(endpoint.trim());
    }
    if (patch.allowedHostsTouched) {
      List<String> hosts = patch.allowedHosts == null ? List.of() : patch.allowedHosts;
      List<String> normalised = normaliseHosts(hosts);
      if (normalised.isEmpty()) {
        throw new IllegalArgumentException("allowedHosts must contain at least one entry");
      }
      cfg.setAllowedHosts(normalised);
    }
    if (patch.cacheTtlSeconds != null) {
      long v = patch.cacheTtlSeconds;
      if (v < 60L) {
        throw new IllegalArgumentException("cacheTtlSeconds must be at least 60");
      }
      cfg.setCacheTtlSeconds(v);
    }
    if (patch.authMode != null) cfg.setAuthMode(normaliseAuthMode(patch.authMode));
    if (patch.oauthTokenUrlTouched) cfg.setOauthTokenUrl(emptyToNull(patch.oauthTokenUrl));
    if (patch.oauthClientIdTouched) cfg.setOauthClientId(emptyToNull(patch.oauthClientId));
    cfg.setUpdatedAtMillis(System.currentTimeMillis());
    if (actorUsername != null && !actorUsername.isBlank()) cfg.setUpdatedBy(actorUsername);
    DbpediaDatabusConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "REF1c: :DbpediaDatabusConfig patched (enabled=%s, endpoint=%s, hosts=%s, ttlSeconds=%d, authMode=%s, oauthTokenUrlPresent=%s, oauthClientIdPresent=%s, by=%s)",
      saved.isEnabled(),
      saved.getDefaultEndpoint(),
      saved.getAllowedHosts(),
      saved.getCacheTtlSeconds(),
      saved.getAuthMode(),
      saved.getOauthTokenUrl() != null,
      saved.getOauthClientId() != null,
      actorUsername
    );
    return saved;
  }

  public synchronized DbpediaDatabusConfig setOauthClientSecret(String plaintext, String actorUsername) {
    if (!credentialService.encryptionAvailable()) {
      throw new IllegalStateException(
        "shepard.secrets.encryption-key is not configured — DBpedia Databus credential storage disabled"
      );
    }
    String cipher = credentialService.encrypt(plaintext);
    DbpediaDatabusConfig cfg = current();
    cfg.setOauthClientSecretCipher(cipher);
    cfg.setOauthClientSecretSet(true);
    cfg.setOauthClientSecretFingerprint(credentialService.fingerprint(cipher));
    cfg.setUpdatedAtMillis(System.currentTimeMillis());
    if (actorUsername != null && !actorUsername.isBlank()) cfg.setUpdatedBy(actorUsername);
    DbpediaDatabusConfig saved = dao.createOrUpdate(cfg);
    Log.infof("REF1c: OAuth client secret set (fingerprint=%s, by=%s)", saved.getOauthClientSecretFingerprint(), actorUsername);
    return saved;
  }

  public synchronized DbpediaDatabusConfig clearOauthClientSecret(String actorUsername) {
    DbpediaDatabusConfig cfg = current();
    cfg.setOauthClientSecretCipher(null);
    cfg.setOauthClientSecretSet(false);
    cfg.setOauthClientSecretFingerprint(null);
    cfg.setUpdatedAtMillis(System.currentTimeMillis());
    if (actorUsername != null && !actorUsername.isBlank()) cfg.setUpdatedBy(actorUsername);
    DbpediaDatabusConfig saved = dao.createOrUpdate(cfg);
    Log.infof("REF1c: OAuth client secret cleared (by=%s)", actorUsername);
    return saved;
  }

  public boolean isHostAllowed(String host) {
    if (host == null || host.isBlank()) return false;
    DbpediaDatabusConfig cfg = current();
    List<String> allow = cfg.getAllowedHosts();
    if (allow == null || allow.isEmpty()) return false;
    String hostLc = host.toLowerCase(Locale.ROOT);
    for (String a : allow) {
      if (a != null && a.toLowerCase(Locale.ROOT).equals(hostLc)) return true;
    }
    return false;
  }

  // ─── helpers ─────────────────────────────────────────────────────

  private static String emptyToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static String emptyToDefault(String s, String dflt) {
    if (s == null || s.trim().isEmpty()) return dflt;
    return s.trim();
  }

  private static String normaliseAuthMode(String raw) {
    if (raw == null) return DbpediaDatabusConfig.AUTH_MODE_NONE;
    String t = raw.trim().toLowerCase(Locale.ROOT);
    if (DbpediaDatabusConfig.AUTH_MODE_OAUTH_CC.equals(t)) return DbpediaDatabusConfig.AUTH_MODE_OAUTH_CC;
    return DbpediaDatabusConfig.AUTH_MODE_NONE;
  }

  private static List<String> parseAllowedHosts(String csv) {
    if (csv == null || csv.trim().isEmpty()) {
      return new ArrayList<>(List.of("databus.dbpedia.org"));
    }
    return normaliseHosts(Arrays.asList(csv.split(",")));
  }

  private static List<String> normaliseHosts(List<String> in) {
    Set<String> uniq = new LinkedHashSet<>();
    for (String raw : in) {
      if (raw == null) continue;
      String t = raw.trim().toLowerCase(Locale.ROOT);
      if (!t.isEmpty()) uniq.add(t);
    }
    return Collections.unmodifiableList(new ArrayList<>(uniq));
  }

  /**
   * Patch DTO. Boxed {@code Boolean} on {@code enabled} so RFC 7396
   * "absent ≠ false" semantics flow; "touched" flags for nullable
   * string fields so we can distinguish "absent" (leave alone) from
   * "explicit-null" (clear).
   */
  public static final class DatabusPatch {

    public Boolean enabled;
    public Long cacheTtlSeconds;
    public String authMode;
    public boolean defaultEndpointTouched;
    public String defaultEndpoint;
    public boolean allowedHostsTouched;
    public List<String> allowedHosts;
    public boolean oauthTokenUrlTouched;
    public String oauthTokenUrl;
    public boolean oauthClientIdTouched;
    public String oauthClientId;
  }
}
