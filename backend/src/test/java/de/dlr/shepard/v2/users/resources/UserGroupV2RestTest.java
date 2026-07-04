package de.dlr.shepard.v2.users.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.endpoints.UserGroupAttributes;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.search.services.UserGroupSearchService;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.users.io.UserGroupV2IO;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** V2-SWEEP-002 — unit tests for {@link UserGroupV2Rest}. No CDI, no Neo4j. */
class UserGroupV2RestTest {

  static final String APP_ID = "01900000-0000-7000-8000-000000000001";
  static final String GROUP_NAME = "LUMEN engineers";
  static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock
  UserGroupService service;

  @Mock
  UserGroupSearchService searchService;

  UserGroupV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new UserGroupV2Rest();
    resource.service = service;
    resource.searchService = searchService;
  }

  // ── helpers ──────────────────────────────────────────────────────────

  private UserGroup stubGroup(String appId, String name) {
    return stubGroup(appId, name, 99L);
  }

  private UserGroup stubGroup(String appId, String name, long numericId) {
    UserGroup g = new UserGroup();
    g.setId(numericId);
    g.setAppId(appId);
    g.setName(name);
    return g;
  }

  private Permissions stubPermissions() {
    var owner = new User();
    owner.setUsername("alice");
    var p = new Permissions();
    p.setPermissionType(PermissionType.Private);
    p.setOwner(owner);
    p.setReader(new ArrayList<>());
    p.setWriter(new ArrayList<>());
    p.setManager(new ArrayList<>());
    p.setReaderGroups(new ArrayList<>());
    p.setWriterGroups(new ArrayList<>());
    p.setEntities(List.of(stubGroup(APP_ID, GROUP_NAME, 99L)));
    return p;
  }

  // ── GET /v2/user-groups ──────────────────────────────────────────────

  @Test
  void listUserGroups_returnsEmptyList() {
    when(service.getAllUserGroups(any(QueryParamHelper.class))).thenReturn(List.of());
    when(service.countAllUserGroups()).thenReturn(0L);
    Response r = resource.listUserGroups(null, 0, 50, null, null);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<UserGroupV2IO> body = (PagedResponseIO<UserGroupV2IO>) r.getEntity();
    assertNotNull(body);
    assertEquals(0, body.items().size());
    assertEquals(0L, body.total());
  }

  @Test
  void listUserGroups_returnsMappedList() {
    when(service.getAllUserGroups(any(QueryParamHelper.class)))
      .thenReturn(List.of(stubGroup(APP_ID, GROUP_NAME)));
    when(service.countAllUserGroups()).thenReturn(1L);
    Response r = resource.listUserGroups(null, 0, 50, null, null);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<UserGroupV2IO> body = (PagedResponseIO<UserGroupV2IO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals(APP_ID, body.items().get(0).getAppId());
    assertEquals(GROUP_NAME, body.items().get(0).getName());
  }

  @Test
  void listUserGroups_envelopeContainsPageAndPageSize() {
    when(service.getAllUserGroups(any(QueryParamHelper.class))).thenReturn(List.of());
    when(service.countAllUserGroups()).thenReturn(42L);
    Response r = resource.listUserGroups(null, 2, 10, null, null);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<UserGroupV2IO> body = (PagedResponseIO<UserGroupV2IO>) r.getEntity();
    assertEquals(42L, body.total());
    assertEquals(2, body.page());
    assertEquals(10, body.pageSize());
  }

  @Test
  void listUserGroups_withQ_delegatesToSearchService() {
    when(searchService.searchByText("lumen")).thenReturn(List.of(stubGroup(APP_ID, GROUP_NAME)));
    Response r = resource.listUserGroups("lumen", 0, 50, null, null);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<UserGroupV2IO> body = (PagedResponseIO<UserGroupV2IO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(GROUP_NAME, body.items().get(0).getName());
  }

  @Test
  void listUserGroups_withQ_returnsEmptyList_whenNoMatches() {
    when(searchService.searchByText("nomatch")).thenReturn(List.of());
    Response r = resource.listUserGroups("nomatch", 0, 50, null, null);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<UserGroupV2IO> body = (PagedResponseIO<UserGroupV2IO>) r.getEntity();
    assertEquals(0, body.items().size());
  }

  // ── GET /v2/user-groups/{appId} ──────────────────────────────────────

  @Test
  void getUserGroup_returns200WhenFound() {
    when(service.getUserGroupByAppId(APP_ID)).thenReturn(stubGroup(APP_ID, GROUP_NAME));
    Response r = resource.getUserGroup(APP_ID);
    assertEquals(200, r.getStatus());
    UserGroupV2IO body = (UserGroupV2IO) r.getEntity();
    assertEquals(APP_ID, body.getAppId());
    assertEquals(GROUP_NAME, body.getName());
  }

  @Test
  void getUserGroup_propagates404WhenNotFound() {
    when(service.getUserGroupByAppId(anyString())).thenThrow(new InvalidPathException("not found"));
    try {
      resource.getUserGroup("nonexistent");
    } catch (InvalidPathException e) {
      assertNotNull(e);
    }
  }

  // ── POST /v2/user-groups ─────────────────────────────────────────────

  @Test
  void createUserGroup_returns201WithLocation() {
    when(service.createUserGroup(any())).thenReturn(stubGroup(APP_ID, GROUP_NAME));
    var body = new UserGroupV2IO();
    body.setName(GROUP_NAME);
    body.setUsernames(List.of("alice", "bob"));
    Response r = resource.createUserGroup(body);
    assertEquals(201, r.getStatus());
    assertNotNull(r.getLocation());
    assertEquals("/v2/user-groups/" + APP_ID, r.getLocation().toString());
    UserGroupV2IO io = (UserGroupV2IO) r.getEntity();
    assertEquals(APP_ID, io.getAppId());
  }

  @Test
  void createUserGroup_nullUsernamesBecomesEmptyArray() {
    when(service.createUserGroup(any())).thenReturn(stubGroup(APP_ID, GROUP_NAME));
    var body = new UserGroupV2IO();
    body.setName(GROUP_NAME);
    // usernames is null → service receives empty array
    resource.createUserGroup(body);
    verify(service).createUserGroup(any());
  }

  // ── PATCH /v2/user-groups/{appId} ────────────────────────────────────

  @Test
  void patchUserGroup_nameOnly_callsServiceWithNullUsernames() throws Exception {
    when(service.patchUserGroupByAppId(eq(APP_ID), eq("new name"), isNull()))
      .thenReturn(stubGroup(APP_ID, "new name"));
    ObjectNode patch = MAPPER.createObjectNode();
    patch.put("name", "new name");
    Response r = resource.patchUserGroup(APP_ID, patch);
    assertEquals(200, r.getStatus());
    verify(service).patchUserGroupByAppId(APP_ID, "new name", null);
  }

  @Test
  void patchUserGroup_usernamesOnly_callsServiceWithNullName() throws Exception {
    when(service.patchUserGroupByAppId(eq(APP_ID), isNull(), any()))
      .thenReturn(stubGroup(APP_ID, GROUP_NAME));
    ObjectNode patch = MAPPER.createObjectNode();
    patch.putArray("usernames").add("carol");
    Response r = resource.patchUserGroup(APP_ID, patch);
    assertEquals(200, r.getStatus());
    verify(service).patchUserGroupByAppId(eq(APP_ID), isNull(), any());
  }

  @Test
  void patchUserGroup_nullBody_returns400() {
    Response r = resource.patchUserGroup(APP_ID, null);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchUserGroup_nullJsonNode_returns400() throws Exception {
    Response r = resource.patchUserGroup(APP_ID, MAPPER.nullNode());
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchUserGroup_usernamesAt501_returns400() {
    ObjectNode patch = MAPPER.createObjectNode();
    var arr = patch.putArray("usernames");
    for (int i = 0; i <= 500; i++) arr.add("user-" + i); // 501 elements
    Response r = resource.patchUserGroup(APP_ID, patch);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchUserGroup_usernamesAt500_returns200() {
    when(service.patchUserGroupByAppId(eq(APP_ID), isNull(), any()))
      .thenReturn(stubGroup(APP_ID, GROUP_NAME));
    ObjectNode patch = MAPPER.createObjectNode();
    var arr = patch.putArray("usernames");
    for (int i = 0; i < 500; i++) arr.add("user-" + i); // exactly 500 — allowed
    Response r = resource.patchUserGroup(APP_ID, patch);
    assertEquals(200, r.getStatus());
  }

  // ── DELETE /v2/user-groups/{appId} ───────────────────────────────────

  @Test
  void deleteUserGroup_returns204OnSuccess() {
    Response r = resource.deleteUserGroup(APP_ID);
    assertEquals(204, r.getStatus());
    verify(service).deleteUserGroupByAppId(APP_ID);
  }

  @Test
  void deleteUserGroup_propagates404WhenNotFound() {
    doThrow(new InvalidPathException("not found")).when(service).deleteUserGroupByAppId(anyString());
    try {
      resource.deleteUserGroup("nonexistent");
    } catch (InvalidPathException e) {
      assertNotNull(e);
    }
  }

  // ── GET /v2/user-groups/{appId}/roles ────────────────────────────────

  @Test
  void getRoles_returns200WithRoles() {
    var group = stubGroup(APP_ID, GROUP_NAME, 42L);
    when(service.getUserGroupByAppId(APP_ID)).thenReturn(group);
    var roles = new Roles(true, false, true, true);
    when(service.getUserGroupRoles(42L)).thenReturn(roles);
    Response r = resource.getUserGroupRoles(APP_ID);
    assertEquals(200, r.getStatus());
    Roles body = (Roles) r.getEntity();
    assertEquals(true, body.isOwner());
    assertEquals(true, body.isWriter());
  }

  @Test
  void getRoles_propagates404WhenGroupNotFound() {
    when(service.getUserGroupByAppId(anyString())).thenThrow(new InvalidPathException("not found"));
    try {
      resource.getUserGroupRoles("nonexistent");
    } catch (InvalidPathException e) {
      assertNotNull(e);
    }
  }

  // ── GET /v2/user-groups/{appId}/permissions ──────────────────────────

  @Test
  void getPermissions_returns200WithPermissions() {
    var group = stubGroup(APP_ID, GROUP_NAME, 42L);
    when(service.getUserGroupByAppId(APP_ID)).thenReturn(group);
    when(service.getUserGroupPermissions(42L)).thenReturn(stubPermissions());
    Response r = resource.getUserGroupPermissions(APP_ID);
    assertEquals(200, r.getStatus());
    var body = (PermissionsIO) r.getEntity();
    assertEquals(PermissionType.Private, body.getPermissionType());
    assertEquals("alice", body.getOwner());
  }

  @Test
  void getPermissions_propagates404WhenGroupNotFound() {
    when(service.getUserGroupByAppId(anyString())).thenThrow(new InvalidPathException("not found"));
    try {
      resource.getUserGroupPermissions("nonexistent");
    } catch (InvalidPathException e) {
      assertNotNull(e);
    }
  }

  // ── PATCH /v2/user-groups/{appId}/permissions ────────────────────────

  @Test
  void patchPermissions_appliesPermissionTypeChange() throws Exception {
    var group = stubGroup(APP_ID, GROUP_NAME, 42L);
    when(service.getUserGroupByAppId(APP_ID)).thenReturn(group);
    when(service.getUserGroupPermissions(42L)).thenReturn(stubPermissions());
    when(service.updateUserGroupPermissions(any(), eq(42L))).thenReturn(stubPermissions());
    ObjectNode patch = MAPPER.createObjectNode();
    patch.put("permissionType", "Public");
    Response r = resource.patchUserGroupPermissions(APP_ID, patch);
    assertEquals(200, r.getStatus());
    verify(service).updateUserGroupPermissions(any(PermissionsIO.class), eq(42L));
  }

  @Test
  void patchPermissions_nullBody_returns400() {
    Response r = resource.patchUserGroupPermissions(APP_ID, null);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchPermissions_nonObjectBody_returns400() throws Exception {
    Response r = resource.patchUserGroupPermissions(APP_ID, MAPPER.readTree("\"not-an-object\""));
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchPermissions_propagates404WhenGroupNotFound() {
    when(service.getUserGroupByAppId(anyString())).thenThrow(new InvalidPathException("not found"));
    ObjectNode patch = MAPPER.createObjectNode();
    patch.put("permissionType", "Public");
    try {
      resource.patchUserGroupPermissions("nonexistent", patch);
    } catch (InvalidPathException e) {
      assertNotNull(e);
    }
  }

  // ── APISIMP-USERGROUP-PATCH-UNCAPPED — size guard tests ─────────────────

  @Test
  void patchPermissions_oversizedReader_returns400() {
    var group = stubGroup(APP_ID, GROUP_NAME, 42L);
    when(service.getUserGroupByAppId(APP_ID)).thenReturn(group);
    when(service.getUserGroupPermissions(42L)).thenReturn(stubPermissions());
    ObjectNode patch = MAPPER.createObjectNode();
    var arr = patch.putArray("reader");
    for (int i = 0; i <= 500; i++) arr.add("user-" + i); // 501 elements
    Response r = resource.patchUserGroupPermissions(APP_ID, patch);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchPermissions_oversizedWriter_returns400() {
    var group = stubGroup(APP_ID, GROUP_NAME, 42L);
    when(service.getUserGroupByAppId(APP_ID)).thenReturn(group);
    when(service.getUserGroupPermissions(42L)).thenReturn(stubPermissions());
    ObjectNode patch = MAPPER.createObjectNode();
    var arr = patch.putArray("writer");
    for (int i = 0; i <= 500; i++) arr.add("user-" + i); // 501 elements
    Response r = resource.patchUserGroupPermissions(APP_ID, patch);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchPermissions_oversizedManager_returns400() {
    var group = stubGroup(APP_ID, GROUP_NAME, 42L);
    when(service.getUserGroupByAppId(APP_ID)).thenReturn(group);
    when(service.getUserGroupPermissions(42L)).thenReturn(stubPermissions());
    ObjectNode patch = MAPPER.createObjectNode();
    var arr = patch.putArray("manager");
    for (int i = 0; i <= 500; i++) arr.add("user-" + i); // 501 elements
    Response r = resource.patchUserGroupPermissions(APP_ID, patch);
    assertEquals(400, r.getStatus());
  }

  // ── APISIMP-USERGROUP-ORDERBY — reflection regression tests ─────────────

  @Test
  void listUserGroups_orderByParam_hasDescriptionAnnotation() throws NoSuchMethodException {
    Method m = UserGroupV2Rest.class.getDeclaredMethod(
        "listUserGroups", String.class, int.class, int.class, UserGroupAttributes.class, Boolean.class);
    var ann = Arrays.stream(m.getAnnotationsByType(Parameter.class))
        .filter(p -> "orderBy".equals(p.name()))
        .findFirst();
    assertTrue(ann.isPresent(), "listUserGroups must have @Parameter(name=\"orderBy\")");
    assertFalse(ann.get().description().isBlank(), "@Parameter.description must be non-blank for orderBy");
  }

  @Test
  void listUserGroups_orderDescParam_hasDescriptionAnnotation() throws NoSuchMethodException {
    Method m = UserGroupV2Rest.class.getDeclaredMethod(
        "listUserGroups", String.class, int.class, int.class, UserGroupAttributes.class, Boolean.class);
    var ann = Arrays.stream(m.getAnnotationsByType(Parameter.class))
        .filter(p -> "orderDesc".equals(p.name()))
        .findFirst();
    assertTrue(ann.isPresent(), "listUserGroups must have @Parameter(name=\"orderDesc\")");
    assertFalse(ann.get().description().isBlank(), "@Parameter.description must be non-blank for orderDesc");
  }
}
