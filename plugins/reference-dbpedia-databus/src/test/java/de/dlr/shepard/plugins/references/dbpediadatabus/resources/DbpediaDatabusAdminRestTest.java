package de.dlr.shepard.plugins.references.dbpediadatabus.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.references.dbpediadatabus.clients.DatabusHttpClient;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusConfigIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusConfigPatchIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusCredentialPatchIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * REF1c — unit tests for {@link DbpediaDatabusAdminRest}.
 */
class DbpediaDatabusAdminRestTest {

  private DbpediaDatabusConfigService configService;
  private DatabusHttpClient httpClient;
  private DbpediaDatabusAdminRest rest;

  @BeforeEach
  void setUp() {
    configService = mock(DbpediaDatabusConfigService.class);
    httpClient = mock(DatabusHttpClient.class);
    rest = new DbpediaDatabusAdminRest();
    rest.configService = configService;
    rest.httpClient = httpClient;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = DbpediaDatabusAdminRest.class.getAnnotation(RolesAllowed.class);
    assertThat(gate).isNotNull();
    assertThat(gate.value()).hasSize(1);
    assertThat(gate.value()[0]).isEqualTo(Constants.INSTANCE_ADMIN_ROLE);
  }

  @Test
  void pathIsV2AdminReferenceDbpediaDatabus() {
    Path p = DbpediaDatabusAdminRest.class.getAnnotation(Path.class);
    assertThat(p).isNotNull();
    assertThat(p.value()).isEqualTo("/v2/admin/references/dbpedia-databus");
  }

  // ─── GET /config ─────────────────────────────────────────────────────────

  @Test
  void getConfig_returnsMaskedIO() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    cfg.setEnabled(true);
    cfg.setDefaultEndpoint("https://databus.dbpedia.org");
    cfg.setOauthClientSecretSet(true);
    cfg.setOauthClientSecretFingerprint("abcd1234");
    when(configService.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertThat(r.getStatus()).isEqualTo(200);
    DbpediaDatabusConfigIO body = (DbpediaDatabusConfigIO) r.getEntity();
    assertThat(body.enabled()).isTrue();
    assertThat(body.oauthClientSecretSet()).isTrue();
    assertThat(body.oauthClientSecretFingerprint()).isEqualTo("abcd1234");
  }

  // ─── PATCH /config ───────────────────────────────────────────────────────

  @Test
  void patchConfig_validPatch_returns200() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    cfg.setEnabled(true);
    when(configService.patch(any(), anyString())).thenReturn(cfg);

    DbpediaDatabusConfigPatchIO body = new DbpediaDatabusConfigPatchIO();
    body.setEnabled(Boolean.TRUE);

    Response r = rest.patchConfig(body, mockSecurityContext("admin"));

    assertThat(r.getStatus()).isEqualTo(200);
    DbpediaDatabusConfigIO out = (DbpediaDatabusConfigIO) r.getEntity();
    assertThat(out.enabled()).isTrue();
  }

  @Test
  void patchConfig_nullBody_treatedAsEmptyPatch() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    when(configService.patch(any(), anyString())).thenReturn(cfg);

    Response r = rest.patchConfig(null, mockSecurityContext("admin"));

    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void patchConfig_validationFails_returns400() {
    when(configService.patch(any(), anyString()))
      .thenThrow(new IllegalArgumentException("defaultEndpoint must be non-blank"));

    DbpediaDatabusConfigPatchIO body = new DbpediaDatabusConfigPatchIO();
    body.setDefaultEndpoint(null);

    Response r = rest.patchConfig(body, mockSecurityContext("admin"));

    assertThat(r.getStatus()).isEqualTo(400);
    assertThat(r.getMediaType().toString()).isEqualTo("application/problem+json");
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertThat(problem.status()).isEqualTo(400);
    assertThat(problem.detail()).contains("defaultEndpoint");
  }

  // ─── POST /credential ────────────────────────────────────────────────────

  @Test
  void setCredential_blankSecret_returns400() {
    DbpediaDatabusCredentialPatchIO body = new DbpediaDatabusCredentialPatchIO();
    body.setClientSecret("   ");

    Response r = rest.setCredential(body, mockSecurityContext("admin"));

    assertThat(r.getStatus()).isEqualTo(400);
    assertThat(r.getMediaType().toString()).isEqualTo("application/problem+json");
  }

  @Test
  void setCredential_nullBody_returns400() {
    Response r = rest.setCredential(null, mockSecurityContext("admin"));
    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void setCredential_encryptionUnavailable_returns400() {
    DbpediaDatabusCredentialPatchIO body = new DbpediaDatabusCredentialPatchIO();
    body.setClientSecret("mysecret");
    when(configService.setOauthClientSecret(anyString(), anyString()))
      .thenThrow(new IllegalStateException("encryption-key not configured"));

    Response r = rest.setCredential(body, mockSecurityContext("admin"));

    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void setCredential_success_returns200WithMaskedConfig() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    cfg.setOauthClientSecretSet(true);
    cfg.setOauthClientSecretFingerprint("ff001122");
    when(configService.setOauthClientSecret(anyString(), anyString())).thenReturn(cfg);

    DbpediaDatabusCredentialPatchIO body = new DbpediaDatabusCredentialPatchIO();
    body.setClientSecret("mysecret");

    Response r = rest.setCredential(body, mockSecurityContext("admin"));

    assertThat(r.getStatus()).isEqualTo(200);
    DbpediaDatabusConfigIO out = (DbpediaDatabusConfigIO) r.getEntity();
    assertThat(out.oauthClientSecretSet()).isTrue();
    assertThat(out.oauthClientSecretFingerprint()).isEqualTo("ff001122");
  }

  // ─── DELETE /credential ──────────────────────────────────────────────────

  @Test
  void clearCredential_returns200() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    cfg.setOauthClientSecretSet(false);
    when(configService.clearOauthClientSecret(anyString())).thenReturn(cfg);

    Response r = rest.clearCredential(mockSecurityContext("admin"));

    assertThat(r.getStatus()).isEqualTo(200);
    DbpediaDatabusConfigIO out = (DbpediaDatabusConfigIO) r.getEntity();
    assertThat(out.oauthClientSecretSet()).isFalse();
  }

  // ─── POST /test-connection ───────────────────────────────────────────────

  @Test
  void testConnection_returns200WithResult() {
    DbpediaDatabusConfig cfg = new DbpediaDatabusConfig();
    cfg.setDefaultEndpoint("https://databus.dbpedia.org");
    when(configService.current()).thenReturn(cfg);

    DatabusHttpClient.ConnectionTestResult result =
      new DatabusHttpClient.ConnectionTestResult(true, 200, 42L, null);
    when(httpClient.testConnection(anyString())).thenReturn(result);

    Response r = rest.testConnection();

    assertThat(r.getStatus()).isEqualTo(200);
    DatabusHttpClient.ConnectionTestResult body = (DatabusHttpClient.ConnectionTestResult) r.getEntity();
    assertThat(body.reachable()).isTrue();
    assertThat(body.statusCode()).isEqualTo(200);
    assertThat(body.latencyMs()).isEqualTo(42L);
  }

  // ─── helper ──────────────────────────────────────────────────────────────

  private jakarta.ws.rs.core.SecurityContext mockSecurityContext(String name) {
    jakarta.ws.rs.core.SecurityContext sc = mock(jakarta.ws.rs.core.SecurityContext.class);
    java.security.Principal p = mock(java.security.Principal.class);
    when(p.getName()).thenReturn(name);
    when(sc.getUserPrincipal()).thenReturn(p);
    return sc;
  }
}
