package de.dlr.shepard.plugins.v1compat.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1ConfigIO;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1ConfigPatchIO;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — unit tests for the admin REST shape. Service is
 * mocked so the test stays unit-level; the contract under test is
 * the IO projection + the actor-sub propagation from
 * {@link SecurityContext} into the audit field on the singleton.
 */
class LegacyV1ConfigAdminRestTest {

  private LegacyV1ConfigService service;
  private LegacyV1ConfigAdminRest rest;

  @BeforeEach
  void setUp() {
    service = mock(LegacyV1ConfigService.class);
    rest = new LegacyV1ConfigAdminRest();
    rest.service = service;
  }

  @Test
  void getConfig_returnsCurrentSingletonProjected() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    row.setAppId("01HF-AAA");
    when(service.current()).thenReturn(row);

    Response response = rest.getConfig();

    assertThat(response.getStatus()).isEqualTo(200);
    LegacyV1ConfigIO body = (LegacyV1ConfigIO) response.getEntity();
    assertThat(body.enabled()).isTrue();
    assertThat(body.appId()).isEqualTo("01HF-AAA");
    assertThat(body.updatedAt()).isNull();
    assertThat(body.updatedBy()).isNull();
  }

  @Test
  void patch_emptyBody_returnsCurrentNoOp() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    when(service.current()).thenReturn(row);

    Response response = rest.patchConfig(new LegacyV1ConfigPatchIO(null, null), null);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(service, org.mockito.Mockito.never()).setEnabled(org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    verify(service, org.mockito.Mockito.never()).setSuppressDeprecationHeaders(org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void patch_nullBody_returnsCurrentNoOp() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    when(service.current()).thenReturn(row);

    Response response = rest.patchConfig(null, null);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(service, org.mockito.Mockito.never()).setEnabled(org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    verify(service, org.mockito.Mockito.never()).setSuppressDeprecationHeaders(org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void patch_enabledFalse_propagatesActorSub() {
    LegacyV1Config post = new LegacyV1Config();
    post.setEnabled(false);
    post.setUpdatedBy("admin@example");
    post.setUpdatedAt(1_700_000_001_000L);
    when(service.setEnabled(eq(false), eq("admin@example"))).thenReturn(post);

    SecurityContext sc = mock(SecurityContext.class);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("admin@example");
    when(sc.getUserPrincipal()).thenReturn(principal);

    Response response = rest.patchConfig(new LegacyV1ConfigPatchIO(false), sc);

    assertThat(response.getStatus()).isEqualTo(200);
    LegacyV1ConfigIO body = (LegacyV1ConfigIO) response.getEntity();
    assertThat(body.enabled()).isFalse();
    assertThat(body.updatedBy()).isEqualTo("admin@example");
    assertThat(body.updatedAt()).isNotNull();
    verify(service).setEnabled(false, "admin@example");
  }

  @Test
  void patch_enabledTrue_propagatesActor() {
    LegacyV1Config post = new LegacyV1Config();
    post.setEnabled(true);
    post.setUpdatedBy("admin@example");
    when(service.setEnabled(eq(true), eq("admin@example"))).thenReturn(post);

    SecurityContext sc = mock(SecurityContext.class);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("admin@example");
    when(sc.getUserPrincipal()).thenReturn(principal);

    Response response = rest.patchConfig(new LegacyV1ConfigPatchIO(true), sc);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(service).setEnabled(true, "admin@example");
  }

  @Test
  void patch_nullSecurityContext_passesNullActor() {
    LegacyV1Config post = new LegacyV1Config();
    post.setEnabled(false);
    when(service.setEnabled(eq(false), eq(null))).thenReturn(post);

    Response response = rest.patchConfig(new LegacyV1ConfigPatchIO(false), null);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(service).setEnabled(false, null);
  }

  @Test
  void patch_nullPrincipal_passesNullActor() {
    LegacyV1Config post = new LegacyV1Config();
    post.setEnabled(true);
    when(service.setEnabled(eq(true), eq(null))).thenReturn(post);

    SecurityContext sc = mock(SecurityContext.class);
    when(sc.getUserPrincipal()).thenReturn(null);

    Response response = rest.patchConfig(new LegacyV1ConfigPatchIO(true), sc);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(service).setEnabled(true, null);
  }

  // ─── V1C1: suppressDeprecationHeaders ─────────────────────────────────

  @Test
  void patch_suppressDeprecationHeaders_true_callsService() {
    LegacyV1Config post = new LegacyV1Config();
    post.setSuppressDeprecationHeaders(true);
    post.setUpdatedBy("admin@example");
    when(service.current()).thenReturn(new LegacyV1Config());
    when(service.setSuppressDeprecationHeaders(eq(true), eq("admin@example"))).thenReturn(post);

    SecurityContext sc = mock(SecurityContext.class);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("admin@example");
    when(sc.getUserPrincipal()).thenReturn(principal);

    Response response = rest.patchConfig(new LegacyV1ConfigPatchIO(null, true), sc);

    assertThat(response.getStatus()).isEqualTo(200);
    LegacyV1ConfigIO body = (LegacyV1ConfigIO) response.getEntity();
    assertThat(body.suppressDeprecationHeaders()).isTrue();
    verify(service).setSuppressDeprecationHeaders(true, "admin@example");
    verify(service, org.mockito.Mockito.never()).setEnabled(org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void patch_suppressDeprecationHeaders_false_callsService() {
    LegacyV1Config post = new LegacyV1Config();
    post.setSuppressDeprecationHeaders(false);
    when(service.current()).thenReturn(new LegacyV1Config());
    when(service.setSuppressDeprecationHeaders(eq(false), eq(null))).thenReturn(post);

    Response response = rest.patchConfig(new LegacyV1ConfigPatchIO(null, false), null);

    assertThat(response.getStatus()).isEqualTo(200);
    LegacyV1ConfigIO body = (LegacyV1ConfigIO) response.getEntity();
    assertThat(body.suppressDeprecationHeaders()).isFalse();
    verify(service).setSuppressDeprecationHeaders(false, null);
  }

  @Test
  void patch_bothFields_callsBothServices() {
    LegacyV1Config post = new LegacyV1Config();
    post.setEnabled(false);
    post.setSuppressDeprecationHeaders(true);
    when(service.current()).thenReturn(new LegacyV1Config());
    when(service.setEnabled(eq(false), eq(null))).thenReturn(post);
    when(service.setSuppressDeprecationHeaders(eq(true), eq(null))).thenReturn(post);

    Response response = rest.patchConfig(new LegacyV1ConfigPatchIO(false, true), null);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(service).setEnabled(false, null);
    verify(service).setSuppressDeprecationHeaders(true, null);
  }

  @Test
  void getConfig_includesSuppressDeprecationHeadersInProjection() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    row.setSuppressDeprecationHeaders(true);
    row.setAppId("01HF-BBB");
    when(service.current()).thenReturn(row);

    Response response = rest.getConfig();

    assertThat(response.getStatus()).isEqualTo(200);
    LegacyV1ConfigIO body = (LegacyV1ConfigIO) response.getEntity();
    assertThat(body.suppressDeprecationHeaders()).isTrue();
    assertThat(body.stripAppIdFromResponses()).isFalse(); // default
    assertThat(body.appId()).isEqualTo("01HF-BBB");
  }
}
