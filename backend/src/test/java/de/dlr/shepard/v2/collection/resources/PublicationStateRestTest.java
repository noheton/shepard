package de.dlr.shepard.v2.collection.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * #27-ARCHIVED — unit tests for {@link CollectionPublicationStateRest} and
 * {@link ContainerPublicationStateRest}. Stubs {@link NeoConnector} via
 * Mockito static-mock so no live Neo4j is required.
 */
class PublicationStateRestTest {

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

  // ── Collection PATCH ─────────────────────────────────────────────────────

  @Test
  void collection_patch_managerCanArchive() {
    CollectionPublicationStateRest rest = new CollectionPublicationStateRest();
    EntityIdResolver resolver = mock(EntityIdResolver.class);
    PermissionsService perms = mock(PermissionsService.class);
    rest.entityIdResolver = resolver;
    rest.permissionsService = perms;

    when(resolver.resolveLong("col-app-id")).thenReturn(42L);
    when(perms.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Manage), eq("alice"), anyLong()))
      .thenReturn(true);

    Result r1 = statusResult("ARCHIVED");
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r1);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      Response r = rest.patch(
        "col-app-id",
        new PublicationStateIO("ARCHIVED", null),
        securityContextFor("alice", false)
      );
      assertEquals(200, r.getStatus());
      PublicationStateIO body = (PublicationStateIO) r.getEntity();
      assertEquals("ARCHIVED", body.getState());
      assertTrue(body.getArchived());
    }
  }

  @Test
  void collection_patch_instanceAdminCanArchive_evenWithoutManage() {
    CollectionPublicationStateRest rest = new CollectionPublicationStateRest();
    EntityIdResolver resolver = mock(EntityIdResolver.class);
    PermissionsService perms = mock(PermissionsService.class);
    rest.entityIdResolver = resolver;
    rest.permissionsService = perms;

    when(resolver.resolveLong("col-app-id")).thenReturn(42L);
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
        "col-app-id",
        new PublicationStateIO("ARCHIVED", null),
        securityContextFor("admin", true)
      );
      assertEquals(200, r.getStatus());
    }
  }

  @Test
  void collection_patch_writeOnlyUser_rejected_403() {
    CollectionPublicationStateRest rest = new CollectionPublicationStateRest();
    EntityIdResolver resolver = mock(EntityIdResolver.class);
    PermissionsService perms = mock(PermissionsService.class);
    rest.entityIdResolver = resolver;
    rest.permissionsService = perms;

    when(resolver.resolveLong("col-app-id")).thenReturn(42L);
    when(perms.isAccessTypeAllowedForUser(anyLong(), eq(AccessType.Manage), anyString(), anyLong()))
      .thenReturn(false);

    Response r = rest.patch(
      "col-app-id",
      new PublicationStateIO("ARCHIVED", null),
      securityContextFor("bob", false)
    );
    assertEquals(403, r.getStatus());
  }

  @Test
  void collection_patch_invalidState_400() {
    CollectionPublicationStateRest rest = new CollectionPublicationStateRest();
    rest.entityIdResolver = mock(EntityIdResolver.class);
    rest.permissionsService = mock(PermissionsService.class);

    Response r = rest.patch(
      "col-app-id",
      new PublicationStateIO("BOGUS", null),
      securityContextFor("alice", false)
    );
    assertEquals(400, r.getStatus());
  }

  @Test
  void collection_patch_unknownAppId_404() {
    CollectionPublicationStateRest rest = new CollectionPublicationStateRest();
    EntityIdResolver resolver = mock(EntityIdResolver.class);
    rest.entityIdResolver = resolver;
    rest.permissionsService = mock(PermissionsService.class);

    when(resolver.resolveLong("missing"))
      .thenThrow(new jakarta.ws.rs.NotFoundException("not-found"));

    Response r = rest.patch(
      "missing",
      new PublicationStateIO("ARCHIVED", null),
      securityContextFor("alice", false)
    );
    assertEquals(404, r.getStatus());
  }

  @Test
  void collection_patchPathOnV2Shelf() {
    Path p = CollectionPublicationStateRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/collections/{collectionAppId}/publication-state", p.value());
  }

  // ── PublicationStateIO helper ────────────────────────────────────────────

  @Test
  void publicationStateIO_of_setsArchivedFlag() {
    assertTrue(PublicationStateIO.of("ARCHIVED").getArchived());
    assertEquals(false, PublicationStateIO.of("READY").getArchived());
    assertEquals(false, PublicationStateIO.of(null).getArchived());
  }
}
