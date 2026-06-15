package de.dlr.shepard.plugins.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.ai.entities.AiCapabilityConfig;
import de.dlr.shepard.plugins.ai.io.AiCapabilityConfigIO;
import de.dlr.shepard.plugins.ai.services.AiCapabilityConfigService;
import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** APISIMP-AI-ADMIN-REST — unit tests for {@link AiConfigDescriptor}. */
class AiConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AiCapabilityConfigService service;
  private AiConfigDescriptor descriptor;

  @BeforeEach
  void setUp() {
    service = Mockito.mock(AiCapabilityConfigService.class);
    descriptor = new AiConfigDescriptor();
    descriptor.service = service;

    // getAllConfigs() returns one skeleton per capability
    List<AiCapabilityConfig> allConfigs = Arrays.stream(AiCapability.values())
      .map(cap -> {
        AiCapabilityConfig cfg = new AiCapabilityConfig();
        cfg.setCapability(cap.name());
        cfg.setEnabled(Boolean.FALSE);
        return cfg;
      })
      .toList();
    Mockito.when(service.getAllConfigs()).thenReturn(allConfigs);
    Mockito.when(service.upsertConfig(Mockito.any(), Mockito.any()))
      .thenAnswer(inv -> {
        AiCapabilityConfig cfg = new AiCapabilityConfig();
        cfg.setCapability(((AiCapability) inv.getArgument(0)).name());
        cfg.setEnabled(Boolean.FALSE);
        return cfg;
      });
  }

  @Test
  void featureNameIsAi() {
    assertThat(descriptor.featureName()).isEqualTo("ai");
  }

  @Test
  void descriptionIsNonBlank() {
    assertThat(descriptor.description()).isNotBlank();
  }

  @Test
  void currentShapeReturnsOneEntryPerCapability() {
    List<AiCapabilityConfigIO> shape = descriptor.currentShape();
    assertThat(shape).hasSize(AiCapability.values().length);
  }

  @Test
  void emptyPatchIsNoOp() throws Exception {
    List<AiCapabilityConfigIO> result = descriptor.applyMergePatch(MAPPER.readTree("{}"));
    assertThat(result).hasSize(AiCapability.values().length);
    Mockito.verify(service, Mockito.never()).upsertConfig(Mockito.any(), Mockito.any());
  }

  @Test
  void patchSingleSlotCallsUpsert() throws Exception {
    descriptor.applyMergePatch(MAPPER.readTree("{\"TEXT\":{\"enabled\":true}}"));
    ArgumentCaptor<AiCapability> capCaptor = ArgumentCaptor.forClass(AiCapability.class);
    ArgumentCaptor<AiCapabilityConfigIO> ioCaptor = ArgumentCaptor.forClass(AiCapabilityConfigIO.class);
    Mockito.verify(service).upsertConfig(capCaptor.capture(), ioCaptor.capture());
    assertThat(capCaptor.getValue()).isEqualTo(AiCapability.TEXT);
    assertThat(ioCaptor.getValue().enabled).isTrue();
  }

  @Test
  void patchMultipleSlotsCallsUpsertForEach() throws Exception {
    descriptor.applyMergePatch(
      MAPPER.readTree("{\"TEXT\":{\"enabled\":true},\"EMBEDDING\":{\"model\":\"e5-small\"}}")
    );
    Mockito.verify(service, Mockito.times(2)).upsertConfig(Mockito.any(), Mockito.any());
  }

  @Test
  void absentFieldsInSlotPatchResultInNullIo() throws Exception {
    descriptor.applyMergePatch(MAPPER.readTree("{\"TEXT\":{\"enabled\":true}}"));
    ArgumentCaptor<AiCapabilityConfigIO> ioCaptor = ArgumentCaptor.forClass(AiCapabilityConfigIO.class);
    Mockito.verify(service).upsertConfig(Mockito.any(), ioCaptor.capture());
    AiCapabilityConfigIO io = ioCaptor.getValue();
    assertThat(io.endpointUrl).isNull(); // absent = leave alone (null → service skips)
    assertThat(io.model).isNull();
    assertThat(io.enabled).isTrue();
  }

  @Test
  void patchWithApiKeyAndTemperature() throws Exception {
    descriptor.applyMergePatch(
      MAPPER.readTree("{\"TEXT\":{\"apiKey\":\"sk-abc\",\"temperature\":0.7}}")
    );
    ArgumentCaptor<AiCapabilityConfigIO> ioCaptor = ArgumentCaptor.forClass(AiCapabilityConfigIO.class);
    Mockito.verify(service).upsertConfig(Mockito.eq(AiCapability.TEXT), ioCaptor.capture());
    assertThat(ioCaptor.getValue().apiKey).isEqualTo("sk-abc");
    assertThat(ioCaptor.getValue().temperature).isEqualTo(0.7);
  }

  @Test
  void unknownCapabilityKeyThrows400() {
    assertThatThrownBy(
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"UNKNOWN_CAP\":{\"enabled\":true}}"))
    )
      .isInstanceOf(ConfigPatchException.class)
      .satisfies(e -> {
        ConfigPatchException cpe = (ConfigPatchException) e;
        assertThat(cpe.getProblemType()).contains("unknown-capability");
        assertThat(cpe.getDetail()).contains("UNKNOWN_CAP");
      });
  }

  @Test
  void nonObjectSlotPatchThrows400() {
    assertThatThrownBy(
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"TEXT\":true}"))
    )
      .isInstanceOf(ConfigPatchException.class)
      .satisfies(e -> {
        ConfigPatchException cpe = (ConfigPatchException) e;
        assertThat(cpe.getProblemType()).contains("invalid-slot-patch");
      });
  }

  @Test
  void nullFieldInSlotPatchPassesNullToService() throws Exception {
    descriptor.applyMergePatch(MAPPER.readTree("{\"TEXT\":{\"endpointUrl\":null}}"));
    ArgumentCaptor<AiCapabilityConfigIO> ioCaptor = ArgumentCaptor.forClass(AiCapabilityConfigIO.class);
    Mockito.verify(service).upsertConfig(Mockito.any(), ioCaptor.capture());
    // null signals "clear" to the service
    assertThat(ioCaptor.getValue().endpointUrl).isNull();
  }

  @Test
  void transcriptionAndModerationCapabilitiesAreValid() throws Exception {
    // smoke: the 2 newer capability values are accepted by the descriptor
    descriptor.applyMergePatch(
      MAPPER.readTree("{\"TRANSCRIPTION\":{\"enabled\":false},\"MODERATION\":{\"enabled\":false}}")
    );
    Mockito.verify(service, Mockito.times(2)).upsertConfig(Mockito.any(), Mockito.any());
  }
}
