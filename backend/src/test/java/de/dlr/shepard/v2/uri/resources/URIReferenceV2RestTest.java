package de.dlr.shepard.v2.uri.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Plain-Mockito unit tests for {@link URIReferenceV2Rest} (REF-EDIT-6).
 * Same wiring pattern as {@code FileReferenceV2RestTest}.
 */
class URIReferenceV2RestTest {

  private static final String REF_APP_ID = "uri-ref-app-1";
  private static final String PARENT_DO_APP_ID = "parent-do-app-42";
  private static final long PARENT_DO_OGM_ID = 42L;
  private static final String CALLER = "alice";

  @Mock
  URIReferenceService uriReferenceService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  ObjectMapper objectMapper = new ObjectMapper();
  URIReferenceV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new URIReferenceV2Rest();
    resource.uriReferenceService = uriReferenceService;
    resource.permissionsService = permissionsService;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    // Default: write allowed via appId path.
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(PARENT_DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    // Default: write allowed via legacy OGM path.
    when(permissionsService.isAccessTypeAllowedForUser(eq(PARENT_DO_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private URIReference existingRef() {
    var parent = new DataObject(PARENT_DO_OGM_ID);
    parent.setAppId(PARENT_DO_APP_ID);
    parent.setShepardId(101L);
    var ref = new URIReference(7L);
    ref.setAppId(REF_APP_ID);
    ref.setName("DLR Homepage");
    ref.setUri("https://www.dlr.de");
    ref.setRelationship("seeAlso");
    ref.setDataObject(parent);
    return ref;
  }

  // ─── PATCH /v2/uri-references/{appId} ─────────────────────────────────────

  @Test
  void patch_returns200OnSuccess() throws Exception {
    URIReference ref = existingRef();
    when(uriReferenceService.findByAppId(REF_APP_ID)).thenReturn(ref);

    URIReference updated = existingRef();
    updated.setName("Updated Name");
    updated.setUri("https://updated.example.com");
    when(uriReferenceService.patchReferenceByAppId(eq(REF_APP_ID), any())).thenReturn(updated);

    var body = objectMapper.readTree("{\"name\":\"Updated Name\",\"uri\":\"https://updated.example.com\"}");
    var r = resource.patchReference(REF_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    var io = (URIReferenceIO) r.getEntity();
    assertEquals("Updated Name", io.getName());
    assertEquals("https://updated.example.com", io.getUri());
  }

  @Test
  void patch_returns404WhenRefNotFound() throws Exception {
    when(uriReferenceService.findByAppId(anyString())).thenReturn(null);

    var body = objectMapper.readTree("{\"name\":\"New Name\"}");
    var r = resource.patchReference("unknown-app-id", body, securityContext);

    assertEquals(404, r.getStatus());
  }

  @Test
  void patch_returns401WhenUnauthenticated() throws Exception {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    var body = objectMapper.readTree("{\"name\":\"New Name\"}");
    var r = resource.patchReference(REF_APP_ID, body, securityContext);

    assertEquals(401, r.getStatus());
  }

  @Test
  void patch_returns403WhenNoWritePermission() throws Exception {
    when(uriReferenceService.findByAppId(REF_APP_ID)).thenReturn(existingRef());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(PARENT_DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);

    var body = objectMapper.readTree("{\"name\":\"New Name\"}");
    var r = resource.patchReference(REF_APP_ID, body, securityContext);

    assertEquals(403, r.getStatus());
  }

  @Test
  void patch_returns400WhenBlankUri() throws Exception {
    when(uriReferenceService.findByAppId(REF_APP_ID)).thenReturn(existingRef());
    when(uriReferenceService.patchReferenceByAppId(eq(REF_APP_ID), any()))
      .thenThrow(new IllegalArgumentException("uri must not be blank"));

    var body = objectMapper.readTree("{\"uri\":\"\"}");
    var r = resource.patchReference(REF_APP_ID, body, securityContext);

    assertEquals(400, r.getStatus());
  }

  @Test
  void patch_returns400WhenBodyNotObject() throws Exception {
    var body = objectMapper.readTree("[\"array\",\"not\",\"object\"]");
    var r = resource.patchReference(REF_APP_ID, body, securityContext);

    assertEquals(400, r.getStatus());
  }

  @Test
  void patch_returns404WhenOrphanRef() throws Exception {
    // Reference with no DataObject attached — graph inconsistency → 404
    var orphan = existingRef();
    orphan.setDataObject(null);
    when(uriReferenceService.findByAppId(REF_APP_ID)).thenReturn(orphan);

    var body = objectMapper.readTree("{\"name\":\"New Name\"}");
    var r = resource.patchReference(REF_APP_ID, body, securityContext);

    assertEquals(404, r.getStatus());
  }

  // ─── jsonNodeToMap helper ─────────────────────────────────────────────────

  @Test
  void jsonNodeToMap_preservesNullValues() throws Exception {
    var node = objectMapper.readTree("{\"relationship\":null,\"name\":\"foo\"}");
    Map<String, Object> m = resource.jsonNodeToMap(node);
    assertEquals(true, m.containsKey("relationship"));
    assertEquals(null, m.get("relationship"));
    assertEquals("foo", m.get("name"));
  }
}
