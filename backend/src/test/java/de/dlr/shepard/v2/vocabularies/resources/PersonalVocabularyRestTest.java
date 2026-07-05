package de.dlr.shepard.v2.vocabularies.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.vocabularies.io.PersonalVocabularyRequestIO;
import de.dlr.shepard.v2.vocabularies.io.VocabularyIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SEMA-V6-014 — unit tests for {@link PersonalVocabularyRest}.
 *
 * <p>Tests cover: feature-disabled gate (403), bad name (400),
 * duplicate detection (409), successful creation (201), list endpoint (200).
 */
class PersonalVocabularyRestTest {

  private OntologyConfigService configService;
  private VocabularyDAO vocabDAO;
  private UserDAO userDAO;
  private AuthenticationContext authCtx;

  private PersonalVocabularyRest rest;

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static SemanticConfig configWith(boolean personalEnabled) {
    SemanticConfig c = new SemanticConfig();
    c.setAppId("cfg-app-id");
    c.setPersonalVocabulariesEnabled(personalEnabled);
    return c;
  }

  private static User userWith(String username, String appId) {
    User u = new User(username);
    u.setAppId(appId);
    return u;
  }

  private static Vocabulary savedVocab(String appId, String uri, String name, String ownerAppId) {
    Vocabulary v = new Vocabulary();
    v.setAppId(appId);
    v.setUri(uri);
    v.setLabel(name);
    v.setType("PERSONAL");
    v.setOwnedByUserAppId(ownerAppId);
    v.setEnabled(true);
    return v;
  }

  private static PersonalVocabularyRequestIO requestWith(String name, String description) {
    PersonalVocabularyRequestIO io = new PersonalVocabularyRequestIO();
    io.setName(name);
    io.setDescription(description);
    return io;
  }

  @BeforeEach
  void setUp() {
    configService = mock(OntologyConfigService.class);
    vocabDAO      = mock(VocabularyDAO.class);
    userDAO       = mock(UserDAO.class);
    authCtx       = mock(AuthenticationContext.class);

    rest = new PersonalVocabularyRest();
    rest.configService = configService;
    rest.vocabDAO      = vocabDAO;
    rest.userDAO       = userDAO;
    rest.authCtx       = authCtx;
  }

  // ─── POST tests ──────────────────────────────────────────────────────────

  @Test
  void createReturns403WhenFeatureDisabled() {
    when(configService.loadSingleton()).thenReturn(configWith(false));

    Response response = rest.create(requestWith("my-vocab", null));

    assertEquals(403, response.getStatus());
    verify(vocabDAO, never()).createOrUpdate(any());
  }

  @Test
  void createReturns400WhenNameIsBlank() {
    when(configService.loadSingleton()).thenReturn(configWith(true));
    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", "user-app-id-alice"));

    Response response = rest.create(requestWith("  ", null));

    assertEquals(400, response.getStatus());
    verify(vocabDAO, never()).createOrUpdate(any());
  }

  @Test
  void createReturns400WhenNameHasInvalidChars() {
    when(configService.loadSingleton()).thenReturn(configWith(true));
    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", "user-app-id-alice"));

    Response response = rest.create(requestWith("My Vocab!", null));

    assertEquals(400, response.getStatus());
    verify(vocabDAO, never()).createOrUpdate(any());
  }

  @Test
  void createReturns409OnDuplicate() {
    String userAppId = "user-app-id-alice";
    String name = "my-vocab";
    String uri = "urn:shepard:personal:" + userAppId + ":" + name;

    when(configService.loadSingleton()).thenReturn(configWith(true));
    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", userAppId));
    when(vocabDAO.findByUri(uri)).thenReturn(savedVocab("existing-appid", uri, name, userAppId));

    Response response = rest.create(requestWith(name, null));

    assertEquals(409, response.getStatus());
    verify(vocabDAO, never()).createOrUpdate(any());
  }

