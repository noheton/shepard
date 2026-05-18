package de.dlr.shepard.v2.aas.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.dlr.shepard.aas.services.AasIdtaTemplateImportService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.template.services.TemplateBodyValidator.InvalidTemplateBodyException;
import de.dlr.shepard.v2.aas.io.AasIdtaImportResultIO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link AasAdminRest} (AAS1d). */
class AasAdminRestTest {

  @Mock
  AasIdtaTemplateImportService importService;

  @Mock
  AuthenticationContext authenticationContext;

  AasAdminRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new AasAdminRest();
    resource.importService = importService;
    resource.authenticationContext = authenticationContext;
    when(authenticationContext.getCurrentUserName()).thenReturn("alice");
  }

  @Test
  void returns200WithImportResult() {
    AasIdtaImportResultIO result = new AasIdtaImportResultIO(List.of(), 3);
    when(importService.importBundledTemplates(any())).thenReturn(result);

    var r = resource.importIdtaTemplates();

    assertEquals(200, r.getStatus());
    assertEquals(result, r.getEntity());
  }

  @Test
  void passesCallerUsernameToService() {
    when(authenticationContext.getCurrentUserName()).thenReturn("bob");
    AasIdtaImportResultIO result = new AasIdtaImportResultIO(List.of(), 0);
    when(importService.importBundledTemplates("bob")).thenReturn(result);

    var r = resource.importIdtaTemplates();

    assertEquals(200, r.getStatus());
  }

  @Test
  void fallsBackToSystemWhenNoAuthentication() {
    when(authenticationContext.getCurrentUserName()).thenReturn(null);
    AasIdtaImportResultIO result = new AasIdtaImportResultIO(List.of(), 0);
    when(importService.importBundledTemplates("system")).thenReturn(result);

    var r = resource.importIdtaTemplates();

    assertEquals(200, r.getStatus());
  }

  @Test
  void returns500OnInvalidTemplateBody() {
    InvalidTemplateBodyException ex = new InvalidTemplateBodyException(List.of("missing submodel key"));
    when(importService.importBundledTemplates(any())).thenThrow(ex);

    var r = resource.importIdtaTemplates();

    assertEquals(500, r.getStatus());
  }

  @Test
  void returns500OnIllegalStateException() {
    when(importService.importBundledTemplates(any()))
        .thenThrow(new IllegalStateException("YAML not found on classpath"));

    var r = resource.importIdtaTemplates();

    assertEquals(500, r.getStatus());
  }
}
