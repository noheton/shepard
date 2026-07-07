package de.dlr.shepard.plugins.minter.datacite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteMinterConfigIO;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService.DatacitePatch;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * APISIMP-MINTER-CRED-CONFIG-UNIFY / V2CONV-A7 — unit tests for
 * {@link DataciteConfigDescriptor}. No Quarkus boot — the
 * {@link DataciteMinterConfigService} is mocked.
 */
class DataciteConfigDescriptorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private DataciteMinterConfigService service;
  private DataciteConfigDescriptor descriptor;

  @BeforeEach
  void setUp() {
    service = mock(DataciteMinterConfigService.class);
    descriptor = new DataciteConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsMinterDatacite() {
    assertThat(descriptor.featureName()).isEqualTo("minter-datacite");
  }

  @Test
  void descriptionIsNonBlank() {
    assertThat(descriptor.description()).isNotBlank();
  }

  @Test
  void currentShape_delegatesToService() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setEnabled(true);
    cfg.setApiBaseUrl("https://api.test.datacite.org");
    cfg.setHandlePrefix("10.5072");
    cfg.setRepositoryId("DLR.ZLP");
    cfg.setPublisher("DLR e.V.");
    when(service.current()).thenReturn(cfg);

    DataciteMinterConfigIO io = descriptor.currentShape();

    assertThat(io.enabled()).isTrue();
    assertThat(io.repositoryId()).isEqualTo("DLR.ZLP");
    assertThat(io.publisher()).isEqualTo("DLR e.V.");
  }

  @Test
  void enabledFlip() throws Exception {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    saved.setEnabled(false);
    ArgumentCaptor<DatacitePatch> captor = ArgumentCaptor.forClass(DatacitePatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"enabled\": false}"));

    assertThat(captor.getValue().enabled).isFalse();
  }

  @Test
  void repositoryIdSet() throws Exception {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    saved.setRepositoryId("DLR.ZLP");
    ArgumentCaptor<DatacitePatch> captor = ArgumentCaptor.forClass(DatacitePatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"repositoryId\": \"DLR.ZLP\"}"));

    DatacitePatch patch = captor.getValue();
    assertThat(patch.repositoryIdTouched).isTrue();
    assertThat(patch.repositoryId).isEqualTo("DLR.ZLP");
  }

  @Test
  void defaultStateNullClears() throws Exception {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    ArgumentCaptor<DatacitePatch> captor = ArgumentCaptor.forClass(DatacitePatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"defaultState\": null}"));

    DatacitePatch patch = captor.getValue();
    assertThat(patch.defaultStateTouched).isTrue();
    assertThat(patch.defaultState).isNull();
  }

  @Test
  void absentFieldsAreNotTouched() throws Exception {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    ArgumentCaptor<DatacitePatch> captor = ArgumentCaptor.forClass(DatacitePatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{}"));

    DatacitePatch patch = captor.getValue();
    assertThat(patch.enabled).isNull();
    assertThat(patch.apiBaseUrlTouched).isFalse();
    assertThat(patch.repositoryIdTouched).isFalse();
    assertThat(patch.publisherTouched).isFalse();
    assertThat(patch.landingPageBaseTouched).isFalse();
    assertThat(patch.defaultStateTouched).isFalse();
  }

  @Test
  void passwordHashIsReadOnly() {
    assertThatThrownBy(() ->
      descriptor.applyMergePatch(mapper.readTree("{\"passwordHash\": \"abc\"}"))
    )
      .isInstanceOf(ConfigPatchException.class)
      .hasMessageContaining("passwordHash");
  }

  @Test
  void passwordCipherIsReadOnly() {
    assertThatThrownBy(() ->
      descriptor.applyMergePatch(mapper.readTree("{\"passwordCipher\": \"abc\"}"))
    )
      .isInstanceOf(ConfigPatchException.class)
      .hasMessageContaining("passwordCipher");
  }

  // ─── password write-only field ──────────────────────────────────────────

  @Test
  void password_setsCredential() throws Exception {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    when(service.setCredential(anyString(), anyString())).thenReturn(saved);
    when(service.patch(any(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"password\": \"the-secret\"}"));

    verify(service).setCredential("the-secret", "admin-config-patch");
  }

  @Test
  void password_nullClearsCredential() throws Exception {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    when(service.clearCredential(anyString())).thenReturn(saved);
    when(service.patch(any(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"password\": null}"));

    verify(service).clearCredential("admin-config-patch");
  }

  @Test
  void password_blankThrowsConfigPatchException() {
    assertThatThrownBy(() ->
      descriptor.applyMergePatch(mapper.readTree("{\"password\": \"\"}"))
    )
      .isInstanceOf(ConfigPatchException.class);
  }

  @Test
  void allStringFieldsSet() throws Exception {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    ArgumentCaptor<DatacitePatch> captor = ArgumentCaptor.forClass(DatacitePatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree(
      "{\"apiBaseUrl\": \"https://api.datacite.org\", " +
      "\"handlePrefix\": \"10.5072\", " +
      "\"repositoryId\": \"DLR.ZLP\", " +
      "\"publisher\": \"DLR e.V.\", " +
      "\"landingPageBase\": \"https://shepard.dlr.de/v2\", " +
      "\"defaultState\": \"draft\"}"
    ));

    DatacitePatch patch = captor.getValue();
    assertThat(patch.apiBaseUrlTouched).isTrue();
    assertThat(patch.apiBaseUrl).isEqualTo("https://api.datacite.org");
    assertThat(patch.handlePrefixTouched).isTrue();
    assertThat(patch.handlePrefix).isEqualTo("10.5072");
    assertThat(patch.repositoryIdTouched).isTrue();
    assertThat(patch.repositoryId).isEqualTo("DLR.ZLP");
    assertThat(patch.publisherTouched).isTrue();
    assertThat(patch.publisher).isEqualTo("DLR e.V.");
    assertThat(patch.landingPageBaseTouched).isTrue();
    assertThat(patch.landingPageBase).isEqualTo("https://shepard.dlr.de/v2");
    assertThat(patch.defaultStateTouched).isTrue();
    assertThat(patch.defaultState).isEqualTo("draft");
  }
}
