package de.dlr.shepard.v2.versioning.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.v2.versioning.io.EntityVersionCreateIO;
import de.dlr.shepard.v2.versioning.io.EntityVersionIO;
import de.dlr.shepard.v2.versioning.io.EntityVersionListIO;
import de.dlr.shepard.versioning.EntityVersion;
import de.dlr.shepard.versioning.EntityVersionException;
import de.dlr.shepard.versioning.EntityVersionService;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ENT1a REST tests — Mockito on the service / permissions /
 * id-resolver dependencies, mirroring the {@code PublishRestTest} shape
 * from KIP1a. Exercises auth gates, RFC 7807 envelopes, kind-segment
 * dispatch, and happy paths for every endpoint.
 */
class EntityVersionRestTest {

  private EntityVersionRest rest;
  private PublishableKindRegistry kindRegistry;
  private EntityVersionService versionService;
  private PermissionsService permissionsService;
  private EntityIdResolver entityIdResolver;
  private SecurityContext securityContext;

  @BeforeEach
  void setUp() {
    rest = new EntityVersionRest();
    kindRegistry = new PublishableKindRegistry();
    versionService = mock(EntityVersionService.class);
    permissionsService = mock(PermissionsService.class);
    entityIdResolver = mock(EntityIdResolver.class);
    securityContext = mock(SecurityContext.class);

    rest.kindRegistry = kindRegistry;
    rest.versionService = versionService;
    rest.permissionsService = permissionsService;
    rest.entityIdResolver = entityIdResolver;

    Principal alice = () -> "alice";
    when(securityContext.getUserPrincipal()).thenReturn(alice);
  }

  private EntityVersion version(String label, int ord, String parentKind) {
    EntityVersion v = new EntityVersion();
    v.setAppId("v-app-" + label);
    v.setVersionLabel(label);
    v.setVersionOrdinal(ord);
    v.setCreatedAt(1_747_000_000_000L);
    v.setCreatedBy("alice");
    v.setParentEntityKind(parentKind);
    v.setParentEntityAppId("01HF-A");
    return v;
  }

  // ─── POST: create ────────────────────────────────────────────────────

