package de.dlr.shepard.plugins.minter.datacite.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient.DataciteHttpResponse;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteCredentialIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteCredentialSetIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteMinterConfigIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteMinterConfigPatchIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteTestConnectionIO;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService.DatacitePatch;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService.ReadOnlyFieldException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KIP1d — unit tests for the admin REST surface. No Quarkus boot —
 * the {@link DataciteMinterConfigService} and {@link DataciteHttpClient}
 * are mocked.
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

  // ─── GET /config ────────────────────────────────────────────────────────

  @Test
  void getConfig_returnsMaskedIO() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setEnabled(true);
    cfg.setApiBaseUrl("https://api.datacite.org");
    cfg.setHandlePrefix("10.5072");
    cfg.setRepositoryId("DLR");
    cfg.setPasswordCipher("gcm1:secret");
    cfg.setPasswordHash("0123456789abcdef".repeat(4));
    cfg.setPublisher("DLR e.V.");
    cfg.setLandingPageBase("https://shepard.dlr.de/v2");
    cfg.setDefaultState("draft");
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertThat(r.getStatus()).isEqualTo(200);
    DataciteMinterConfigIO body = (DataciteMinterConfigIO) r.getEntity();
    assertThat(body.enabled()).isTrue();
    assertThat(body.handlePrefix()).isEqualTo("10.5072");
    assertThat(body.publisher()).isEqualTo("DLR e.V.");
    assertThat(body.passwordSet()).isTrue();
    assertThat(body.passwordFingerprint()).isEqualTo("01234567");

    // CRITICAL — the IO must never serialise the cipher or the full hash.
    String rendered = body.toString();
    assertThat(rendered).doesNotContain("gcm1:secret");
    assertThat(rendered).doesNotContain("0123456789abcdef0123456789abcdef");
  }

  @Test
  void getConfig_returnsPasswordSetFalseWhenNoCredential() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setEnabled(false);
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    DataciteMinterConfigIO body = (DataciteMinterConfigIO) r.getEntity();
    assertThat(body.passwordSet()).isFalse();
    assertThat(body.passwordFingerprint()).isNull();
  }

  // ─── PATCH /config ──────────────────────────────────────────────────────

  @Test
  void patchConfig_appliesAndReturnsMaskedIO() {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    saved.setEnabled(true);
    saved.setApiBaseUrl("https://api.test.datacite.org");
    when(service.patch(any(DatacitePatch.class), anyString())).thenReturn(saved);

    DataciteMinterConfigPatchIO patch = new DataciteMinterConfigPatchIO();
    patch.setEnabled(true);

    Response r = rest.patchConfig(patch, security);

    assertThat(r.getStatus()).isEqualTo(200);
    DataciteMinterConfigIO body = (DataciteMinterConfigIO) r.getEntity();
    assertThat(body.enabled()).isTrue();
  }

  @Test
  void patchConfig_acceptsNullBodyAsNoOp() {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    when(service.patch(any(DatacitePatch.class), anyString())).thenReturn(saved);

    Response r = rest.patchConfig(null, security);

    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void patchConfig_rejectsPasswordHashWithProblemJson() {
    when(service.patch(any(DatacitePatch.class), anyString()))
      .thenThrow(new ReadOnlyFieldException("passwordHash"));

    DataciteMinterConfigPatchIO patch = new DataciteMinterConfigPatchIO();
    patch.setPasswordHash("anything");

    Response r = rest.patchConfig(patch, security);

    assertThat(r.getStatus()).isEqualTo(400);
    assertThat(r.getMediaType().toString()).contains("application/problem+json");
    ProblemJson body = (ProblemJson) r.getEntity();
    assertThat(body.title()).contains("read-only");
    assertThat(body.detail()).contains("passwordHash");
  }

  @Test
  void patchConfig_rejectsInvalidStateWithProblemJson() {
    when(service.patch(any(DatacitePatch.class), anyString()))
      .thenThrow(new IllegalArgumentException("defaultState must be one of draft/registered/findable"));

    DataciteMinterConfigPatchIO patch = new DataciteMinterConfigPatchIO();
    patch.setDefaultState("nonsense");

    Response r = rest.patchConfig(patch, security);

    assertThat(r.getStatus()).isEqualTo(400);
    ProblemJson body = (ProblemJson) r.getEntity();
    assertThat(body.title()).contains("Invalid");
  }

  // ─── POST /credential ───────────────────────────────────────────────────

  @Test
  void setCredential_returnsFingerprint() {
    DataciteMinterConfig saved = new DataciteMinterConfig();
    saved.setPasswordHash("deadbeef".repeat(8));
    when(service.setCredential(anyString(), anyString())).thenReturn(saved);

    DataciteCredentialIO body = new DataciteCredentialIO("the-password");

    Response r = rest.setCredential(body, security);

    assertThat(r.getStatus()).isEqualTo(200);
    DataciteCredentialSetIO out = (DataciteCredentialSetIO) r.getEntity();
    assertThat(out.passwordSet()).isTrue();
    assertThat(out.fingerprint()).isEqualTo("deadbeef");
  }

  @Test
  void setCredential_rejectsEmptyBodyWithProblemJson() {
    DataciteCredentialIO body = new DataciteCredentialIO("");

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
    DataciteMinterConfig saved = new DataciteMinterConfig();
    when(service.clearCredential(anyString())).thenReturn(saved);

    Response r = rest.clearCredential(security);

    assertThat(r.getStatus()).isEqualTo(200);
    DataciteMinterConfigIO body = (DataciteMinterConfigIO) r.getEntity();
    assertThat(body.passwordSet()).isFalse();
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
