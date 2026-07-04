package de.dlr.shepard.v2.container.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.collection.io.PublicationStateIO;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * #27-ARCHIVED — unit tests for {@link ContainerPublicationStateRest}.
 * Lives in the same package as the resource so the package-private
 * injected fields are reachable.
 */
class ContainerPublicationStateRestTest {

  private static SecurityContext securityContextFor(String username, boolean isInstanceAdmin) {
    SecurityContext sc = mock(SecurityContext.class);
    Principal p = mock(Principal.class);
    when(p.getName()).thenReturn(username);
    when(sc.getUserPrincipal()).thenReturn(p);
    when(sc.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(isInstanceAdmin);
    return sc;
  }

  private static Result statusResult(String status) {
    Map<String, Object> row = new HashMap<>();
    row.put("s", status);
    Result r = mock(Result.class);
    when(r.queryResults()).thenReturn(List.of(row));
    return r;
  }

  @Test
  void container_patch_managerCanArchive() {
    ContainerPublicationStateRest rest = new ContainerPublicationStateRest();
    EntityIdResolver resolver = mock(EntityIdResolver.class);
    PermissionsService perms = mock(PermissionsService.class);
    rest.entityIdResolver = resolver;
    rest.permissionsService = perms;

    when(resolver.resolveLong("ctr-app-id")).thenReturn(7L);
    when(perms.isAccessTypeAllowedForUser(eq(7L), eq(AccessType.Manage), eq("alice"), anyLong()))
      .thenReturn(true);

    Result r1 = statusResult("ARCHIVED");
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r1);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      Response r = rest.patch(
        "ctr-app-id",
        new PublicationStateIO("ARCHIVED", null),
        securityContextFor("alice", false)
      );
      assertEquals(200, r.getStatus());
    }
  }

  @Test
  void container_patch_writeOnlyUser_403() {
    ContainerPublicationStateRest rest = new ContainerPublicationStateRest();
    EntityIdResolver resolver = mock(EntityIdResolver.class);
    PermissionsService perms = mock(PermissionsService.class);
    rest.entityIdResolver = resolver;
    rest.permissionsService = perms;

    when(resolver.resolveLong("ctr-app-id")).thenReturn(7L);
    when(perms.isAccessTypeAllowedForUser(anyLong(), eq(AccessType.Manage), anyString(), anyLong()))
      .thenReturn(false);

    Response r = rest.patch(
      "ctr-app-id",
      new PublicationStateIO("ARCHIVED", null),
      securityContextFor("bob", false)
    );
    assertEquals(403, r.getStatus());
  }

  @Test
  void container_patch_instanceAdmin_canArchive() {
    ContainerPublicationStateRest rest = new ContainerPublicationStateRest();
    EntityIdResolver resolver = mock(EntityIdResolver.class);
    PermissionsService perms = mock(PermissionsService.class);
    rest.entityIdResolver = resolver;
    rest.permissionsService = perms;

    when(resolver.resolveLong("ctr-app-id")).thenReturn(7L);
    when(perms.isAccessTypeAllowedForUser(anyLong(), eq(AccessType.Manage), anyString(), anyLong()))
      .thenReturn(false);

    Result r1 = statusResult("ARCHIVED");
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r1);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      Response r = rest.patch(
        "ctr-app-id",
        new PublicationStateIO("ARCHIVED", null),
        securityContextFor("admin", true)
      );
      assertEquals(200, r.getStatus());
    }
  }

  @Test
  void container_patch_invalidState_400() {
    ContainerPublicationStateRest rest = new ContainerPublicationStateRest();
    rest.entityIdResolver = mock(EntityIdResolver.class);
    rest.permissionsService = mock(PermissionsService.class);

    Response r = rest.patch(
      "ctr-app-id",
      new PublicationStateIO("BOGUS", null),
      securityContextFor("alice", false)
    );
    assertEquals(400, r.getStatus());
  }

  @Test
  void container_pathOnV2Shelf() {
    Path p = ContainerPublicationStateRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/containers/{containerAppId}/publication-state", p.value());
  }
}
