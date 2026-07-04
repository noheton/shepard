package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** V2CONV-A4 — unit tests for {@link SemanticConfigDescriptor}. */
class SemanticConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OntologyConfigService configService;
  private AuthenticationContext authContext;
  private SemanticConfigDescriptor descriptor;

  @BeforeEach
  void setUp() {
    configService = Mockito.mock(OntologyConfigService.class);
    authContext = Mockito.mock(AuthenticationContext.class);
    Mockito.when(authContext.getCurrentUserName()).thenReturn("admin-user");
    Mockito.when(configService.loadSingleton()).thenAnswer(i -> new SemanticConfig());
    Mockito.when(configService.patchConfig(Mockito.any())).thenAnswer(i -> i.getArgument(0));
    descriptor = new SemanticConfigDescriptor();
    descriptor.configService = configService;
    descriptor.authenticationContext = authContext;
  }

  @Test
  void featureNameIsSemantic() {
    assertEquals("semantic", descriptor.featureName());
  }

  @Test
  void appliesPreseedAndStampsActor() throws Exception {
    descriptor.applyMergePatch(MAPPER.readTree("{\"preseedEnabled\":false}"));
    ArgumentCaptor<SemanticConfig> saved = ArgumentCaptor.forClass(SemanticConfig.class);
    Mockito.verify(configService).patchConfig(saved.capture());
    assertEquals(false, saved.getValue().isPreseedEnabled());
    assertEquals("admin-user", saved.getValue().getUpdatedBy());
    assertTrue(saved.getValue().getUpdatedAt() > 0);
  }

  @Test
  void invalidAnnotationModeThrows() {
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"annotationMode\":\"LOOSE\"}"))
    );
    assertEquals(SemanticConfigDescriptor.PROBLEM_TYPE_BAD_MODE, ex.getProblemType());
    Mockito.verify(configService, Mockito.never()).patchConfig(Mockito.any());
  }

  @Test
  void validAnnotationModeNormalisesToUpper() throws Exception {
    descriptor.applyMergePatch(MAPPER.readTree("{\"annotationMode\":\"strict\"}"));
    ArgumentCaptor<SemanticConfig> saved = ArgumentCaptor.forClass(SemanticConfig.class);
    Mockito.verify(configService).patchConfig(saved.capture());
    assertEquals("STRICT", saved.getValue().getAnnotationMode());
  }

  @Test
  void invalidDeletePolicyThrows() {
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"annotationDeletePolicy\":\"everyone\"}"))
    );
    assertEquals(SemanticConfigDescriptor.PROBLEM_TYPE_BAD_DELETE_POLICY, ex.getProblemType());
  }

  @Test
  void blankStringClearsNullableField() throws Exception {
    SemanticConfig seeded = new SemanticConfig();
    seeded.setDefaultVocabularyAppId("some-app-id");
    Mockito.when(configService.loadSingleton()).thenReturn(seeded);
    descriptor.applyMergePatch(MAPPER.readTree("{\"defaultVocabularyAppId\":\"\"}"));
    ArgumentCaptor<SemanticConfig> saved = ArgumentCaptor.forClass(SemanticConfig.class);
    Mockito.verify(configService).patchConfig(saved.capture());
    assertNull(saved.getValue().getDefaultVocabularyAppId());
  }

  @Test
  void emptyPatchStillStampsActor() throws Exception {
    descriptor.applyMergePatch(MAPPER.readTree("{}"));
    ArgumentCaptor<SemanticConfig> saved = ArgumentCaptor.forClass(SemanticConfig.class);
    Mockito.verify(configService).patchConfig(saved.capture());
    assertEquals("admin-user", saved.getValue().getUpdatedBy());
  }
}
