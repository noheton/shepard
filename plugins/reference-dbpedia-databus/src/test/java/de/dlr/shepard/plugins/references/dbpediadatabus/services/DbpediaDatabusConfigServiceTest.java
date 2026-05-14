package de.dlr.shepard.plugins.references.dbpediadatabus.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.references.dbpediadatabus.daos.DbpediaDatabusConfigDAO;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusConfigService.DatabusPatch;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * REF1c — service-level tests for {@link DbpediaDatabusConfigService}.
 */
class DbpediaDatabusConfigServiceTest {

  private DbpediaDatabusConfigDAO dao;
  private DbpediaDatabusCredentialService credentialService;
  private DbpediaDatabusConfigService service;

  @BeforeEach
  void setUp() {
    dao = mock(DbpediaDatabusConfigDAO.class);
    credentialService = mock(DbpediaDatabusCredentialService.class);
    when(dao.createOrUpdate(any(DbpediaDatabusConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    service = new DbpediaDatabusConfigService(
      dao, credentialService, false,
      DbpediaDatabusConfig.DEFAULT_ENDPOINT,
      "databus.dbpedia.org",
      Duration.ofHours(24),
      DbpediaDatabusConfig.AUTH_MODE_NONE,
      "", ""
    );
  }

  // ─── seed-on-first-start ────────────────────────────────────────────────

  @Test
  void seedIfNeeded_createsSingletonWhenAbsent() {
    when(dao.findSingleton()).thenReturn(null);

    DbpediaDatabusConfig seeded = service.seedIfNeeded();

    assertThat(seeded).isNotNull();
    assertThat(seeded.isEnabled()).isFalse();
    assertThat(seeded.getDefaultEndpoint()).isEqualTo(DbpediaDatabusConfig.DEFAULT_ENDPOINT);
    assertThat(seeded.getCacheTtlSeconds()).isEqualTo(86400L);
    assertThat(seeded.getAuthMode()).isEqualTo(DbpediaDatabusConfig.AUTH_MODE_NONE);
    verify(dao).createOrUpdate(any());
  }

  @Test
  void seedIfNeeded_noOpWhenAlreadyExists() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    existing.setEnabled(true);
    when(dao.findSingleton()).thenReturn(existing);

    DbpediaDatabusConfig result = service.seedIfNeeded();

    assertThat(result).isSameAs(existing);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void current_seedsOnMissingNode() {
    when(dao.findSingleton()).thenReturn(null);

    DbpediaDatabusConfig cfg = service.current();

    assertThat(cfg).isNotNull();
    verify(dao).createOrUpdate(any());
  }

  // ─── patch ──────────────────────────────────────────────────────────────

  @Test
  void patch_flipsEnabled() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    existing.setEnabled(false);
    when(dao.findSingleton()).thenReturn(existing);

    DatabusPatch patch = new DatabusPatch();
    patch.enabled = true;
    DbpediaDatabusConfig result = service.patch(patch, "admin");

    assertThat(result.isEnabled()).isTrue();
    assertThat(result.getUpdatedBy()).isEqualTo("admin");
  }

  @Test
  void patch_updatesDefaultEndpoint() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    existing.setDefaultEndpoint("https://old.example.org");
    when(dao.findSingleton()).thenReturn(existing);

    DatabusPatch patch = new DatabusPatch();
    patch.defaultEndpointTouched = true;
    patch.defaultEndpoint = "https://new.example.org";
    DbpediaDatabusConfig result = service.patch(patch, "admin");

    assertThat(result.getDefaultEndpoint()).isEqualTo("https://new.example.org");
  }

  @Test
  void patch_blankEndpoint_throwsIllegalArgument() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    existing.setDefaultEndpoint("https://old.example.org");
    when(dao.findSingleton()).thenReturn(existing);

    DatabusPatch patch = new DatabusPatch();
    patch.defaultEndpointTouched = true;
    patch.defaultEndpoint = "  ";

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("defaultEndpoint");
  }

  @Test
  void patch_cacheTtlTooSmall_throwsIllegalArgument() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    when(dao.findSingleton()).thenReturn(existing);

    DatabusPatch patch = new DatabusPatch();
    patch.cacheTtlSeconds = 30L;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("cacheTtlSeconds");
  }

  @Test
  void patch_allowedHostsUpdated() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    when(dao.findSingleton()).thenReturn(existing);

    DatabusPatch patch = new DatabusPatch();
    patch.allowedHostsTouched = true;
    patch.allowedHosts = java.util.List.of("mybus.example.org");
    DbpediaDatabusConfig result = service.patch(patch, "admin");

    assertThat(result.getAllowedHosts()).containsExactly("mybus.example.org");
  }

  @Test
  void patch_emptyAllowedHosts_throwsIllegalArgument() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    when(dao.findSingleton()).thenReturn(existing);

    DatabusPatch patch = new DatabusPatch();
    patch.allowedHostsTouched = true;
    patch.allowedHosts = java.util.List.of();

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("allowedHosts");
  }

  // ─── isHostAllowed ──────────────────────────────────────────────────────

  @Test
  void isHostAllowed_matchesAllowedHost() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    existing.setAllowedHosts(java.util.List.of("databus.dbpedia.org"));
    when(dao.findSingleton()).thenReturn(existing);

    assertThat(service.isHostAllowed("databus.dbpedia.org")).isTrue();
    assertThat(service.isHostAllowed("DATABUS.DBPEDIA.ORG")).isTrue();
    assertThat(service.isHostAllowed("other.example.org")).isFalse();
    assertThat(service.isHostAllowed(null)).isFalse();
  }

  // ─── credential methods ─────────────────────────────────────────────────

  @Test
  void setOauthClientSecret_encryptsAndSaves() {
    when(credentialService.encryptionAvailable()).thenReturn(true);
    when(credentialService.encrypt("mysecret")).thenReturn("cipher123");
    when(credentialService.fingerprint("cipher123")).thenReturn("abcd1234");
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    when(dao.findSingleton()).thenReturn(existing);

    DbpediaDatabusConfig result = service.setOauthClientSecret("mysecret", "admin");

    assertThat(result.getOauthClientSecretCipher()).isEqualTo("cipher123");
    assertThat(result.isOauthClientSecretSet()).isTrue();
    assertThat(result.getOauthClientSecretFingerprint()).isEqualTo("abcd1234");
  }

  @Test
  void setOauthClientSecret_noEncryptionKey_throwsIllegalState() {
    when(credentialService.encryptionAvailable()).thenReturn(false);

    assertThatThrownBy(() -> service.setOauthClientSecret("mysecret", "admin"))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void clearOauthClientSecret_clearsFields() {
    DbpediaDatabusConfig existing = new DbpediaDatabusConfig(1L);
    existing.setOauthClientSecretCipher("old");
    existing.setOauthClientSecretSet(true);
    existing.setOauthClientSecretFingerprint("fingerprint");
    when(dao.findSingleton()).thenReturn(existing);

    DbpediaDatabusConfig result = service.clearOauthClientSecret("admin");

    assertThat(result.getOauthClientSecretCipher()).isNull();
    assertThat(result.isOauthClientSecretSet()).isFalse();
    assertThat(result.getOauthClientSecretFingerprint()).isNull();
  }
}
