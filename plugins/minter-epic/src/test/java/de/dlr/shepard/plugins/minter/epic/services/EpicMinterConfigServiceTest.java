package de.dlr.shepard.plugins.minter.epic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.minter.epic.daos.EpicMinterConfigDAO;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService.EpicPatch;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService.ReadOnlyFieldException;
import jakarta.enterprise.context.control.RequestContextController;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KIP1c — service-level tests, modelled on KIP1d's
 * {@code DataciteMinterConfigServiceTest}. No Quarkus boot, no testcontainer —
 * the DAO is mocked and the service exercised in isolation.
 */
class EpicMinterConfigServiceTest {

  private static final Pattern HEX8 = Pattern.compile("^[0-9a-f]{8}$");

  private EpicMinterConfigDAO dao;
  private EpicMinterConfigService service;

  @BeforeEach
  void setUp() {
    dao = mock(EpicMinterConfigDAO.class);
    when(dao.createOrUpdate(any(EpicMinterConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    service = new EpicMinterConfigService();
    service.dao = dao;
    service.installDefaultEnabled = false;
    service.installDefaultApiBaseUrl = "";
    service.installDefaultHandlePrefix = "";
    service.instanceId = "test-instance";
    service.requestContextController = mock(RequestContextController.class);
  }

  // ─── seed-on-first-start ─────────────────────────────────────────────────

  @Test
  void seedIfNeeded_createsSingletonWhenAbsent() {
    when(dao.findSingleton()).thenReturn(null);

    EpicMinterConfig seeded = service.seedIfNeeded();

    assertThat(seeded).isNotNull();
    assertThat(seeded.isEnabled()).isFalse();
    assertThat(seeded.getApiBaseUrl()).isNull();
    assertThat(seeded.getHandlePrefix()).isNull();
    assertThat(seeded.getCredentialKey()).isNull();
    assertThat(seeded.getCredentialHash()).isNull();
    verify(dao).createOrUpdate(any(EpicMinterConfig.class));
  }

  @Test
  void seedIfNeeded_idempotent_returnsExistingWhenPresent() {
    EpicMinterConfig existing = new EpicMinterConfig();
    existing.setAppId("existing-appid");
    existing.setEnabled(true);
    existing.setHandlePrefix("21.T11148");
    when(dao.findSingleton()).thenReturn(existing);

    EpicMinterConfig result = service.seedIfNeeded();

    assertThat(result.getAppId()).isEqualTo("existing-appid");
    assertThat(result.isEnabled()).isTrue();
    assertThat(result.getHandlePrefix()).isEqualTo("21.T11148");
    verify(dao, never()).createOrUpdate(any(EpicMinterConfig.class));
  }

  @Test
  void seedIfNeeded_honoursInstallDefaults() {
    service.installDefaultEnabled = true;
    service.installDefaultApiBaseUrl = "https://handle.argo.grnet.gr/api";
    service.installDefaultHandlePrefix = "21.T11148";
    when(dao.findSingleton()).thenReturn(null);

    EpicMinterConfig seeded = service.seedIfNeeded();

    assertThat(seeded.isEnabled()).isTrue();
    assertThat(seeded.getApiBaseUrl()).isEqualTo("https://handle.argo.grnet.gr/api");
    assertThat(seeded.getHandlePrefix()).isEqualTo("21.T11148");
  }

  @Test
  void current_seedsWhenMissing() {
    when(dao.findSingleton()).thenReturn(null);
    EpicMinterConfig fromCurrent = service.current();
    assertThat(fromCurrent).isNotNull();
    assertThat(fromCurrent.isEnabled()).isFalse();
  }

  // ─── patch happy path + read-only-field rejection ───────────────────────

  @Test
  void patch_updatesEnabledAndAuditFields() {
    EpicMinterConfig existing = new EpicMinterConfig();
    existing.setEnabled(false);
    when(dao.findSingleton()).thenReturn(existing);

    EpicPatch patch = new EpicPatch();
    patch.enabled = true;

    EpicMinterConfig saved = service.patch(patch, "admin1");

    assertThat(saved.isEnabled()).isTrue();
    assertThat(saved.getUpdatedBy()).isEqualTo("admin1");
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void patch_apiBaseUrl_setAndClear() {
    EpicMinterConfig existing = new EpicMinterConfig();
    when(dao.findSingleton()).thenReturn(existing);

    EpicPatch set = new EpicPatch();
    set.apiBaseUrl = "https://handle.argo.grnet.gr/api";
    set.apiBaseUrlTouched = true;
    EpicMinterConfig afterSet = service.patch(set, "ops");
    assertThat(afterSet.getApiBaseUrl()).isEqualTo("https://handle.argo.grnet.gr/api");

    EpicPatch clear = new EpicPatch();
    clear.apiBaseUrl = null;
    clear.apiBaseUrlTouched = true;
    EpicMinterConfig afterClear = service.patch(clear, "ops");
    assertThat(afterClear.getApiBaseUrl()).isNull();
  }

  @Test
  void patch_handlePrefix_setAndClear() {
    EpicMinterConfig existing = new EpicMinterConfig();
    when(dao.findSingleton()).thenReturn(existing);

    EpicPatch set = new EpicPatch();
    set.handlePrefix = "21.T11148";
    set.handlePrefixTouched = true;
    EpicMinterConfig afterSet = service.patch(set, "ops");
    assertThat(afterSet.getHandlePrefix()).isEqualTo("21.T11148");

    EpicPatch clear = new EpicPatch();
    clear.handlePrefix = null;
    clear.handlePrefixTouched = true;
    EpicMinterConfig afterClear = service.patch(clear, "ops");
    assertThat(afterClear.getHandlePrefix()).isNull();
  }

  @Test
  void patch_rejectsCredentialHashTouched() {
    when(dao.findSingleton()).thenReturn(new EpicMinterConfig());

    EpicPatch patch = new EpicPatch();
    patch.credentialHashTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(ReadOnlyFieldException.class)
      .hasMessageContaining("credentialHash");
  }

  @Test
  void patch_rejectsCredentialKeyTouched() {
    when(dao.findSingleton()).thenReturn(new EpicMinterConfig());

    EpicPatch patch = new EpicPatch();
    patch.credentialKeyTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(ReadOnlyFieldException.class)
      .hasMessageContaining("credentialKey");
  }

  // ─── credential set / clear / resolve ──────────────────────────────────

  @Test
  void setCredential_storesCipherAndHash() {
    when(dao.findSingleton()).thenReturn(new EpicMinterConfig());

    EpicMinterConfig saved = service.setCredential("user:s3cret", "operator");

    assertThat(saved.getCredentialKey()).isNotNull();
    assertThat(saved.getCredentialKey()).startsWith("gcm1:");
    assertThat(saved.getCredentialHash()).isNotNull();
    assertThat(saved.getCredentialHash()).hasSize(64); // SHA-256 hex
    assertThat(saved.getUpdatedBy()).isEqualTo("operator");
  }

  @Test
  void setCredential_rejectsBlank() {
    assertThatThrownBy(() -> service.setCredential("", "ops"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.setCredential(null, "ops"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvePlaintext_returnsOriginalAfterSetCredential() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    when(dao.findSingleton()).thenReturn(cfg);

    service.setCredential("the-epic-secret", "ops");

    assertThat(service.resolvePlaintext()).isEqualTo("the-epic-secret");
  }

  @Test
  void resolvePlaintext_returnsNullWhenNoCredentialStored() {
    when(dao.findSingleton()).thenReturn(new EpicMinterConfig());

    assertThat(service.resolvePlaintext()).isNull();
  }

  @Test
  void clearCredential_wipesBothFields() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    service.setCredential("temporary", "ops");
    assertThat(cfg.getCredentialKey()).isNotNull();

    EpicMinterConfig cleared = service.clearCredential("admin");

    assertThat(cleared.getCredentialKey()).isNull();
    assertThat(cleared.getCredentialHash()).isNull();
    assertThat(cleared.getUpdatedBy()).isEqualTo("admin");
  }

  // ─── fingerprint format ─────────────────────────────────────────────────

  @Test
  void fingerprint_returnsFirst8HexChars() {
    String fp = EpicMinterConfigService.fingerprint("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    assertThat(fp).isEqualTo("01234567");
    assertThat(HEX8.matcher(fp).matches()).isTrue();
  }

  @Test
  void fingerprint_returnsNullOnNullOrShortInput() {
    assertThat(EpicMinterConfigService.fingerprint(null)).isNull();
    assertThat(EpicMinterConfigService.fingerprint("abc")).isNull();
  }

  @Test
  void credentialFingerprint_reflectsCurrentRow() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    service.setCredential("hello-world", "ops");
    assertThat(service.credentialFingerprint()).isNotNull();
    assertThat(HEX8.matcher(service.credentialFingerprint()).matches()).isTrue();
  }

  // ─── SHA-256 canonical match ────────────────────────────────────────────

  @Test
  void sha256Hex_matchesCanonicalDigest() {
    // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
    String hash = EpicMinterConfigService.sha256Hex("hello");
    assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
  }

  // ─── cipher lazy build ──────────────────────────────────────────────────

  @Test
  void cipher_isLazilyBuiltAndCached() {
    CredentialCipher first = service.cipher();
    CredentialCipher second = service.cipher();
    assertThat(first).isSameAs(second);
  }
}
