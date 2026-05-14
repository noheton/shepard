package de.dlr.shepard.plugins.storage.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.storage.s3.daos.S3StorageConfigDAO;
import de.dlr.shepard.plugins.storage.s3.entities.S3StorageConfig;
import de.dlr.shepard.plugins.storage.s3.services.S3StorageConfigService.ReadOnlyFieldException;
import de.dlr.shepard.plugins.storage.s3.services.S3StorageConfigService.S3Patch;
import jakarta.enterprise.context.control.RequestContextController;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FS1b — service-level tests. No Quarkus boot, no testcontainer.
 * DAO is mocked; service exercised in isolation.
 */
class S3StorageConfigServiceTest {

  private static final Pattern HEX8 = Pattern.compile("^[0-9a-f]{8}$");

  private S3StorageConfigDAO dao;
  private S3StorageConfigService service;

  @BeforeEach
  void setUp() {
    dao = mock(S3StorageConfigDAO.class);
    when(dao.createOrUpdate(any(S3StorageConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    service = new S3StorageConfigService();
    service.dao = dao;
    service.installDefaultEnabled = false;
    service.installDefaultEndpointUrl = "";
    service.installDefaultRegion = "us-east-1";
    service.installDefaultBucket = "";
    service.installDefaultBucketPrefix = "";
    service.installDefaultForcePathStyle = true;
    service.installDefaultSseAlgorithm = "";
    service.installDefaultMultipartThresholdBytes = 16777216L;
    service.installDefaultConnectionTimeoutSeconds = 10;
    service.installDefaultRequestTimeoutSeconds = 30;
    service.instanceId = "test-instance";
    service.requestContextController = mock(RequestContextController.class);
  }

  // ─── seed-on-first-start ─────────────────────────────────────────────────

  @Test
  void seedIfNeeded_createsSingletonWhenAbsent() {
    when(dao.findSingleton()).thenReturn(null);

    S3StorageConfig seeded = service.seedIfNeeded();

    assertThat(seeded).isNotNull();
    assertThat(seeded.isEnabled()).isFalse();
    assertThat(seeded.getRegion()).isEqualTo("us-east-1");
    assertThat(seeded.getBucket()).isEqualTo("");
    assertThat(seeded.isForcePathStyle()).isTrue();
    assertThat(seeded.getSecretAccessKeyCipher()).isNull();
    assertThat(seeded.getSecretAccessKeyHash()).isNull();
    assertThat(seeded.getMultipartThresholdBytes()).isEqualTo(16777216L);
    verify(dao).createOrUpdate(any(S3StorageConfig.class));
  }

  @Test
  void seedIfNeeded_idempotent_returnsExistingWhenPresent() {
    S3StorageConfig existing = new S3StorageConfig();
    existing.setAppId("existing-appid");
    existing.setEnabled(true);
    existing.setBucket("my-bucket");
    when(dao.findSingleton()).thenReturn(existing);

    S3StorageConfig result = service.seedIfNeeded();

    assertThat(result.getAppId()).isEqualTo("existing-appid");
    assertThat(result.isEnabled()).isTrue();
    assertThat(result.getBucket()).isEqualTo("my-bucket");
    verify(dao, never()).createOrUpdate(any(S3StorageConfig.class));
  }

  @Test
  void seedIfNeeded_honoursInstallDefaults() {
    service.installDefaultEnabled = true;
    service.installDefaultEndpointUrl = "https://garage.example.org";
    service.installDefaultRegion = "eu-central-1";
    service.installDefaultBucket = "my-shepard-bucket";
    service.installDefaultForcePathStyle = false;
    when(dao.findSingleton()).thenReturn(null);

    S3StorageConfig seeded = service.seedIfNeeded();

    assertThat(seeded.isEnabled()).isTrue();
    assertThat(seeded.getEndpointUrl()).isEqualTo("https://garage.example.org");
    assertThat(seeded.getRegion()).isEqualTo("eu-central-1");
    assertThat(seeded.getBucket()).isEqualTo("my-shepard-bucket");
    assertThat(seeded.isForcePathStyle()).isFalse();
  }

  @Test
  void current_seedsWhenMissing() {
    when(dao.findSingleton()).thenReturn(null);
    S3StorageConfig fromCurrent = service.current();
    assertThat(fromCurrent).isNotNull();
    assertThat(fromCurrent.getRegion()).isEqualTo("us-east-1");
  }

  // ─── getActive ─────────────────────────────────────────────────────────

  @Test
  void getActive_returnsNullWhenDisabled() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setEnabled(false);
    cfg.setBucket("bucket");
    cfg.setAccessKeyId("key");
    cfg.setSecretAccessKeyCipher("gcm1:cipher");
    when(dao.findSingleton()).thenReturn(cfg);

    assertThat(service.getActive()).isNull();
  }

  @Test
  void getActive_returnsNullWhenBucketMissing() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setEnabled(true);
    cfg.setBucket("");
    cfg.setAccessKeyId("key");
    cfg.setSecretAccessKeyCipher("gcm1:cipher");
    when(dao.findSingleton()).thenReturn(cfg);

    assertThat(service.getActive()).isNull();
  }

  @Test
  void getActive_returnsNullWhenCredentialMissing() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setEnabled(true);
    cfg.setBucket("bucket");
    cfg.setAccessKeyId("");
    cfg.setSecretAccessKeyCipher(null);
    when(dao.findSingleton()).thenReturn(cfg);

    assertThat(service.getActive()).isNull();
  }

