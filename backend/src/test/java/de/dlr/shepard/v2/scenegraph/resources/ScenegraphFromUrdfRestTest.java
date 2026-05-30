package de.dlr.shepard.v2.scenegraph.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneFromUrdfRequestIO;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.ProvenanceContext;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService.ExistingSceneException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SCENEGRAPH-CREATE-FROM-URDF-1 — unit tests for the REST layer.
 *
 * <p>The service-layer orchestration is exercised in
 * {@code ScenegraphFromUrdfServiceTest}; this suite focuses on the
 * exception-to-status-code mapping and the 409 response shape (which
 * embeds the existing scene's appId).
 */
class ScenegraphFromUrdfRestTest {

  private static final String FILE_REF_APP_ID = "fr-app";
  private static final String CALLER = "alice";

  private ScenegraphFromUrdfRest resource;
  private ScenegraphFromUrdfService service;
  private SecurityContext sc;
  private Principal principal;

  @BeforeEach
  void setUp() {
    resource = new ScenegraphFromUrdfRest();
    service = mock(ScenegraphFromUrdfService.class);
    resource.service = service;
    sc = mock(SecurityContext.class);
    principal = mock(Principal.class);
    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void unauthenticated_returns401() {
    SecurityContext anon = mock(SecurityContext.class);
    when(anon.getUserPrincipal()).thenReturn(null);
    Response r = resource.createFromUrdf(FILE_REF_APP_ID, null, null, anon);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void happy_returns201AndScene() {
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId("scene-1");
    scene.setName("KR210");
    when(service.createFromUrdf(eq(FILE_REF_APP_ID), any(), any(),
        any(ProvenanceContext.class), eq(CALLER)))
      .thenReturn(scene);

    Response r = resource.createFromUrdf(FILE_REF_APP_ID,
      new CreateSceneFromUrdfRequestIO(), null, sc);
    assertThat(r.getStatus()).isEqualTo(201);
    SceneGraphIO body = (SceneGraphIO) r.getEntity();
    assertThat(body.getAppId()).isEqualTo("scene-1");
    assertThat(body.getName()).isEqualTo("KR210");
  }

  @Test
  void existingScene_returns409WithExistingAppId() {
    when(service.createFromUrdf(eq(FILE_REF_APP_ID), any(), any(),
        any(ProvenanceContext.class), eq(CALLER)))
      .thenThrow(new ExistingSceneException("scene-existing"));

    Response r = resource.createFromUrdf(FILE_REF_APP_ID, null, null, sc);
    assertThat(r.getStatus()).isEqualTo(409);
    String body = (String) r.getEntity();
    assertThat(body).contains("\"existingSceneAppId\":\"scene-existing\"");
  }

  @Test
  void forbidden_returns403() {
    when(service.createFromUrdf(eq(FILE_REF_APP_ID), any(), any(),
        any(ProvenanceContext.class), eq(CALLER)))
      .thenThrow(new ForbiddenException("denied"));

    Response r = resource.createFromUrdf(FILE_REF_APP_ID, null, null, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  @Test
  void notFound_returns404() {
    when(service.createFromUrdf(eq(FILE_REF_APP_ID), any(), any(),
        any(ProvenanceContext.class), eq(CALLER)))
      .thenThrow(new NotFoundException("missing"));

    Response r = resource.createFromUrdf(FILE_REF_APP_ID, null, null, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void badRequest_returns400() {
    when(service.createFromUrdf(eq(FILE_REF_APP_ID), any(), any(),
        any(ProvenanceContext.class), eq(CALLER)))
      .thenThrow(new BadRequestException("not a singleton"));

    Response r = resource.createFromUrdf(FILE_REF_APP_ID, null, null, sc);
    assertThat(r.getStatus()).isEqualTo(400);
    String body = (String) r.getEntity();
    assertThat(body).contains("not a singleton");
  }

  @Test
  void aiAgentHeader_propagatesToProvenanceContext() {
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId("s");
    when(service.createFromUrdf(anyString(), any(), any(), any(ProvenanceContext.class), anyString()))
      .thenAnswer(inv -> {
        ProvenanceContext prov = inv.getArgument(3);
        // Carrier check: header → AI mode.
        assertThat(prov.sourceMode()).isEqualTo("ai");
        assertThat(prov.agentId()).isEqualTo("claude-test");
        return scene;
      });

    Response r = resource.createFromUrdf(FILE_REF_APP_ID, null, "claude-test", sc);
    assertThat(r.getStatus()).isEqualTo(201);
  }
}
