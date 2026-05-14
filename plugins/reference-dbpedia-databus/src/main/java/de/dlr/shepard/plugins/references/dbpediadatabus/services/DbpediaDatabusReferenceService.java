package de.dlr.shepard.plugins.references.dbpediadatabus.services;

import de.dlr.shepard.plugins.references.dbpediadatabus.clients.DatabusHttpClient;
import de.dlr.shepard.plugins.references.dbpediadatabus.daos.DbpediaDatabusReferenceDAO;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusReference;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusPreviewIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * REF1c — service layer orchestrating preview-fetch / cache refresh
 * for {@code :DbpediaDatabusReference} rows. Same shape as G1b's
 * {@code GitReferenceService}: validate → master-toggle → cache
 * freshness → fetch → write-back.
 */
@RequestScoped
public class DbpediaDatabusReferenceService {

  @Inject
  DbpediaDatabusConfigService configService;

  @Inject
  DbpediaDatabusCredentialService credentialService;

  @Inject
  DbpediaDatabusReferenceDAO referenceDAO;

  @Inject
  DatabusHttpClient httpClient;

  public DbpediaDatabusReferenceService() {}

  DbpediaDatabusReferenceService(
    DbpediaDatabusConfigService configService,
    DbpediaDatabusCredentialService credentialService,
    DbpediaDatabusReferenceDAO referenceDAO,
    DatabusHttpClient httpClient
  ) {
    this.configService = configService;
    this.credentialService = credentialService;
    this.referenceDAO = referenceDAO;
    this.httpClient = httpClient;
  }

  public DbpediaDatabusPreviewIO preview(DbpediaDatabusReference ref) {
    return preview(ref, false);
  }

  public DbpediaDatabusPreviewIO preview(DbpediaDatabusReference ref, boolean forceRefresh) {
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    if (ref == null || ref.getArtifactUri() == null || ref.getArtifactUri().isBlank()) {
      out.setAvailable(false);
      out.setReason("invalid-uri");
      return out;
    }
    DbpediaDatabusConfig cfg = configService.current();
    if (cfg == null || !cfg.isEnabled()) {
      hydrateFromCache(out, ref);
      out.setAvailable(false);
      out.setReason("disabled");
      return out;
    }
    String host = extractHost(ref.getArtifactUri());
    if (host == null) {
      out.setAvailable(false);
      out.setReason("invalid-uri");
      return out;
    }
    if (!configService.isHostAllowed(host)) {
      hydrateFromCache(out, ref);
      out.setAvailable(false);
      out.setReason("host-not-allowed");
      return out;
    }
    if (!forceRefresh && isFresh(ref, cfg)) {
      hydrateFromCache(out, ref);
      out.setAvailable(true);
      out.setCacheStatus(DbpediaDatabusReference.STATUS_FRESH);
      return out;
    }
    DatabusHttpClient.AuthMode auth = buildAuthMode(cfg);
    DbpediaDatabusPreviewIO fetched = httpClient.fetchArtifact(ref.getArtifactUri(), auth);
    if (fetched.isAvailable()) {
      writeBackCache(ref, fetched);
      fetched.setCacheFetchedAt(
        ref.getCacheFetchedAtMillis() == null ? null : new java.util.Date(ref.getCacheFetchedAtMillis())
      );
      fetched.setCacheStatus(DbpediaDatabusReference.STATUS_FRESH);
      return fetched;
    }
    ref.setCacheStatus(DbpediaDatabusReference.STATUS_UNAVAILABLE);
    referenceDAO.createOrUpdate(ref);
    hydrateFromCache(fetched, ref);
    fetched.setCacheStatus(DbpediaDatabusReference.STATUS_UNAVAILABLE);
    return fetched;
  }

