package de.dlr.shepard.v2.git.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.git.daos.GitReferenceDAO;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.io.GitReferenceIO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class GitReferenceRestTest {

  static final String DO_APP_ID = "do-appid-1";
  static final long DO_OGM_ID = 42L;
  static final String CALLER = "alice";
  static final String GR_APP_ID = "gr-appid-1";

  @Mock
  GitReferenceDAO gitReferenceDAO;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  GitReferenceRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new GitReferenceRest();
    resource.gitReferenceDAO = gitReferenceDAO;
    resource.dataObjectDAO = dataObjectDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), any(AccessType.class), eq(CALLER))).thenReturn(true);
  }

  // ── list ───────────────────────────────────────────────────────────

  @Test
  void list_returns200WithRows() {
    var gr = new GitReference("https://gitlab.dlr.de/g/r", "main", "src");
    when(gitReferenceDAO.findByDataObjectAppId(DO_APP_ID)).thenReturn(List.of(gr));
    var r = resource.list(DO_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<GitReferenceIO> rows = (List<GitReferenceIO>) r.getEntity();
    assertEquals(1, rows.size());
    assertEquals("https://gitlab.dlr.de/g/r", rows.get(0).getRepoUrl());
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.list(DO_APP_ID, securityContext).getStatus());
  }

  @Test
  void list_returns404WhenDataObjectMissing() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException("missing"));
    assertEquals(404, resource.list(DO_APP_ID, securityContext).getStatus());
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER)).thenReturn(false);
    assertEquals(403, resource.list(DO_APP_ID, securityContext).getStatus());
  }

  // ── create ─────────────────────────────────────────────────────────

  @Test
  void create_returns201AndPersistsTheRow() {
    var parent = new DataObject();
    when(dataObjectDAO.findByNeo4jId(DO_OGM_ID)).thenReturn(parent);
    var saved = new GitReference("https://gitlab.dlr.de/g/r", "main", "src");
    saved.setAppId(GR_APP_ID);
    when(gitReferenceDAO.createOrUpdate(any(GitReference.class))).thenReturn(saved);

    var body = new GitReferenceIO();
    body.setRepoUrl("https://gitlab.dlr.de/g/r");
    body.setRef("main");
    body.setPath("src");
    var r = resource.create(DO_APP_ID, body, securityContext);

    assertEquals(201, r.getStatus());
    ArgumentCaptor<GitReference> captor = ArgumentCaptor.forClass(GitReference.class);
    verify(gitReferenceDAO).createOrUpdate(captor.capture());
    var passed = captor.getValue();
    assertEquals("https://gitlab.dlr.de/g/r", passed.getRepoUrl());
    assertEquals("main", passed.getRef());
    assertEquals("src", passed.getPath());
    assertEquals(parent, passed.getDataObject());
  }

  @Test
  void create_returns400WhenRepoUrlMissing() {
    var body = new GitReferenceIO();
    body.setRef("main");
    var r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(400, r.getStatus());
    verify(gitReferenceDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns400WhenRepoUrlBlank() {
    var body = new GitReferenceIO();
    body.setRepoUrl("   ");
    assertEquals(400, resource.create(DO_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void create_returns400WhenBodyNull() {
    assertEquals(400, resource.create(DO_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void create_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER)).thenReturn(false);
    var body = new GitReferenceIO();
    body.setRepoUrl("https://gitlab.dlr.de/g/r");
    assertEquals(403, resource.create(DO_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void create_returns404WhenDataObjectVanishedAfterResolve() {
    // Defensive path: resolver answered with an OGM id but the DAO load
    // came back null (race / stale cache). Don't surface a 500.
    when(dataObjectDAO.findByNeo4jId(DO_OGM_ID)).thenReturn(null);
    var body = new GitReferenceIO();
    body.setRepoUrl("https://gitlab.dlr.de/g/r");
    assertEquals(404, resource.create(DO_APP_ID, body, securityContext).getStatus());
  }

  // ── read single ────────────────────────────────────────────────────

  @Test
  void read_returns200WhenChildOfMatchingDataObject() {
    var parent = new DataObject();
    parent.setAppId(DO_APP_ID);
    var gr = new GitReference("https://gitlab.dlr.de/g/r", null, null);
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(parent);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);

    var r = resource.read(DO_APP_ID, GR_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals(GR_APP_ID, ((GitReferenceIO) r.getEntity()).getAppId());
  }

  @Test
  void read_returns404WhenGitReferenceMissing() {
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(null);
    assertEquals(404, resource.read(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
  }

  @Test
  void read_returns404WhenGitReferenceBelongsToDifferentDataObject() {
    // Cross-DataObject leak guard: GET /data-objects/{A}/git-references/{X}
    // must not return X if X actually hangs off a different DataObject.
    var other = new DataObject();
    other.setAppId("different-do-appid");
    var gr = new GitReference("https://gitlab.dlr.de/g/r", null, null);
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(other);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    assertEquals(404, resource.read(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
  }

  // ── delete ─────────────────────────────────────────────────────────

  @Test
  void delete_returns204OnSuccess() {
    var parent = new DataObject();
    parent.setAppId(DO_APP_ID);
    var gr = new GitReference(7L); // testing-only constructor sets the OGM id
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(parent);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);

    var r = resource.delete(DO_APP_ID, GR_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(gitReferenceDAO).deleteByNeo4jId(7L);
  }

  @Test
  void delete_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER)).thenReturn(false);
    assertEquals(403, resource.delete(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
    verify(gitReferenceDAO, never()).deleteByNeo4jId(anyLong());
  }

  @Test
  void delete_returns404WhenGitReferenceMissing() {
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(null);
    assertEquals(404, resource.delete(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
    verify(gitReferenceDAO, never()).deleteByNeo4jId(anyLong());
  }

  @Test
  void delete_returns404WhenGitReferenceBelongsToDifferentDataObject() {
    var other = new DataObject();
    other.setAppId("different-do-appid");
    var gr = new GitReference(7L);
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(other);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    assertEquals(404, resource.delete(DO_APP_ID, GR_APP_ID, securityContext).getStatus());
    verify(gitReferenceDAO, never()).deleteByNeo4jId(anyLong());
  }

  // ── patch ──────────────────────────────────────────────────────────

  private static JsonNode json(String json) {
    try {
      return new ObjectMapper().readTree(json);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private GitReference existingGr() {
    var parent = new DataObject();
    parent.setAppId(DO_APP_ID);
    var gr = new GitReference("https://gitlab.dlr.de/g/r", "main", "src");
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(parent);
    return gr;
  }

  @Test
  void patch_returns200WhenRepoUrlUpdated() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var updated = new GitReference("https://gitlab.dlr.de/g/other", "main", "src");
    updated.setAppId(GR_APP_ID);
    updated.setDataObject(gr.getDataObject());
    when(gitReferenceDAO.createOrUpdate(any(GitReference.class))).thenReturn(updated);

    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{\"repoUrl\":\"https://gitlab.dlr.de/g/other\"}"), securityContext);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<GitReference> captor = ArgumentCaptor.forClass(GitReference.class);
    verify(gitReferenceDAO).createOrUpdate(captor.capture());
    assertEquals("https://gitlab.dlr.de/g/other", captor.getValue().getRepoUrl());
    // Absent fields preserved
    assertEquals("main", captor.getValue().getRef());
    assertEquals("src", captor.getValue().getPath());
  }

  @Test
  void patch_returns200AndClearsRefWhenNullSent() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var updated = new GitReference("https://gitlab.dlr.de/g/r", null, "src");
    updated.setAppId(GR_APP_ID);
    updated.setDataObject(gr.getDataObject());
    when(gitReferenceDAO.createOrUpdate(any(GitReference.class))).thenReturn(updated);

    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{\"ref\":null}"), securityContext);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<GitReference> captor = ArgumentCaptor.forClass(GitReference.class);
    verify(gitReferenceDAO).createOrUpdate(captor.capture());
    assertNull(captor.getValue().getRef());
    // repoUrl and path preserved
    assertEquals("https://gitlab.dlr.de/g/r", captor.getValue().getRepoUrl());
    assertEquals("src", captor.getValue().getPath());
  }

  @Test
  void patch_returns400WhenRepoUrlNullSent() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{\"repoUrl\":null}"), securityContext);
    assertEquals(400, r.getStatus());
    verify(gitReferenceDAO, never()).createOrUpdate(any());
  }

  @Test
  void patch_returns400WhenRepoUrlBlank() {
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{\"repoUrl\":\"  \"}"), securityContext);
    assertEquals(400, r.getStatus());
    verify(gitReferenceDAO, never()).createOrUpdate(any());
  }

  @Test
  void patch_returns400WhenBodyNotObject() {
    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("\"not-an-object\""), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patch_returns400WhenBodyNull() {
    var r = resource.patch(DO_APP_ID, GR_APP_ID, null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patch_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{\"ref\":\"v1.0\"}"), securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void patch_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER)).thenReturn(false);
    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{\"ref\":\"v1.0\"}"), securityContext);
    assertEquals(403, r.getStatus());
    verify(gitReferenceDAO, never()).createOrUpdate(any());
  }

  @Test
  void patch_returns404WhenGitReferenceMissing() {
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(null);
    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{\"ref\":\"v1.0\"}"), securityContext);
    assertEquals(404, r.getStatus());
    verify(gitReferenceDAO, never()).createOrUpdate(any());
  }

  @Test
  void patch_returns404WhenGitReferenceBelongsToDifferentDataObject() {
    var other = new DataObject();
    other.setAppId("different-do-appid");
    var gr = new GitReference("https://gitlab.dlr.de/g/r", "main", null);
    gr.setAppId(GR_APP_ID);
    gr.setDataObject(other);
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{\"ref\":\"v1.0\"}"), securityContext);
    assertEquals(404, r.getStatus());
    verify(gitReferenceDAO, never()).createOrUpdate(any());
  }

  @Test
  void patch_returns200WhenEmptyObjectSent_noFieldsModified() {
    // Empty merge-patch body is valid — no fields change.
    GitReference gr = existingGr();
    when(gitReferenceDAO.findByAppId(GR_APP_ID)).thenReturn(gr);
    when(gitReferenceDAO.createOrUpdate(any(GitReference.class))).thenReturn(gr);

    var r = resource.patch(DO_APP_ID, GR_APP_ID, json("{}"), securityContext);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<GitReference> captor = ArgumentCaptor.forClass(GitReference.class);
    verify(gitReferenceDAO).createOrUpdate(captor.capture());
    // All fields preserved
    assertEquals("https://gitlab.dlr.de/g/r", captor.getValue().getRepoUrl());
    assertEquals("main", captor.getValue().getRef());
    assertEquals("src", captor.getValue().getPath());
  }
}
