package de.dlr.shepard.plugins.references.dbpediadatabus.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.plugins.references.dbpediadatabus.clients.DatabusHttpClient;
import de.dlr.shepard.plugins.references.dbpediadatabus.daos.DbpediaDatabusReferenceDAO;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusReference;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusCreateReferenceIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusReferenceIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusConfigService;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusCredentialService;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusReferenceService;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * REF1c — unit tests for {@link DbpediaDatabusReferenceRest}.
 *
 * <p>Uses Mockito to stub all injected collaborators; no Quarkus boot.
 */
class DbpediaDatabusReferenceRestTest {

  private DbpediaDatabusReferenceDAO referenceDAO;
  private DbpediaDatabusReferenceService referenceService;
  private DbpediaDatabusReferenceRest rest;

  @BeforeEach
  void setUp() {
    referenceDAO = mock(DbpediaDatabusReferenceDAO.class);
    referenceService = mock(DbpediaDatabusReferenceService.class);
    rest = new DbpediaDatabusReferenceRest();
    rest.referenceDAO = referenceDAO;
    rest.referenceService = referenceService;
    rest.configService = mock(DbpediaDatabusConfigService.class);
    rest.credentialService = mock(DbpediaDatabusCredentialService.class);
    rest.httpClient = mock(DatabusHttpClient.class);
    rest.dataObjectDAO = mock(de.dlr.shepard.context.collection.daos.DataObjectDAO.class);
    rest.permissionsService = mock(de.dlr.shepard.auth.permission.services.PermissionsService.class);
    rest.entityIdResolver = mock(de.dlr.shepard.common.identifier.EntityIdResolver.class);
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void pathIsV2DataObjectsDbpediaDatabusReferences() {
    Path p = DbpediaDatabusReferenceRest.class.getAnnotation(Path.class);
    assertThat(p).isNotNull();
    assertThat(p.value()).isEqualTo("/v2/data-objects/{dataObjectAppId}/dbpedia-databus-references");
  }

  // ─── POST create — validation ────────────────────────────────────────────

  @Test
  void create_nullBody_returns400() {
    Response r = rest.create("do01", null, mockSecurityContext("user1"));
    assertThat(r.getStatus()).isEqualTo(400);
    assertThat(r.getMediaType().toString()).isEqualTo("application/problem+json");
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertThat(problem.status()).isEqualTo(400);
    assertThat(problem.detail()).contains("artifactUri");
  }

  @Test
  void create_blankUri_returns400() {
    DbpediaDatabusCreateReferenceIO body = new DbpediaDatabusCreateReferenceIO();
    body.setArtifactUri("  ");
    Response r = rest.create("do01", body, mockSecurityContext("user1"));
    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void create_hostNotAllowed_returns400() {
    DbpediaDatabusCreateReferenceIO body = new DbpediaDatabusCreateReferenceIO();
    body.setArtifactUri("https://evil.example.org/art");
    when(referenceService.validateUri("https://evil.example.org/art"))
      .thenReturn(DbpediaDatabusReferenceService.ValidationResult.invalid("host 'evil.example.org' is not in allowedHosts"));

    Response r = rest.create("do01", body, mockSecurityContext("user1"));

    assertThat(r.getStatus()).isEqualTo(400);
  }

  // ─── paths/methods existence ─────────────────────────────────────────────

  @Test
  void restClass_hasCrudMethods() throws Exception {
    // Smoke: check all the method names we expect exist
    assertThat(DbpediaDatabusReferenceRest.class.getDeclaredMethod("list", String.class, jakarta.ws.rs.core.SecurityContext.class)).isNotNull();
    assertThat(DbpediaDatabusReferenceRest.class.getDeclaredMethod("create", String.class, DbpediaDatabusCreateReferenceIO.class, jakarta.ws.rs.core.SecurityContext.class)).isNotNull();
    assertThat(DbpediaDatabusReferenceRest.class.getDeclaredMethod("read", String.class, String.class, jakarta.ws.rs.core.SecurityContext.class)).isNotNull();
    assertThat(DbpediaDatabusReferenceRest.class.getDeclaredMethod("delete", String.class, String.class, jakarta.ws.rs.core.SecurityContext.class)).isNotNull();
    assertThat(DbpediaDatabusReferenceRest.class.getDeclaredMethod("preview", String.class, String.class, jakarta.ws.rs.core.SecurityContext.class)).isNotNull();
  }

  // ─── helper ──────────────────────────────────────────────────────────────

  private jakarta.ws.rs.core.SecurityContext mockSecurityContext(String name) {
    jakarta.ws.rs.core.SecurityContext sc = mock(jakarta.ws.rs.core.SecurityContext.class);
    Principal p = mock(Principal.class);
    when(p.getName()).thenReturn(name);
    when(sc.getUserPrincipal()).thenReturn(p);
    return sc;
  }
}