  public ValidationResult validateUri(String artifactUri) {
    if (artifactUri == null || artifactUri.isBlank()) {
      return ValidationResult.invalid("artifactUri must be non-blank");
    }
    String host = extractHost(artifactUri);
    if (host == null) {
      return ValidationResult.invalid("artifactUri must be a valid http(s) URL");
    }
    if (!configService.isHostAllowed(host)) {
      return ValidationResult.invalid("host '" + host + "' is not in allowedHosts");
    }
    return ValidationResult.ok();
  }

  public DbpediaDatabusPreviewIO refresh(DbpediaDatabusReference ref) {
    return preview(ref, true);
  }

  static String extractHost(String uri) {
    try {
      URI u = new URI(uri);
      String host = u.getHost();
      if (host == null || host.isBlank()) return null;
      String scheme = u.getScheme();
      if (scheme == null) return null;
      String s = scheme.toLowerCase(Locale.ROOT);
      if (!"http".equals(s) && !"https".equals(s)) return null;
      return host.toLowerCase(Locale.ROOT);
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private boolean isFresh(DbpediaDatabusReference ref, DbpediaDatabusConfig cfg) {
    Long fetchedAt = ref.getCacheFetchedAtMillis();
    if (fetchedAt == null) return false;
    long ttlMs = cfg.getCacheTtlSeconds() * 1000L;
    return System.currentTimeMillis() - fetchedAt < ttlMs;
  }

  private void hydrateFromCache(DbpediaDatabusPreviewIO out, DbpediaDatabusReference ref) {
    out.setTitle(ref.getCachedTitle());
    out.setDescription(ref.getCachedAbstract());
    out.setVersion(ref.getCachedVersion());
    out.setLicence(ref.getCachedLicence());
    if (ref.getCachedModifiedAtMillis() != null) {
      out.setModifiedAt(new java.util.Date(ref.getCachedModifiedAtMillis()));
    }
    if (ref.getCacheFetchedAtMillis() != null) {
      out.setCacheFetchedAt(new java.util.Date(ref.getCacheFetchedAtMillis()));
    }
    out.setCacheStatus(ref.getCacheStatus());
  }

  private void writeBackCache(DbpediaDatabusReference ref, DbpediaDatabusPreviewIO fetched) {
    ref.setCachedTitle(fetched.getTitle());
    ref.setCachedAbstract(fetched.getDescription());
    ref.setCachedVersion(fetched.getVersion());
    ref.setCachedLicence(fetched.getLicence());
    ref.setCachedModifiedAtMillis(fetched.getModifiedAt() == null ? null : fetched.getModifiedAt().getTime());
    ref.setCacheFetchedAtMillis(System.currentTimeMillis());
    ref.setCacheStatus(DbpediaDatabusReference.STATUS_FRESH);
    try {
      referenceDAO.createOrUpdate(ref);
    } catch (RuntimeException e) {
      Log.warnf(e, "REF1c: cache write-back failed for ref appId=%s", ref.getAppId());
    }
  }

  private DatabusHttpClient.AuthMode buildAuthMode(DbpediaDatabusConfig cfg) {
    if (!DbpediaDatabusConfig.AUTH_MODE_OAUTH_CC.equals(cfg.getAuthMode())) {
      return DatabusHttpClient.AuthMode.none();
    }
    if (!cfg.isOauthClientSecretSet() || cfg.getOauthClientSecretCipher() == null) {
      return DatabusHttpClient.AuthMode.none();
    }
    Optional<String> secret = credentialService.decrypt(cfg.getOauthClientSecretCipher());
    if (secret.isEmpty()) {
      return DatabusHttpClient.AuthMode.none();
    }
    return DatabusHttpClient.AuthMode.oauthClientCredentials(
      cfg.getOauthTokenUrl(),
      cfg.getOauthClientId(),
      secret.get()
    );
  }

  public record ValidationResult(boolean ok, String reason) {
    public static ValidationResult ok() {
      return new ValidationResult(true, null);
    }

    public static ValidationResult invalid(String reason) {
      return new ValidationResult(false, reason);
    }
  }
}
