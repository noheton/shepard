package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import de.dlr.shepard.v2.admin.jupyter.io.JupyterConfigIO;
import de.dlr.shepard.v2.admin.jupyter.services.JupyterConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** V2CONV-A4 — unit tests for {@link JupyterConfigDescriptor}. */
class JupyterConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JupyterConfigService service;
  private JupyterConfigDescriptor descriptor;

  private static JupyterConfig cfg(boolean enabled, String hubUrl) {
    JupyterConfig c = new JupyterConfig();
    c.setEnabled(enabled);
    c.setHubUrl(hubUrl);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(JupyterConfigService.class);
    Mockito.when(service.getDefaultHubUrl()).thenReturn(null);
    descriptor = new JupyterConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsJupyter() {
    assertEquals("jupyter", descriptor.featureName());
  }

  @Test
  void enableFlip() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(false, "https://hub.example.org"));
    Mockito.when(service.patch(Mockito.anyBoolean(), Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1)));
    JupyterConfigIO io = descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":true}"));
    assertTrue(io.enabled());
    Mockito.verify(service).patch(true, "https://hub.example.org");
  }

  @Test
  void explicitNullEnabledLeavesAlone() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, "https://hub.example.org"));
    Mockito.when(service.patch(Mockito.anyBoolean(), Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1)));
    descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":null}"));
    Mockito.verify(service).patch(true, "https://hub.example.org");
  }

  @Test
  void hubUrlNullClearsToDefault() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, "https://hub.example.org"));
    Mockito.when(service.patch(Mockito.anyBoolean(), Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1)));
    JupyterConfigIO io = descriptor.applyMergePatch(MAPPER.readTree("{\"hubUrl\":null}"));
    assertFalse(io.enabled() && io.hubUrl() != null && io.hubUrl().isEmpty());
    Mockito.verify(service).patch(true, null);
  }

  @Test
  void invalidHubUrlThrows() {
    Mockito.when(service.current()).thenReturn(cfg(true, null));
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"hubUrl\":\"ftp://nope\"}"))
    );
    assertEquals(JupyterConfigDescriptor.PROBLEM_TYPE_INVALID_HUB_URL, ex.getProblemType());
  }

  @Test
  void urlValidationAcceptsHttpAndHttps() {
    assertTrue(JupyterConfigDescriptor.isValidAbsoluteHttpUrl("https://hub.example.org/jupyterhub"));
    assertTrue(JupyterConfigDescriptor.isValidAbsoluteHttpUrl("http://hub.example.org:8443"));
    assertFalse(JupyterConfigDescriptor.isValidAbsoluteHttpUrl("hub.example.org"));
    assertFalse(JupyterConfigDescriptor.isValidAbsoluteHttpUrl("file:///etc/passwd"));
    assertFalse(JupyterConfigDescriptor.isValidAbsoluteHttpUrl(null));
  }
}