  @Test
  void createReturns201OnSuccess() {
    String userAppId = "user-app-id-alice";
    String name = "my-vocab";
    String uri = "urn:shepard:personal:" + userAppId + ":" + name;
    Vocabulary saved = savedVocab("new-vocab-appid", uri, name, userAppId);

    when(configService.loadSingleton()).thenReturn(configWith(true));
    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", userAppId));
    when(vocabDAO.findByUri(uri)).thenReturn(null); // no duplicate
    when(vocabDAO.createOrUpdate(any(Vocabulary.class))).thenReturn(saved);

    Response response = rest.create(requestWith(name, "My personal namespace"));

    assertEquals(201, response.getStatus());
    VocabularyIO io = (VocabularyIO) response.getEntity();
    assertNotNull(io);
    assertEquals("new-vocab-appid", io.getAppId());
    assertEquals(uri, io.getUri());
    assertEquals("PERSONAL", io.getType());
    assertEquals(userAppId, io.getOwnedByUserAppId());
    assertTrue(io.isEnabled());
  }

  // ─── GET tests ───────────────────────────────────────────────────────────

  @Test
  void listReturnsEmptyPageWhenUserHasNoPersonalVocabs() {
    String userAppId = "user-app-id-alice";

    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", userAppId));
    when(vocabDAO.countPersonalByOwner(userAppId)).thenReturn(0L);
    when(vocabDAO.listPersonalByOwner(userAppId, 0, 50)).thenReturn(List.of());

    Response response = rest.list(0, 50);

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> body = (PagedResponseIO<VocabularyIO>) response.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty());
    assertEquals(0L, body.total());
    assertEquals(0, body.page());
    assertEquals(50, body.pageSize());
    assertEquals("0", response.getHeaderString("X-Total-Count"));
  }

  @Test
  void listReturnsPersonalVocabsForCallerFirstPage() {
    String userAppId = "user-app-id-alice";
    String uri1 = "urn:shepard:personal:" + userAppId + ":vocab-a";
    String uri2 = "urn:shepard:personal:" + userAppId + ":vocab-b";
    List<Vocabulary> owned = List.of(
      savedVocab("appid-1", uri1, "vocab-a", userAppId),
      savedVocab("appid-2", uri2, "vocab-b", userAppId)
    );

    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", userAppId));
    when(vocabDAO.countPersonalByOwner(userAppId)).thenReturn(2L);
    when(vocabDAO.listPersonalByOwner(userAppId, 0, 50)).thenReturn(owned);

    Response response = rest.list(0, 50);

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> body = (PagedResponseIO<VocabularyIO>) response.getEntity();
    assertNotNull(body);
    assertEquals(2, body.items().size());
    assertEquals(2L, body.total());
    assertEquals(0, body.page());
    assertEquals(50, body.pageSize());
    assertTrue(body.items().stream().allMatch(v -> "PERSONAL".equals(v.getType())));
    assertTrue(body.items().stream().allMatch(v -> userAppId.equals(v.getOwnedByUserAppId())));
    assertEquals("2", response.getHeaderString("X-Total-Count"));
  }

  @Test
  void listReturnsEmptyPageWhenUserHasNoAppId() {
    User noAppIdUser = new User("bob");
    // appId is null by default

    when(authCtx.getCurrentUserName()).thenReturn("bob");
    when(userDAO.find("bob")).thenReturn(noAppIdUser);

    Response response = rest.list(0, 50);

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> body = (PagedResponseIO<VocabularyIO>) response.getEntity();
    assertTrue(body.items().isEmpty());
    assertEquals(0L, body.total());
    verify(vocabDAO, never()).countPersonalByOwner(any());
    verify(vocabDAO, never()).listPersonalByOwner(any(), anyInt(), anyInt());
  }

  @Test
  void listPaginatesCorrectlySecondPage() {
    String userAppId = "user-app-id-alice";
    // page=1, pageSize=5 → skip = min(1*5, 7) = 5 → DB returns items[5..6] (2 items)
    List<Vocabulary> page2Items = List.of(
      savedVocab("appid-5", "urn:shepard:personal:" + userAppId + ":vocab-5", "vocab-5", userAppId),
      savedVocab("appid-6", "urn:shepard:personal:" + userAppId + ":vocab-6", "vocab-6", userAppId)
    );

    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", userAppId));
    when(vocabDAO.countPersonalByOwner(userAppId)).thenReturn(7L);
    when(vocabDAO.listPersonalByOwner(userAppId, 5, 5)).thenReturn(page2Items);

    Response response = rest.list(1, 5);  // page 1, size 5 → skip 5

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> body = (PagedResponseIO<VocabularyIO>) response.getEntity();
    assertEquals(2, body.items().size());
    assertEquals(7L, body.total());
    assertEquals(1, body.page());
    assertEquals(5, body.pageSize());
    assertEquals("7", response.getHeaderString("X-Total-Count"));
  }

  @Test
  void listReturnsBeyondEndAsEmptyItems() {
    String userAppId = "user-app-id-alice";
    // page=5, pageSize=50 → skip = min(5*50=250, 1) = 1 → DB returns nothing (past end)

    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", userAppId));
    when(vocabDAO.countPersonalByOwner(userAppId)).thenReturn(1L);
    when(vocabDAO.listPersonalByOwner(userAppId, 1, 50)).thenReturn(List.of());

    Response response = rest.list(5, 50);  // page 5, size 50 — skip lands at 1 (beyond the only item)

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> body = (PagedResponseIO<VocabularyIO>) response.getEntity();
    assertTrue(body.items().isEmpty());
    assertEquals(1L, body.total());
    assertEquals("1", response.getHeaderString("X-Total-Count"));
  }

  @Test
  void listXTotalCountHeaderPresent() {
    String userAppId = "user-app-id-alice";
    String uri1 = "urn:shepard:personal:" + userAppId + ":vocab-a";

    when(authCtx.getCurrentUserName()).thenReturn("alice");
    when(userDAO.find("alice")).thenReturn(userWith("alice", userAppId));
    when(vocabDAO.countPersonalByOwner(userAppId)).thenReturn(1L);
    when(vocabDAO.listPersonalByOwner(userAppId, 0, 50))
      .thenReturn(List.of(savedVocab("appid-1", uri1, "vocab-a", userAppId)));

    Response response = rest.list(0, 50);

    assertEquals("1", response.getHeaderString("X-Total-Count"));
  }

  @Test
  void createReturns403WhenUserHasNoAppId() {
    User noAppIdUser = new User("charlie");
    // appId is null by default

    when(configService.loadSingleton()).thenReturn(configWith(true));
    when(authCtx.getCurrentUserName()).thenReturn("charlie");
    when(userDAO.find("charlie")).thenReturn(noAppIdUser);

    Response response = rest.create(requestWith("my-vocab", null));

    assertEquals(403, response.getStatus());
    verify(vocabDAO, never()).createOrUpdate(any());
  }

  @Test
  void namePatterMatchesValidSlugs() {
    assertTrue(PersonalVocabularyRest.NAME_PATTERN.matcher("my-vocab").matches());
    assertTrue(PersonalVocabularyRest.NAME_PATTERN.matcher("my_vocab").matches());
    assertTrue(PersonalVocabularyRest.NAME_PATTERN.matcher("abc").matches());
    assertTrue(PersonalVocabularyRest.NAME_PATTERN.matcher("a1b2c3").matches());
    assertFalse(PersonalVocabularyRest.NAME_PATTERN.matcher("MY-VOCAB").matches());
    assertFalse(PersonalVocabularyRest.NAME_PATTERN.matcher("-starts-with-dash").matches());
    assertFalse(PersonalVocabularyRest.NAME_PATTERN.matcher("has spaces").matches());
    assertFalse(PersonalVocabularyRest.NAME_PATTERN.matcher("").matches());
  }
}
