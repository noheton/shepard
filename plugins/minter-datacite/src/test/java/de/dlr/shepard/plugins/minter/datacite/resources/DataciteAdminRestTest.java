package de.dlr.shepard.plugins.minter.datacite.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient.DataciteHttpResponse;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteCredentialIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteTestConnectionIO;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-MINTER-CRED-CONFIG-UNIFY — unit tests for the admin REST surface.
 * No Quarkus boot — the {@link DataciteMinterConfigService} and
 * {@link DataciteHttpClient} are mocked.
 */
class DataciteAdminRestTest {

  private DataciteMinterConfigService service;
  private DataciteHttpClient http;
  private DataciteAdminRest rest;
  private SecurityContext security;

  @BeforeEach
  void setUp() {
    service = mock(DataciteMinterConfigService.class);
    http = mock(DataciteHttpClient.class);
    rest = new DataciteAdminRest();
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
    RolesAllowed gate = DataciteAdminRest.class.getAnnotation(RolesAllowed.class);
    assertThat(gate).isNotNull();
    assertThat(gate.value()).containsExactly(Constants.INSTANCE_ADMIN_ROLE);
  }

  @Test
  void pathIsV2() {
    Path p = DataciteAdminRest.class.getAnnotation(Path.class);
    assertThat(p).isNotNull();
    assertThat(p.value()).isEqualTo("/v2/admin/minters/datacite");
  }

  // ─── POST /credential (tombstoned) ──────────────────────────────────────

  @Test
  void setCredential_returnsGoneWithLocationHeader() {
    Response r = rest.setCredential(new DataciteCredentialIO("the-password"), security);
    assertThat(r.getStatus()).isEqualTo(410);
    assertThat(r.getHeaderString("Location")).isEqualTo("/v2/admin/config/minter-datacite");
  }

  @Test
  void setCredential_nullBodyAlsoReturnsGone() {
    Response r = rest.setCredential(null, security);
    assertThat(r.getStatus()).isEqualTo(410);
    assertThat(r.getHeaderString("Location")).isEqualTo("/v2/admin/config/minter-datacite");
  }

  // ─── DELETE /credential (tombstoned) ────────────────────────────────────

  @Test
  void clearCredential_returnsGoneWithLocationHeader() {
    Response r = rest.clearCredential(security);
    assertThat(r.getStatus()).isEqualTo(410);
    assertThat(r.getHeaderString("Location")).isEqualTo("/v2/admin/config/minter-datacite");
  }

  // ─── POST /test-connection ──────────────────────────────────────────────

  @Test
  void testConnection_reachableWhenDataciteResponds2xx() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setApiBaseUrl("https://api.test.datacite.org");
    when(service.current()).thenReturn(cfg);
    when(http.getDiagnostic(anyString(), any())).thenReturn(new DataciteHttpResponse(200, "OK"));

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    DataciteTestConnectionIO body = (DataciteTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isTrue();
    assertThat(body.statusCode()).isEqualTo(200);
    assertThat(body.apiBaseUrl()).isEqualTo("https://api.test.datacite.org");
  }

  @Test
  void testConnection_unreachableOnNetworkError() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setApiBaseUrl("https://api.test.datacite.org");
    when(service.current()).thenReturn(cfg);
    when(http.getDiagnostic(anyString(), any())).thenReturn(new DataciteHttpResponse(0, "network: oops"));

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    DataciteTestConnectionIO body = (DataciteTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isFalse();
    assertThat(body.statusCode()).isEqualTo(0);
    assertThat(body.detail()).contains("network");
  }

  @Test
  void testConnection_reportsUnconfiguredApiBaseUrl() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setApiBaseUrl(null);
    when(service.current()).thenReturn(cfg);

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    DataciteTestConnectionIO body = (DataciteTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isFalse();
    assertThat(body.detail()).contains("not configured");
  }

  @Test
  void testConnection_reachableWhenDataciteReturns3xx() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setApiBaseUrl("https://api.datacite.org/");
    when(service.current()).thenReturn(cfg);
    when(http.getDiagnostic(anyString(), any())).thenReturn(new DataciteHttpResponse(301, "Moved"));

    Response r = rest.testConnection();

    DataciteTestConnectionIO body = (DataciteTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isTrue();
    assertThat(body.statusCode()).isEqualTo(301);
  }

  @Test
  void testConnection_unreachableOn5xx() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setApiBaseUrl("https://api.datacite.org");
    when(service.current()).thenReturn(cfg);
    when(http.getDiagnostic(anyString(), any())).thenReturn(new DataciteHttpResponse(503, "down"));

    Response r = rest.testConnection();

    DataciteTestConnectionIO body = (DataciteTestConnectionIO) r.getEntity();
    assertThat(body.reachable()).isFalse();
    assertThat(body.statusCode()).isEqualTo(503);
  }
}
