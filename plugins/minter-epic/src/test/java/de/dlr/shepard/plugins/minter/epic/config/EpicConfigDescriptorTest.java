package de.dlr.shepard.plugins.minter.epic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import de.dlr.shepard.plugins.minter.epic.io.EpicMinterConfigIO;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService.EpicPatch;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * APISIMP-MINTER-CRED-CONFIG-UNIFY / V2CONV-A7 — unit tests for
 * {@link EpicConfigDescriptor}. No Quarkus boot — the
 * {@link EpicMinterConfigService} is mocked.
 */
class EpicConfigDescriptorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private EpicMinterConfigService service;
  private EpicConfigDescriptor descriptor;

  @BeforeEach
  void setUp() {
    service = mock(EpicMinterConfigService.class);
    descriptor = new EpicConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsMinterEpic() {
    assertThat(descriptor.featureName()).isEqualTo("minter-epic");
  }

  @Test
  void descriptionIsNonBlank() {
    assertThat(descriptor.description()).isNotBlank();
  }

  @Test
  void currentShape_delegatesToService() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    cfg.setEnabled(true);
    cfg.setApiBaseUrl("https://handle.argo.grnet.gr/api");
    cfg.setHandlePrefix("21.T11148");
    when(service.current()).thenReturn(cfg);

    EpicMinterConfigIO io = descriptor.currentShape();

    assertThat(io.enabled()).isTrue();
    assertThat(io.apiBaseUrl()).isEqualTo("https://handle.argo.grnet.gr/api");
    assertThat(io.handlePrefix()).isEqualTo("21.T11148");
  }

  @Test
  void enabledFlip() throws Exception {
    EpicMinterConfig saved = new EpicMinterConfig();
    saved.setEnabled(true);
    ArgumentCaptor<EpicPatch> captor = ArgumentCaptor.forClass(EpicPatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"enabled\": true}"));

    assertThat(captor.getValue().enabled).isTrue();
  }

  @Test
  void explicitNullOnEnabledLeavesAlone() throws Exception {
    EpicMinterConfig saved = new EpicMinterConfig();
    ArgumentCaptor<EpicPatch> captor = ArgumentCaptor.forClass(EpicPatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"enabled\": null}"));

    assertThat(captor.getValue().enabled).isNull();
  }

  @Test
  void apiBaseUrlSet() throws Exception {
    EpicMinterConfig saved = new EpicMinterConfig();
    saved.setApiBaseUrl("https://handle.argo.grnet.gr/api");
    ArgumentCaptor<EpicPatch> captor = ArgumentCaptor.forClass(EpicPatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"apiBaseUrl\": \"https://handle.argo.grnet.gr/api\"}"));

    EpicPatch patch = captor.getValue();
    assertThat(patch.apiBaseUrlTouched).isTrue();
    assertThat(patch.apiBaseUrl).isEqualTo("https://handle.argo.grnet.gr/api");
  }

  @Test
  void apiBaseUrlNullClears() throws Exception {
    EpicMinterConfig saved = new EpicMinterConfig();
    ArgumentCaptor<EpicPatch> captor = ArgumentCaptor.forClass(EpicPatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"apiBaseUrl\": null}"));

    EpicPatch patch = captor.getValue();
    assertThat(patch.apiBaseUrlTouched).isTrue();
    assertThat(patch.apiBaseUrl).isNull();
  }

  @Test
  void absentFieldsAreNotTouched() throws Exception {
    EpicMinterConfig saved = new EpicMinterConfig();
    ArgumentCaptor<EpicPatch> captor = ArgumentCaptor.forClass(EpicPatch.class);
    when(service.patch(captor.capture(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{}"));

    EpicPatch patch = captor.getValue();
    assertThat(patch.enabled).isNull();
    assertThat(patch.apiBaseUrlTouched).isFalse();
    assertThat(patch.handlePrefixTouched).isFalse();
  }

  @Test
  void credentialHashIsReadOnly() {
    assertThatThrownBy(() ->
      descriptor.applyMergePatch(mapper.readTree("{\"credentialHash\": \"abc\"}"))
    )
      .isInstanceOf(ConfigPatchException.class)
      .hasMessageContaining("credentialHash");
  }

  @Test
  void credentialKeyIsReadOnly() {
    assertThatThrownBy(() ->
      descriptor.applyMergePatch(mapper.readTree("{\"credentialKey\": \"abc\"}"))
    )
      .isInstanceOf(ConfigPatchException.class)
      .hasMessageContaining("credentialKey");
  }

  // ─── credential write-only field ────────────────────────────────────────

  @Test
  void credential_setsCredential() throws Exception {
    EpicMinterConfig saved = new EpicMinterConfig();
    when(service.setCredential(anyString(), anyString())).thenReturn(saved);
    when(service.patch(any(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"credential\": \"user:the-secret\"}"));

    verify(service).setCredential("user:the-secret", "admin-config-patch");
  }

  @Test
  void credential_nullClearsCredential() throws Exception {
    EpicMinterConfig saved = new EpicMinterConfig();
    when(service.clearCredential(anyString())).thenReturn(saved);
    when(service.patch(any(), anyString())).thenReturn(saved);

    descriptor.applyMergePatch(mapper.readTree("{\"credential\": null}"));

    verify(service).clearCredential("admin-config-patch");
  }

  @Test
  void credential_blankThrowsConfigPatchException() {
    assertThatThrownBy(() ->
      descriptor.applyMergePatch(mapper.readTree("{\"credential\": \"\"}"))
    )
      .isInstanceOf(ConfigPatchException.class);
  }
}
