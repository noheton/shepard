package de.dlr.shepard.plugins.references.dbpediadatabus.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.references.dbpediadatabus.clients.DatabusHttpClient;
import de.dlr.shepard.plugins.references.dbpediadatabus.daos.DbpediaDatabusReferenceDAO;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusReference;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusPreviewIO;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * REF1c — service-level tests for {@link DbpediaDatabusReferenceService}.
 */
class DbpediaDatabusReferenceServiceTest {

  private DbpediaDatabusConfigService configService;
  private DbpediaDatabusCredentialService credentialService;
  private DbpediaDatabusReferenceDAO referenceDAO;
  private DatabusHttpClient httpClient;
  private DbpediaDatabusReferenceService service;

  @BeforeEach
  void setUp() {
    configService = mock(DbpediaDatabusConfigService.class);
    credentialService = mock(DbpediaDatabusCredentialService.class);
    referenceDAO = mock(DbpediaDatabusReferenceDAO.class);
    httpClient = mock(DatabusHttpClient.class);
    service = new DbpediaDatabusReferenceService(configService, credentialService, referenceDAO, httpClient);

    when(referenceDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private DbpediaDatabusConfig enabledConfig() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig(1L);
    cfg.setEnabled(true);
    cfg.setDefaultEndpoint("https://databus.dbpedia.org");
    cfg.setAllowedHosts(java.util.List.of("databus.dbpedia.org"));
    cfg.setCacheTtlSeconds(86400L);
    cfg.setAuthMode(DbpediaDatabusConfig.AUTH_MODE_NONE);
    return cfg;
  }

  // ─── disabled ───────────────────────────────────────────────────────────

  @Test
  void preview_disabled_returnsDisabledReason() {
    DbpediaDatabusConfig cfg = enabledConfig();
    cfg.setEnabled(false);
    when(configService.current()).thenReturn(cfg);

    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://databus.dbpedia.org/artifact");
    DbpediaDatabusPreviewIO out = service.preview(ref);

    assertThat(out.isAvailable()).isFalse();
    assertThat(out.getReason()).isEqualTo("disabled");
  }

  // ─── host not allowed ───────────────────────────────────────────────────

  @Test
  void preview_hostNotAllowed_returnsHostNotAllowed() {
    when(configService.current()).thenReturn(enabledConfig());
    when(configService.isHostAllowed("evil.example.org")).thenReturn(false);

    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://evil.example.org/artifact");
    DbpediaDatabusPreviewIO out = service.preview(ref);

    assertThat(out.isAvailable()).isFalse();
    assertThat(out.getReason()).isEqualTo("host-not-allowed");
  }

  // ─── fresh cache served from cache ──────────────────────────────────────

  @Test
  void preview_freshCache_servedWithoutFetch() {
    when(configService.current()).thenReturn(enabledConfig());
    when(configService.isHostAllowed("databus.dbpedia.org")).thenReturn(true);

    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://databus.dbpedia.org/art");
    ref.setCachedTitle("Cached Title");
    ref.setCachedAbstract("Cached Desc");
    ref.setCacheFetchedAtMillis(System.currentTimeMillis()); // just now → fresh
    ref.setCacheStatus(DbpediaDatabusReference.STATUS_FRESH);

    DbpediaDatabusPreviewIO out = service.preview(ref);

    assertThat(out.isAvailable()).isTrue();
    assertThat(out.getTitle()).isEqualTo("Cached Title");
    assertThat(out.getDescription()).isEqualTo("Cached Desc");
    assertThat(out.getCacheStatus()).isEqualTo(DbpediaDatabusReference.STATUS_FRESH);
  }

  // ─── stale cache triggers live fetch ────────────────────────────────────

  @Test
  void preview_staleCacheLiveFetchOk_updatesCache() {
    when(configService.current()).thenReturn(enabledConfig());
    when(configService.isHostAllowed("databus.dbpedia.org")).thenReturn(true);

    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://databus.dbpedia.org/art");
    ref.setCacheFetchedAtMillis(1L); // epoch → definitely stale
    ref.setCacheStatus(DbpediaDatabusReference.STATUS_STALE);

    DbpediaDatabusPreviewIO fetched = new DbpediaDatabusPreviewIO();
    fetched.setAvailable(true);
    fetched.setTitle("Live Title");
    when(httpClient.fetchArtifact(any(), any())).thenReturn(fetched);

    DbpediaDatabusPreviewIO out = service.preview(ref);

    assertThat(out.isAvailable()).isTrue();
    assertThat(out.getTitle()).isEqualTo("Live Title");
  }

  // ─── live fetch fails → mark UNAVAILABLE ────────────────────────────────

  @Test
  void preview_liveFetchFails_marksUnavailable() {
    when(configService.current()).thenReturn(enabledConfig());
    when(configService.isHostAllowed("databus.dbpedia.org")).thenReturn(true);

    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://databus.dbpedia.org/art");
    ref.setCacheFetchedAtMillis(1L);

    DbpediaDatabusPreviewIO fetched = new DbpediaDatabusPreviewIO();
    fetched.setAvailable(false);
    fetched.setReason("fetch-failed");
    when(httpClient.fetchArtifact(any(), any())).thenReturn(fetched);

    DbpediaDatabusPreviewIO out = service.preview(ref);

    assertThat(out.isAvailable()).isFalse();
    assertThat(out.getReason()).isEqualTo("fetch-failed");
  }

  // ─── validateUri ────────────────────────────────────────────────────────

  @Test
  void validateUri_ok_forAllowedHttpsUri() {
    when(configService.isHostAllowed("databus.dbpedia.org")).thenReturn(true);
    DbpediaDatabusReferenceService.ValidationResult vr = service.validateUri("https://databus.dbpedia.org/art");
    assertThat(vr.ok()).isTrue();
  }

  @Test
  void validateUri_invalid_forBlank() {
    DbpediaDatabusReferenceService.ValidationResult vr = service.validateUri("  ");
    assertThat(vr.ok()).isFalse();
    assertThat(vr.reason()).contains("non-blank");
  }

  @Test
  void validateUri_invalid_forNonHttpScheme() {
    DbpediaDatabusReferenceService.ValidationResult vr = service.validateUri("ftp://databus.dbpedia.org/art");
    assertThat(vr.ok()).isFalse();
    assertThat(vr.reason()).contains("http");
  }

  @Test
  void validateUri_invalid_hostNotAllowed() {
    when(configService.isHostAllowed("evil.example.org")).thenReturn(false);
    DbpediaDatabusReferenceService.ValidationResult vr = service.validateUri("https://evil.example.org/art");
    assertThat(vr.ok()).isFalse();
    assertThat(vr.reason()).contains("allowedHosts");
  }

  // ─── extractHost ────────────────────────────────────────────────────────

  @Test
  void extractHost_validHttpsUri_returnsLowercaseHost() {
    assertThat(DbpediaDatabusReferenceService.extractHost("https://DataBus.DBpedia.ORG/art"))
      .isEqualTo("databus.dbpedia.org");
  }

  @Test
  void extractHost_ftpScheme_returnsNull() {
    assertThat(DbpediaDatabusReferenceService.extractHost("ftp://example.org/f")).isNull();
  }

  @Test
  void extractHost_noHost_returnsNull() {
    assertThat(DbpediaDatabusReferenceService.extractHost("not-a-url")).isNull();
  }

  // ─── OAuth auth mode ────────────────────────────────────────────────────

  @Test
  void preview_oauthNotSetUp_usesNoneAuth() {
    DbpediaDatabusConfig cfg = enabledConfig();
    cfg.setAuthMode(DbpediaDatabusConfig.AUTH_MODE_OAUTH_CC);
    cfg.setOauthClientSecretSet(false); // no secret stored
    when(configService.current()).thenReturn(cfg);
    when(configService.isHostAllowed("databus.dbpedia.org")).thenReturn(true);

    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://databus.dbpedia.org/art");
    ref.setCacheFetchedAtMillis(1L); // stale

    DbpediaDatabusPreviewIO fetched = new DbpediaDatabusPreviewIO();
    fetched.setAvailable(true);
    fetched.setTitle("T");
    when(httpClient.fetchArtifact(any(), any())).thenReturn(fetched);

    // Should not throw even with oauth authMode but no secret
    DbpediaDatabusPreviewIO out = service.preview(ref);
    assertThat(out.isAvailable()).isTrue();
  }

  @Test
  void preview_oauthWithDecryptedSecret_buildsOauthAuthMode() {
    DbpediaDatabusConfig cfg = enabledConfig();
    cfg.setAuthMode(DbpediaDatabusConfig.AUTH_MODE_OAUTH_CC);
    cfg.setOauthClientSecretSet(true);
    cfg.setOauthClientSecretCipher("cipher");
    cfg.setOauthTokenUrl("https://token.example.org/token");
    cfg.setOauthClientId("client-id");
    when(configService.current()).thenReturn(cfg);
    when(configService.isHostAllowed("databus.dbpedia.org")).thenReturn(true);
    when(credentialService.decrypt("cipher")).thenReturn(Optional.of("secret-plain"));

    DbpediaDatabusReference ref = new DbpediaDatabusReference("https://databus.dbpedia.org/art");
    ref.setCacheFetchedAtMillis(1L); // stale

    DbpediaDatabusPreviewIO fetched = new DbpediaDatabusPreviewIO();
    fetched.setAvailable(true);
    when(httpClient.fetchArtifact(any(), any())).thenReturn(fetched);

    DbpediaDatabusPreviewIO out = service.preview(ref);
    assertThat(out.isAvailable()).isTrue();
  }
}
