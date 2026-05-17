package de.dlr.shepard.v2.users.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.services.UserService;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link MePreferencesRest} — {@code GET/PATCH /v2/users/me/preferences}.
 * Per {@code aidocs/16 U1d}.
 */
@SuppressWarnings("unchecked")
class MePreferencesRestTest {

  static final String CALLER = "alice";
  static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock
  UserService userService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  MePreferencesRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new MePreferencesRest();
    resource.userService = userService;
    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ── GET ──────────────────────────────────────────────────────────────────

  @Test
  void get_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    var r = resource.getPreferences(sc);
    assertEquals(401, r.getStatus());
    verifyNoInteractions(userService);
  }

  @Test
  void get_returnsEmptyMapWhenNoPrefsSet() {
    when(userService.getPreferences(CALLER)).thenReturn(new HashMap<>());
    var r = resource.getPreferences(sc);
    assertEquals(200, r.getStatus());
    var body = (Map<String, String>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.isEmpty());
  }

  @Test
  void get_returnsExistingPrefs() {
    Map<String, String> stored = Map.of("theme", "dark", "language", "de");
    when(userService.getPreferences(CALLER)).thenReturn(new HashMap<>(stored));
    var r = resource.getPreferences(sc);
    assertEquals(200, r.getStatus());
    var body = (Map<String, String>) r.getEntity();
    assertEquals("dark", body.get("theme"));
    assertEquals("de", body.get("language"));
  }

  // ── PATCH ─────────────────────────────────────────────────────────────────

  @Test
  void patch_returns401WhenUnauthenticated() throws Exception {
    when(sc.getUserPrincipal()).thenReturn(null);
    var r = resource.patchPreferences(MAPPER.readTree("{\"theme\":\"dark\"}"), sc);
    assertEquals(401, r.getStatus());
    verifyNoInteractions(userService);
  }

  @Test
  void patch_returns400ForNonObjectBody() throws Exception {
    var r = resource.patchPreferences(MAPPER.readTree("\"not-an-object\""), sc);
    assertEquals(400, r.getStatus());
    verifyNoInteractions(userService);
  }

  @Test
  void patch_returns400ForNullBody() {
    var r = resource.patchPreferences(null, sc);
    assertEquals(400, r.getStatus());
    verifyNoInteractions(userService);
  }

  @Test
  void patch_setsNewKey() throws Exception {
    Map<String, String> merged = Map.of("theme", "dark");
    when(userService.patchPreferences(eq(CALLER), anyMap())).thenReturn(new HashMap<>(merged));

    var r = resource.patchPreferences(MAPPER.readTree("{\"theme\":\"dark\"}"), sc);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    verify(userService).patchPreferences(eq(CALLER), captor.capture());
    assertEquals("dark", captor.getValue().get("theme"));

    var body = (Map<String, String>) r.getEntity();
    assertEquals("dark", body.get("theme"));
  }

  @Test
  void patch_nullValueRemovesKey() throws Exception {
    // RFC 7396: explicit null removes the key from the map.
    Map<String, String> afterPatch = Map.of("language", "de"); // "theme" removed
    when(userService.patchPreferences(eq(CALLER), anyMap())).thenReturn(new HashMap<>(afterPatch));

    var r = resource.patchPreferences(MAPPER.readTree("{\"theme\":null}"), sc);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    verify(userService).patchPreferences(eq(CALLER), captor.capture());
    // The patch map should carry null for "theme" as the remove sentinel.
    assertTrue(captor.getValue().containsKey("theme"));
    assertEquals(null, captor.getValue().get("theme"));
  }

  @Test
  void patch_unknownKeyAccepted() throws Exception {
    // Open-world: unknown string keys are accepted without validation.
    Map<String, String> merged = Map.of("myCustomKey", "value");
    when(userService.patchPreferences(eq(CALLER), anyMap())).thenReturn(new HashMap<>(merged));

    var r = resource.patchPreferences(MAPPER.readTree("{\"myCustomKey\":\"value\"}"), sc);
    assertEquals(200, r.getStatus());
  }

  @Test
  void patch_nonStringValueReturns400() throws Exception {
    // Non-null, non-string value must be rejected with 400.
    var r = resource.patchPreferences(MAPPER.readTree("{\"theme\":42}"), sc);
    assertEquals(400, r.getStatus());
    verifyNoInteractions(userService);
  }

  @Test
  void patch_preservesExistingKeysNotInBody() throws Exception {
    // Keys absent from the PATCH body are preserved — RFC 7396 semantics.
    // The service is responsible for the merge; the REST layer must pass
    // ONLY the keys from the body (not add the existing ones).
    Map<String, String> afterPatch = Map.of("theme", "dark", "language", "de");
    when(userService.patchPreferences(eq(CALLER), anyMap())).thenReturn(new HashMap<>(afterPatch));

    // Body only contains "language" — "theme" is absent (not removed, just absent).
    var r = resource.patchPreferences(MAPPER.readTree("{\"language\":\"de\"}"), sc);
    assertEquals(200, r.getStatus());

    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    verify(userService).patchPreferences(eq(CALLER), captor.capture());
    // REST layer should only pass what was in the body, not add keys from the DB.
    assertEquals(1, captor.getValue().size());
    assertEquals("de", captor.getValue().get("language"));

    // The response shows both keys (service returned them both).
    var body = (Map<String, String>) r.getEntity();
    assertEquals("dark", body.get("theme"));
    assertEquals("de", body.get("language"));
  }

  @Test
  void patch_emptyBodyPreservesAllKeys() throws Exception {
    // An empty patch body {} is valid and preserves everything.
    Map<String, String> existing = Map.of("theme", "dark");
    when(userService.patchPreferences(eq(CALLER), anyMap())).thenReturn(new HashMap<>(existing));

    var r = resource.patchPreferences(MAPPER.readTree("{}"), sc);
    assertEquals(200, r.getStatus());
  }
}
