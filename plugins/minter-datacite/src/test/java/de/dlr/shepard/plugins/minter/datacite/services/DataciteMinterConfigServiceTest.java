package de.dlr.shepard.plugins.minter.datacite.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.minter.datacite.daos.DataciteMinterConfigDAO;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService.DatacitePatch;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService.ReadOnlyFieldException;
import jakarta.enterprise.context.control.RequestContextController;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KIP1d — service-level tests, modelled on UH1a's
 * {@code UnhideConfigServiceTest}. No Quarkus boot, no testcontainer —
 * the DAO is mocked and the service exercised in isolation.
 */
class DataciteMinterConfigServiceTest {

  private static final Pattern HEX8 = Pattern.compile("^[0-9a-f]{8}$");

  private DataciteMinterConfigDAO dao;
  private DataciteMinterConfigService service;

  @BeforeEach
  void setUp() {
    dao = mock(DataciteMinterConfigDAO.class);
    when(dao.createOrUpdate(any(DataciteMinterConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    service = new DataciteMinterConfigService();
    service.dao = dao;
    service.installDefaultEnabled = false;
    service.installDefaultApiBaseUrl = "https://api.test.datacite.org";
    service.installDefaultHandlePrefix = "";
    service.installDefaultRepositoryId = "";
    service.installDefaultPublisher = "";
    service.installDefaultLandingPageBase = "";
    service.installDefaultState = "draft";
    service.instanceId = "test-instance";
    service.requestContextController = mock(RequestContextController.class);
  }

  // ─── seed-on-first-start ─────────────────────────────────────────────────

  @Test
  void seedIfNeeded_createsSingletonWhenAbsent() {
    when(dao.findSingleton()).thenReturn(null);

    DataciteMinterConfig seeded = service.seedIfNeeded();

    assertThat(seeded).isNotNull();
    assertThat(seeded.isEnabled()).isFalse();
    assertThat(seeded.getApiBaseUrl()).isEqualTo("https://api.test.datacite.org");
    assertThat(seeded.getHandlePrefix()).isNull();
    assertThat(seeded.getRepositoryId()).isNull();
    assertThat(seeded.getPasswordCipher()).isNull();
    assertThat(seeded.getPasswordHash()).isNull();
    assertThat(seeded.getDefaultState()).isEqualTo("draft");
    verify(dao).createOrUpdate(any(DataciteMinterConfig.class));
  }

  @Test
  void seedIfNeeded_idempotent_returnsExistingWhenPresent() {
    DataciteMinterConfig existing = new DataciteMinterConfig();
    existing.setAppId("existing-appid");
    existing.setEnabled(true);
    existing.setHandlePrefix("10.1234");
    when(dao.findSingleton()).thenReturn(existing);

    DataciteMinterConfig result = service.seedIfNeeded();

    assertThat(result.getAppId()).isEqualTo("existing-appid");
    assertThat(result.isEnabled()).isTrue();
    assertThat(result.getHandlePrefix()).isEqualTo("10.1234");
    verify(dao, never()).createOrUpdate(any(DataciteMinterConfig.class));
  }

  @Test
  void seedIfNeeded_honoursInstallDefaults() {
    service.installDefaultEnabled = true;
    service.installDefaultApiBaseUrl = "https://api.datacite.org";
    service.installDefaultHandlePrefix = "10.5072";
    service.installDefaultPublisher = "DLR";
    service.installDefaultLandingPageBase = "https://example.org/v2";
    service.installDefaultState = "registered";
    when(dao.findSingleton()).thenReturn(null);

    DataciteMinterConfig seeded = service.seedIfNeeded();

    assertThat(seeded.isEnabled()).isTrue();
    assertThat(seeded.getApiBaseUrl()).isEqualTo("https://api.datacite.org");
    assertThat(seeded.getHandlePrefix()).isEqualTo("10.5072");
    assertThat(seeded.getPublisher()).isEqualTo("DLR");
    assertThat(seeded.getLandingPageBase()).isEqualTo("https://example.org/v2");
    assertThat(seeded.getDefaultState()).isEqualTo("registered");
  }

  @Test
  void current_seedsWhenMissing() {
    when(dao.findSingleton()).thenReturn(null);
    DataciteMinterConfig fromCurrent = service.current();
    assertThat(fromCurrent).isNotNull();
    assertThat(fromCurrent.getApiBaseUrl()).isEqualTo("https://api.test.datacite.org");
  }

  // ─── patch happy path + read-only-field rejection ───────────────────────

  @Test
  void patch_updatesEnabledAndAuditFields() {
    DataciteMinterConfig existing = new DataciteMinterConfig();
    existing.setEnabled(false);
    when(dao.findSingleton()).thenReturn(existing);

    DatacitePatch patch = new DatacitePatch();
    patch.enabled = true;

    DataciteMinterConfig saved = service.patch(patch, "admin1");

    assertThat(saved.isEnabled()).isTrue();
    assertThat(saved.getUpdatedBy()).isEqualTo("admin1");
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void patch_apiBaseUrlEmptyFallsBackToDefault() {
    DataciteMinterConfig existing = new DataciteMinterConfig();
    existing.setApiBaseUrl("https://api.datacite.org");
    when(dao.findSingleton()).thenReturn(existing);

    DatacitePatch patch = new DatacitePatch();
    patch.apiBaseUrl = "";
    patch.apiBaseUrlTouched = true;

    DataciteMinterConfig saved = service.patch(patch, "admin");

    assertThat(saved.getApiBaseUrl()).isEqualTo("https://api.test.datacite.org");
  }

  @Test
  void patch_handlePrefix_setAndClear() {
    DataciteMinterConfig existing = new DataciteMinterConfig();
    when(dao.findSingleton()).thenReturn(existing);

    DatacitePatch set = new DatacitePatch();
    set.handlePrefix = "10.9999";
    set.handlePrefixTouched = true;
    DataciteMinterConfig afterSet = service.patch(set, "ops");
    assertThat(afterSet.getHandlePrefix()).isEqualTo("10.9999");

    DatacitePatch clear = new DatacitePatch();
    clear.handlePrefix = null;
    clear.handlePrefixTouched = true;
    DataciteMinterConfig afterClear = service.patch(clear, "ops");
    assertThat(afterClear.getHandlePrefix()).isNull();
  }

  @Test
  void patch_publisher_clearWithBlankNull() {
    DataciteMinterConfig existing = new DataciteMinterConfig();
    existing.setPublisher("DLR");
    when(dao.findSingleton()).thenReturn(existing);

    DatacitePatch p = new DatacitePatch();
    p.publisher = "   ";
    p.publisherTouched = true;

    DataciteMinterConfig saved = service.patch(p, "admin");

    assertThat(saved.getPublisher()).isNull();
  }

  @Test
  void patch_rejectsPasswordHashTouched() {
    when(dao.findSingleton()).thenReturn(new DataciteMinterConfig());

    DatacitePatch patch = new DatacitePatch();
    patch.passwordHashTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(ReadOnlyFieldException.class)
      .hasMessageContaining("passwordHash");
  }

  @Test
  void patch_rejectsPasswordCipherTouched() {
    when(dao.findSingleton()).thenReturn(new DataciteMinterConfig());

    DatacitePatch patch = new DatacitePatch();
    patch.passwordCipherTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(ReadOnlyFieldException.class)
      .hasMessageContaining("passwordCipher");
  }

  @Test
  void patch_invalidDefaultStateRaisesIllegalArgument() {
    when(dao.findSingleton()).thenReturn(new DataciteMinterConfig());

    DatacitePatch patch = new DatacitePatch();
    patch.defaultState = "not-a-real-state";
    patch.defaultStateTouched = true;

    assertThatThrownBy(() -> service.patch(patch, "admin"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("defaultState");
  }

  @Test
  void patch_normalisesDefaultStateCase() {
    when(dao.findSingleton()).thenReturn(new DataciteMinterConfig());

    DatacitePatch patch = new DatacitePatch();
    patch.defaultState = "  FINDABLE  ";
    patch.defaultStateTouched = true;

    DataciteMinterConfig saved = service.patch(patch, "admin");

    assertThat(saved.getDefaultState()).isEqualTo("findable");
  }

  // ─── credential set / clear / resolve ──────────────────────────────────

  @Test
  void setCredential_storesCipherAndHash() {
    when(dao.findSingleton()).thenReturn(new DataciteMinterConfig());

    DataciteMinterConfig saved = service.setCredential("s3cret", "operator");

    assertThat(saved.getPasswordCipher()).isNotNull();
    assertThat(saved.getPasswordCipher()).startsWith("gcm1:");
    assertThat(saved.getPasswordHash()).isNotNull();
    assertThat(saved.getPasswordHash()).hasSize(64); // SHA-256 hex
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
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    when(dao.findSingleton()).thenReturn(cfg);

    service.setCredential("the-secret", "ops");

    assertThat(service.resolvePlaintext()).isEqualTo("the-secret");
  }

  @Test
  void resolvePlaintext_returnsNullWhenNoCredentialStored() {
    when(dao.findSingleton()).thenReturn(new DataciteMinterConfig());

    assertThat(service.resolvePlaintext()).isNull();
  }

  @Test
  void clearCredential_wipesBothFields() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    service.setCredential("temporary", "ops");
    assertThat(cfg.getPasswordCipher()).isNotNull();

    DataciteMinterConfig cleared = service.clearCredential("admin");

    assertThat(cleared.getPasswordCipher()).isNull();
    assertThat(cleared.getPasswordHash()).isNull();
    assertThat(cleared.getUpdatedBy()).isEqualTo("admin");
  }

  // ─── fingerprint format ─────────────────────────────────────────────────

  @Test
  void fingerprint_returnsFirst8HexChars() {
    String fp = DataciteMinterConfigService.fingerprint("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    assertThat(fp).isEqualTo("01234567");
    assertThat(HEX8.matcher(fp).matches()).isTrue();
  }

  @Test
  void fingerprint_returnsNullOnNullOrShortInput() {
    assertThat(DataciteMinterConfigService.fingerprint(null)).isNull();
    assertThat(DataciteMinterConfigService.fingerprint("abc")).isNull();
  }

  @Test
  void passwordFingerprint_reflectsCurrentRow() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    when(dao.findSingleton()).thenReturn(cfg);
    service.setCredential("hello-world", "ops");
    assertThat(service.passwordFingerprint()).isNotNull();
    assertThat(HEX8.matcher(service.passwordFingerprint()).matches()).isTrue();
  }

  // ─── SHA-256 canonical match ────────────────────────────────────────────

  @Test
  void sha256Hex_matchesCanonicalDigest() {
    // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
    String hash = DataciteMinterConfigService.sha256Hex("hello");
    assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
  }

  // ─── normaliseState ──────────────────────────────────────────────────

  @Test
  void normaliseState_acceptsAllThreeCanonicalValues() {
    assertThat(DataciteMinterConfigService.normaliseState("draft")).isEqualTo("draft");
    assertThat(DataciteMinterConfigService.normaliseState("registered")).isEqualTo("registered");
    assertThat(DataciteMinterConfigService.normaliseState("findable")).isEqualTo("findable");
  }

  @Test
  void normaliseState_defaultsBlankToDraft() {
    assertThat(DataciteMinterConfigService.normaliseState(null)).isEqualTo("draft");
    assertThat(DataciteMinterConfigService.normaliseState("")).isEqualTo("draft");
    assertThat(DataciteMinterConfigService.normaliseState("   ")).isEqualTo("draft");
  }

  @Test
  void cipher_isLazilyBuiltAndCached() {
    CredentialCipher first = service.cipher();
    CredentialCipher second = service.cipher();
    assertThat(first).isSameAs(second);
  }
}