  @Test
  void createHappyPathReturns201() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(42L, AccessType.Write, "alice")).thenReturn(true);
    EntityVersion v = version("v3", 3, "collection");
    when(versionService.createVersion(eq("collection"), eq("01HF-A"), eq(null), eq(null), eq("alice"))).thenReturn(v);

    Response r = rest.create("collections", "01HF-A", new EntityVersionCreateIO(null, null), securityContext);
    assertEquals(201, r.getStatus());
    EntityVersionIO io = (EntityVersionIO) r.getEntity();
    assertEquals("v3", io.versionLabel());
    assertEquals(3, io.versionOrdinal());
    assertEquals("collection", io.parentEntityKind());
  }

  @Test
  void createWithCustomLabelAndNote() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(42L, AccessType.Write, "alice")).thenReturn(true);
    EntityVersion v = version("1.0.0-rc.1", 5, "data-object");
    v.setNote("first RC");
    when(versionService.createVersion(eq("data-object"), eq("01HF-A"), eq("1.0.0-rc.1"), eq("first RC"), eq("alice")))
      .thenReturn(v);

    Response r = rest.create(
      "data-objects",
      "01HF-A",
      new EntityVersionCreateIO("1.0.0-rc.1", "first RC"),
      securityContext
    );
    assertEquals(201, r.getStatus());
    EntityVersionIO io = (EntityVersionIO) r.getEntity();
    assertEquals("1.0.0-rc.1", io.versionLabel());
    assertEquals("first RC", io.note());
  }

  @Test
  void createWithNullBodyIsLegal() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(42L, AccessType.Write, "alice")).thenReturn(true);
    EntityVersion v = version("v1", 1, "collection");
    when(versionService.createVersion(anyString(), anyString(), eq(null), eq(null), anyString())).thenReturn(v);

    Response r = rest.create("collections", "01HF-A", null, securityContext);
    assertEquals(201, r.getStatus());
  }

  @Test
  void createMissingAuthReturns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = rest.create("collections", "01HF-A", null, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void createPermissionDeniedReturns403() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(anyLong(), any(), anyString())).thenReturn(false);
    Response r = rest.create("collections", "01HF-A", null, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void createMissingParentReturns404() {
    when(entityIdResolver.resolveLong("01HF-MISSING")).thenThrow(new NotFoundException("nope"));
    Response r = rest.create("collections", "01HF-MISSING", null, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void createUnsupportedKindReturnsProblemJson() {
    Response r = rest.create("bundles", "01HF-A", null, securityContext);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("versions.kind.unsupported"));
  }

  @Test
  void createDuplicateLabelReturns409ProblemJson() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(42L, AccessType.Write, "alice")).thenReturn(true);
    when(versionService.createVersion(anyString(), anyString(), anyString(), any(), anyString())).thenThrow(
      new EntityVersionException(EntityVersionException.Reason.LABEL_DUPLICATE, "dup")
    );
    Response r = rest.create("collections", "01HF-A", new EntityVersionCreateIO("v1", null), securityContext);
    assertEquals(409, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("versions.label.duplicate"));
  }

  @Test
  void createInvalidLabelReturns400ProblemJson() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(42L, AccessType.Write, "alice")).thenReturn(true);
    when(versionService.createVersion(anyString(), anyString(), anyString(), any(), anyString())).thenThrow(
      new EntityVersionException(EntityVersionException.Reason.LABEL_INVALID, "bad shape")
    );
    Response r = rest.create("collections", "01HF-A", new EntityVersionCreateIO("has space", null), securityContext);
    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("versions.label.invalid"));
  }

  // ─── GET: list ──────────────────────────────────────────────────────

  @Test
  void listReturnsFilteredVersions() {
    EntityVersion v1 = version("v1", 1, "collection");
    EntityVersion v2 = version("v2", 2, "collection");
    when(versionService.listVersions(eq("collection"), eq("01HF-A"), eq("alice"))).thenReturn(List.of(v2, v1));
    Response r = rest.list("collections", "01HF-A", securityContext);
    assertEquals(200, r.getStatus());
    EntityVersionListIO io = (EntityVersionListIO) r.getEntity();
    assertEquals(2, io.versions().size());
    assertEquals("v2", io.versions().get(0).versionLabel());
  }

  @Test
  void listMissingAuthReturns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = rest.list("collections", "01HF-A", securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void listUnsupportedKindReturnsProblemJson() {
    Response r = rest.list("widgets", "01HF-A", securityContext);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
  }

  // ─── GET: single ────────────────────────────────────────────────────

  @Test
  void getOneHappyPath() {
    EntityVersion v = version("v1", 1, "collection");
    when(versionService.getVersion(eq("collection"), eq("01HF-A"), eq("v1"), eq("alice"))).thenReturn(v);
    Response r = rest.getOne("collections", "01HF-A", "v1", securityContext);
    assertEquals(200, r.getStatus());
    EntityVersionIO io = (EntityVersionIO) r.getEntity();
    assertEquals("v1", io.versionLabel());
  }

  @Test
  void getOneNotFoundReturnsProblemJson() {
    when(versionService.getVersion(anyString(), anyString(), anyString(), anyString())).thenThrow(
      new EntityVersionException(EntityVersionException.Reason.NOT_FOUND, "no such version")
    );
    Response r = rest.getOne("collections", "01HF-A", "v99", securityContext);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("versions.not-found"));
  }

  @Test
  void getOneForbiddenReturns403() {
    when(versionService.getVersion(anyString(), anyString(), anyString(), anyString())).thenThrow(
      new EntityVersionException(EntityVersionException.Reason.FORBIDDEN, "deny")
    );
    Response r = rest.getOne("collections", "01HF-A", "v1", securityContext);
    assertEquals(403, r.getStatus());
  }

  // ─── PATCH: permissions ─────────────────────────────────────────────

  @Test
  void patchPermissionsHappyPath() {
    Permissions updated = new Permissions();
    User owner = new User();
    owner.setUsername("alice");
    updated.setOwner(owner);
    updated.setPermissionType(PermissionType.Private);
    updated.setReader(new java.util.ArrayList<>());
    updated.setWriter(new java.util.ArrayList<>());
    updated.setManager(new java.util.ArrayList<>());
    updated.setReaderGroups(new java.util.ArrayList<>());
    updated.setWriterGroups(new java.util.ArrayList<>());
    de.dlr.shepard.common.neo4j.entities.BasicEntity ent = new de.dlr.shepard.context.collection.entities.Collection() {
      @Override
      public Long getId() {
        return 7L;
      }

      @Override
      public long getNumericId() {
        return 7L;
      }
    };
    updated.setEntities(List.of(ent));
    when(versionService.patchVersionPermissions(anyString(), anyString(), anyString(), any(), anyString())).thenReturn(
      updated
    );

    PermissionsIO body = new PermissionsIO();
    body.setReader(new String[0]);
    body.setWriter(new String[0]);
    body.setManager(new String[0]);
    body.setReaderGroupIds(new long[0]);
    body.setWriterGroupIds(new long[0]);

    Response r = rest.patchPermissions("collections", "01HF-A", "v1", body, securityContext);
    assertEquals(200, r.getStatus());
    assertNotNull(r.getEntity());
  }

  @Test
  void patchPermissionsForbiddenReturns403() {
    when(versionService.patchVersionPermissions(anyString(), anyString(), anyString(), any(), anyString())).thenThrow(
      new EntityVersionException(EntityVersionException.Reason.FORBIDDEN, "no manage")
    );
    PermissionsIO body = new PermissionsIO();
    body.setReader(new String[0]);
    body.setWriter(new String[0]);
    body.setManager(new String[0]);
    body.setReaderGroupIds(new long[0]);
    body.setWriterGroupIds(new long[0]);
    Response r = rest.patchPermissions("collections", "01HF-A", "v1", body, securityContext);
    assertEquals(403, r.getStatus());
  }

  // ─── DELETE ──────────────────────────────────────────────────────────

  @Test
  void deleteHappyPathReturns204() {
    Response r = rest.delete("collections", "01HF-A", "v2", securityContext);
    assertEquals(204, r.getStatus());
  }

  @Test
  void deleteRefusesLastVersionReturns409ProblemJson() {
    org.mockito.Mockito.doThrow(
      new EntityVersionException(EntityVersionException.Reason.CANNOT_DELETE_ONLY, "only one")
    ).when(versionService)
      .deleteVersion(anyString(), anyString(), anyString(), anyString());
    Response r = rest.delete("collections", "01HF-A", "v1", securityContext);
    assertEquals(409, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("versions.cannot-delete-only"));
  }

  @Test
  void deleteNotFoundReturns404ProblemJson() {
    org.mockito.Mockito.doThrow(new EntityVersionException(EntityVersionException.Reason.NOT_FOUND, "x")).when(versionService)
      .deleteVersion(anyString(), anyString(), anyString(), anyString());
    Response r = rest.delete("collections", "01HF-A", "v99", securityContext);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
  }

  @Test
  void deleteForbiddenReturns403() {
    org.mockito.Mockito.doThrow(new EntityVersionException(EntityVersionException.Reason.FORBIDDEN, "no manage"))
      .when(versionService)
      .deleteVersion(anyString(), anyString(), anyString(), anyString());
    Response r = rest.delete("collections", "01HF-A", "v1", securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void deleteMissingAuthReturns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = rest.delete("collections", "01HF-A", "v1", securityContext);
    assertEquals(401, r.getStatus());
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  @Test
  void singularKindOfMapsBothPublishableKinds() {
    assertEquals("data-object", EntityVersionRest.singularKindOf(de.dlr.shepard.publish.PublishableKind.DATA_OBJECTS));
    assertEquals("collection", EntityVersionRest.singularKindOf(de.dlr.shepard.publish.PublishableKind.COLLECTIONS));
  }
}
