package de.dlr.shepard.v2.admin.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.v2.admin.semantic.io.PatchSemanticConfigIO;
import de.dlr.shepard.v2.admin.semantic.io.SemanticConfigIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SEMA-V6-003 — unit tests for {@link SemanticConfigRest}.
 *
 * <p>Tests cover: GET returns current config, PATCH applies merge fields,
 * PATCH ignores null fields (merge semantics), PATCH rejects invalid
 * annotationMode, and auth guards (401/403).
 */
class SemanticConfigRestTest {

  private OntologyConfigService configService;
  private AuthenticationContext authCtx;
  private SecurityContext adminSc;

  private SemanticConfigRest rest;

  @BeforeEach
  void setUp() {
    configService = mock(OntologyConfigService.class);
    authCtx       = mock(AuthenticationContext.class);
    adminSc       = adminSecurityContext("alice");

    rest = new SemanticConfigRest();
    rest.configService = configService;
    rest.authenticationContext = authCtx;
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static SemanticConfig defaultConfig() {
    SemanticConfig c = new SemanticConfig();
    c.setAppId("test-app-id");
    c.setPreseedEnabled(true);
    c.setDisabledBundles(new ArrayList<>());
    c.setAnnotationMode("PERMISSIVE");
    c.setSuggestionEnabled(false);
    return c;
  }

  private static SecurityContext adminSecurityContext(String name) {
    SecurityContext sc = mock(SecurityContext.class);
    Principal p = mock(Principal.class);
    when(p.getName()).thenReturn(name);
    when(sc.getUserPrincipal()).thenReturn(p);
    when(sc.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);
    return sc;
  }

  private static SecurityContext nonAdminSecurityContext() {
    SecurityContext sc = mock(SecurityContext.class);
    Principal p = mock(Principal.class);
    when(p.getName()).thenReturn("bob");
    when(sc.getUserPrincipal()).thenReturn(p);
    when(sc.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);
    return sc;
  }

  // ─── GET ─────────────────────────────────────────────────────────────────

  @Test
  void getConfigReturnsCurrentConfig() {
    SemanticConfig cfg = defaultConfig();
    when(configService.loadSingleton()).thenReturn(cfg);

    Response response = rest.getConfig(adminSc);

    assertEquals(200, response.getStatus());
    SemanticConfigIO io = (SemanticConfigIO) response.getEntity();
    assertEquals("test-app-id", io.getAppId());
    assertTrue(io.isPreseedEnabled());
    assertEquals("PERMISSIVE", io.getAnnotationMode());
    assertFalse(io.isSuggestionEnabled());
  }

  @Test
  void getConfigReturns403ForNonAdmin() {
    Response response = rest.getConfig(nonAdminSecurityContext());
    assertEquals(403, response.getStatus());
  }

  @Test
  void getConfigReturns401WithoutPrincipal() {
    SecurityContext sc = mock(SecurityContext.class);
    when(sc.getUserPrincipal()).thenReturn(null);
    Response response = rest.getConfig(sc);
    assertEquals(401, response.getStatus());
  }

  // ─── PATCH ───────────────────────────────────────────────────────────────

  @Test
  void patchAppliesNonNullFields() {
    SemanticConfig cfg = defaultConfig();
    when(configService.loadSingleton()).thenReturn(cfg);
    when(configService.patchConfig(any(SemanticConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    PatchSemanticConfigIO patch = new PatchSemanticConfigIO();
    patch.setAnnotationMode("STRICT");
    patch.setSuggestionEnabled(true);
    patch.setDefaultVocabularyAppId("vocab-123");

    Response response = rest.patchConfig(patch, adminSc);

    assertEquals(200, response.getStatus());
    SemanticConfigIO io = (SemanticConfigIO) response.getEntity();
    assertEquals("STRICT",    io.getAnnotationMode());
    assertTrue(io.isSuggestionEnabled());
    assertEquals("vocab-123", io.getDefaultVocabularyAppId());
    // preseedEnabled was not in patch — should stay true (original value).
    assertTrue(io.isPreseedEnabled());
  }

  @Test
  void patchNullFieldsAreIgnored() {
    SemanticConfig cfg = defaultConfig();
    cfg.setAnnotationMode("STRICT");
    cfg.setSuggestionEnabled(true);
    when(configService.loadSingleton()).thenReturn(cfg);
    when(configService.patchConfig(any(SemanticConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    // patch with all nulls = no-op
    PatchSemanticConfigIO patch = new PatchSemanticConfigIO();
    // all fields null by default in PatchSemanticConfigIO

    Response response = rest.patchConfig(patch, adminSc);

    assertEquals(200, response.getStatus());
    SemanticConfigIO io = (SemanticConfigIO) response.getEntity();
    // Values unchanged:
    assertEquals("STRICT", io.getAnnotationMode());
    assertTrue(io.isSuggestionEnabled());
  }

  @Test
  void patchReturns400ForInvalidAnnotationMode() {
    SemanticConfig cfg = defaultConfig();
    when(configService.loadSingleton()).thenReturn(cfg);

    PatchSemanticConfigIO patch = new PatchSemanticConfigIO();
    patch.setAnnotationMode("INVALID_MODE");

    Response response = rest.patchConfig(patch, adminSc);

    assertEquals(400, response.getStatus());
  }

  @Test
  void patchReturns403ForNonAdmin() {
    PatchSemanticConfigIO patch = new PatchSemanticConfigIO();
    Response response = rest.patchConfig(patch, nonAdminSecurityContext());
    assertEquals(403, response.getStatus());
  }

  @Test
  void patchNullBodyIsNoOp() {
    SemanticConfig cfg = defaultConfig();
    when(configService.loadSingleton()).thenReturn(cfg);

    Response response = rest.patchConfig(null, adminSc);

    assertEquals(200, response.getStatus());
    // loadSingleton called but patchConfig not called.
    verify(configService).loadSingleton();
  }

  @Test
  void patchClearsSuggestionModelIdOnEmptyString() {
    SemanticConfig cfg = defaultConfig();
    cfg.setSuggestionModelId("old-model");
    when(configService.loadSingleton()).thenReturn(cfg);
    when(configService.patchConfig(any(SemanticConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    PatchSemanticConfigIO patch = new PatchSemanticConfigIO();
    patch.setSuggestionModelId("");  // empty string → clear (null)

    Response response = rest.patchConfig(patch, adminSc);

    assertEquals(200, response.getStatus());
    SemanticConfigIO io = (SemanticConfigIO) response.getEntity();
    // empty string is treated as clear → null in the entity
    // SemanticConfigIO.from maps null suggestionModelId → null in IO
    assertEquals(null, io.getSuggestionModelId());
  }
}
