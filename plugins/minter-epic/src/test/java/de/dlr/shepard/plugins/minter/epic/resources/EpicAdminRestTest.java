package de.dlr.shepard.plugins.minter.epic.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient.EpicHttpResponse;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import de.dlr.shepard.plugins.minter.epic.io.EpicCredentialIO;
import de.dlr.shepard.plugins.minter.epic.io.EpicCredentialSetIO;
import de.dlr.shepard.plugins.minter.epic.io.EpicMinterConfigIO;
import de.dlr.shepard.plugins.minter.epic.io.EpicTestConnectionIO;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KIP1c — unit tests for the admin REST surface. No Quarkus boot —
 * the {@link EpicMinterConfigService} and {@link EpicHttpClient}
 * are mocked.
 */
class EpicAdminRestTest {

  private EpicMinterConfigService service;
  private EpicHttpClient http;
  private EpicAdminRest rest;
  private SecurityContext security;

  @BeforeEach
  void setUp() {
    service = mock(EpicMinterConfigService.class);
    http = mock(EpicHttpClient.class);
    rest = new EpicAdminRest();
    rest.service = service;
    rest.http = http;
    security = mock(SecurityContext.class);
    Principal p = mock(Principal.class);
    when(p.getName()).thenReturn("operator-alice");
    when(security.getUserPrincipal()).thenReturn(p);
  }

  // ─── annotation gates ───────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = EpicAdminRest.class.getAnnotation(RolesAllowed.class);
    assertThat(gate).isNotNull();
    assertThat(gate.value()).containsExactly(Constants.INSTANCE_ADMIN_ROLE);
  }

  @Test
  void pathIsV2() {
    Path p = EpicAdminRest.class.getAnnotation(Path.class);
    assertThat(p).isNotNull();
    assertThat(p.value()).isEqualTo("/v2/admin/minters/epic");
  }

  // ─── POST /credential ───────────────────────────────────────────────────

  @Test
  void setCredential_returnsFingerprint() {
    EpicMinterConfig saved = new EpicMinterConfig();
    saved.setCredentialHash("deadbeef".repeat(8));
    when(service.setCredential(anyString(), anyString())).thenReturn(saved);

    EpicCredentialIO body = new EpicCredentialIO("user:the-credential");

    Response r = rest.setCredential(body, security);

    assertThat(r.getStatus()).isEqualTo(200);
    EpicCredentialSetIO out = (EpicCredentialSetIO) r.getEntity();
    assertThat(out.credentialSet()).isTrue();
    assertThat(out.fingerprint()).isEqualTo("deadbeef");
  }

  @Test
  void setCredential_rejectsEmptyBodyWithProblemJson() {
    EpicCredentialIO body = new EpicCredentialIO("");

    Response r = rest.setCredential(body, security);

    assertThat(r.getStatus()).isEqualTo(400);
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertThat(problem.title()).contains("Empty");
  }

  @Test
  void setCredential_rejectsNullBody() {
    Response r = rest.setCredential(null, security);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  // ─── DELETE /credential ─────────────────────────────────────────────────

  @Test
  void clearCredential_resetsToMaskedShape() {
    EpicMinterConfig saved = new EpicMinterConfig();
    when(service.clearCredential(anyString())).thenReturn(saved);

    Response r = rest.clearCredential(security);

    assertThat(r.getStatus()).isEqualTo(200);
    EpicMinterConfigIO body = (EpicMinterConfigIO) r.getEntity();
    assertThat(body.credentialSet()).isFalse();
  }

  // ─── POST /test-connection ──────────────────────────────────────────────

  @Test
  void testConnection_reachableWhenApiResponds2xx() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    cfg.setApiBaseUrl("https://handle.argo.grnet.gr/api");
    when(service.current()).thenReturn(cfg);
    when(http.getDiagnostic(anyString(), any())).thenReturn(new EpicHttpResponse(200, "OK"));

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    EpicTestConnectionIO body = (EpicTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isTrue();
    assertThat(body.statusCode()).isEqualTo(200);
    assertThat(body.apiBaseUrl()).isEqualTo("https://handle.argo.grnet.gr/api");
  }

  @Test
  void testConnection_unreachableOnNetworkError() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    cfg.setApiBaseUrl("https://handle.argo.grnet.gr/api");
    when(service.current()).thenReturn(cfg);
    when(http.getDiagnostic(anyString(), any())).thenReturn(new EpicHttpResponse(0, "network: oops"));

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    EpicTestConnectionIO body = (EpicTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isFalse();
    assertThat(body.statusCode()).isEqualTo(0);
    assertThat(body.detail()).contains("network");
  }

  @Test
  void testConnection_reportsUnconfiguredApiBaseUrl() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    cfg.setApiBaseUrl(null);
    when(service.current()).thenReturn(cfg);

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    EpicTestConnectionIO body = (EpicTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isFalse();
    assertThat(body.detail()).contains("not configured");
  }

  @Test
  void testConnection_unreachableOn5xx() {
    EpicMinterConfig cfg = new EpicMinterConfig();
    cfg.setApiBaseUrl("https://handle.argo.grnet.gr/api");
    when(service.current()).thenReturn(cfg);
    when(http.getDiagnostic(anyString(), any())).thenReturn(new EpicHttpResponse(503, "down"));

    Response r = rest.testConnection();

    EpicTestConnectionIO body = (EpicTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isFalse();
    assertThat(body.statusCode()).isEqualTo(503);
  }
}