  @Test
  void getActive_returnsConfigWhenFullyConfigured() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setEnabled(true);
    cfg.setBucket("my-bucket");
    cfg.setAccessKeyId("AKIA...");
    cfg.setSecretAccessKeyCipher("gcm1:something");
    when(dao.findSingleton()).thenReturn(cfg);

    assertThat(service.getActive()).isSameAs(cfg);
  }

  // ─── patch happy path + read-only-field rejection ───────────────────────

  @Test
  void patch_updatesEnabledAndAuditFields() {
    S3StorageConfig existing = new S3StorageConfig();
    existing.setEnabled(false);
    when(dao.findSingleton()).thenReturn(existing);

    S3Patch patch = new S3Patch();
    patch.enabled = true;

    S3StorageConfig saved = service.patch(patch, "admin1");

    assertThat(saved.isEnabled()).isTrue();
    assertThat(saved.getUpdatedBy()).isEqualTo("admin1");
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void patch_endpointUrlValidated() {
    when(dao.findSingleton()).thenReturn(new S3StorageConfig());

    S3Patch patch = new S3Patch();
    patch.endpointUrl = "not-a-url";
    patch.endpointUrlTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("endpointUrl");
  }

  @Test
  void patch_endpointUrlEmpty_setsBlank() {
    S3StorageConfig existing = new S3StorageConfig();
    existing.setEndpointUrl("https://garage.example.org");
    when(dao.findSingleton()).thenReturn(existing);

    S3Patch patch = new S3Patch();
    patch.endpointUrl = "";
    patch.endpointUrlTouched = true;

    S3StorageConfig saved = service.patch(patch, "admin");
    assertThat(saved.getEndpointUrl()).isEqualTo("");
  }

  @Test
  void patch_multipartThresholdBytesPositiveValidation() {
    when(dao.findSingleton()).thenReturn(new S3StorageConfig());

    S3Patch patch = new S3Patch();
    patch.multipartThresholdBytes = -1L;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("multipartThresholdBytes");
  }

  @Test
  void patch_rejectsAccessKeyIdTouched() {
    when(dao.findSingleton()).thenReturn(new S3StorageConfig());

    S3Patch patch = new S3Patch();
    patch.accessKeyIdTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(ReadOnlyFieldException.class)
      .hasMessageContaining("accessKeyId");
  }

  @Test
  void patch_rejectsSecretAccessKeyCipherTouched() {
    when(dao.findSingleton()).thenReturn(new S3StorageConfig());

    S3Patch patch = new S3Patch();
    patch.secretAccessKeyCipherTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(ReadOnlyFieldException.class)
      .hasMessageContaining("secretAccessKeyCipher");
  }

  @Test
  void patch_rejectsSecretAccessKeyHashTouched() {
    when(dao.findSingleton()).thenReturn(new S3StorageConfig());

    S3Patch patch = new S3Patch();
    patch.secretAccessKeyHashTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(ReadOnlyFieldException.class)
      .hasMessageContaining("secretAccessKeyHash");
  }

  // ─── credential set / clear / resolve ──────────────────────────────────

  @Test
  void setCredential_storesCipherAndHash() {
    when(dao.findSingleton()).thenReturn(new S3StorageConfig());

    S3StorageConfig saved = service.setCredential("AKIA123", "s3cr3t-key", "operator");

    assertThat(saved.getSecretAccessKeyCipher()).isNotNull();
    assertThat(saved.getSecretAccessKeyCipher()).startsWith("gcm1:");
    assertThat(saved.getSecretAccessKeyHash()).isNotNull();
    assertThat(saved.getSecretAccessKeyHash()).hasSize(64); // SHA-256 hex
    assertThat(saved.getAccessKeyId()).isEqualTo("AKIA123");
    assertThat(saved.getUpdatedBy()).isEqualTo("operator");
  }

  @Test
  void setCredential_rejectsBlankAccessKeyId() {
    assertThatThrownBy(() -> service.setCredential("", "secret", "ops"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.setCredential(null, "secret", "ops"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void setCredential_rejectsBlankSecretKey() {
    assertThatThrownBy(() -> service.setCredential("AKIA123", "", "ops"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.setCredential("AKIA123", null, "ops"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvePlaintextSecret_returnsOriginalAfterSetCredential() {
    S3StorageConfig cfg = new S3StorageConfig();
    when(dao.findSingleton()).thenReturn(cfg);

    service.setCredential("AKIA123", "the-secret-key", "ops");

    assertThat(service.resolvePlaintextSecret()).isEqualTo("the-secret-key");
  }

  @Test
  void resolvePlaintextSecret_returnsNullWhenNoCredentialStored() {
    when(dao.findSingleton()).thenReturn(new S3StorageConfig());

    assertThat(service.resolvePlaintextSecret()).isNull();
  }

  @Test
  void clearCredential_wipesAllFields() {
    S3StorageConfig cfg = new S3StorageConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    service.setCredential("AKIA123", "temporary", "ops");
    assertThat(cfg.getSecretAccessKeyCipher()).isNotNull();

    S3StorageConfig cleared = service.clearCredential("admin");

    assertThat(cleared.getAccessKeyId()).isEqualTo("");
    assertThat(cleared.getSecretAccessKeyCipher()).isNull();
    assertThat(cleared.getSecretAccessKeyHash()).isNull();
    assertThat(cleared.getUpdatedBy()).isEqualTo("admin");
  }

  // ─── fingerprint format ─────────────────────────────────────────────────

  @Test
  void fingerprint_returnsFirst8HexChars() {
    String fp = S3StorageConfigService.fingerprint("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    assertThat(fp).isEqualTo("01234567");
    assertThat(HEX8.matcher(fp).matches()).isTrue();
  }

  @Test
  void fingerprint_returnsNullOnNullOrShortInput() {
    assertThat(S3StorageConfigService.fingerprint(null)).isNull();
    assertThat(S3StorageConfigService.fingerprint("abc")).isNull();
  }

  @Test
  void secretFingerprint_reflectsCurrentRow() {
    S3StorageConfig cfg = new S3StorageConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    service.setCredential("AKIA123", "hello-world", "ops");
    assertThat(service.secretFingerprint()).isNotNull();
    assertThat(HEX8.matcher(service.secretFingerprint()).matches()).isTrue();
  }

  // ─── SHA-256 canonical match ────────────────────────────────────────────

  @Test
  void sha256Hex_matchesCanonicalDigest() {
    String hash = S3StorageConfigService.sha256Hex("hello");
    assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
  }

  // ─── cipher lazy build ──────────────────────────────────────────────────

  @Test
  void cipher_isLazilyBuiltAndCached() {
    CredentialCipher first = service.cipher();
    CredentialCipher second = service.cipher();
    assertThat(first).isSameAs(second);
  }

  // ─── validateEndpointUrl ────────────────────────────────────────────────

  @Test
  void validateEndpointUrl_acceptsHttpAndHttps() {
    // Should not throw
    S3StorageConfigService.validateEndpointUrl("https://s3.example.com");
    S3StorageConfigService.validateEndpointUrl("http://minio.local:9000");
  }

  @Test
  void validateEndpointUrl_rejectsFtpScheme() {
    assertThatThrownBy(() -> S3StorageConfigService.validateEndpointUrl("ftp://example.com"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("scheme");
  }

  @Test
  void validateEndpointUrl_rejectsNoScheme() {
    assertThatThrownBy(() -> S3StorageConfigService.validateEndpointUrl("example.com/path"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validateEndpointUrl_blanksAreSkipped() {
    // Should not throw for blank / null
    S3StorageConfigService.validateEndpointUrl(null);
    S3StorageConfigService.validateEndpointUrl("");
    S3StorageConfigService.validateEndpointUrl("  ");
  }
}
