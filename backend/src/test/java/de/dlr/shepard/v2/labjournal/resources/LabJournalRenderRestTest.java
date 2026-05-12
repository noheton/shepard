package de.dlr.shepard.v2.labjournal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LabJournalRenderRestTest {

  static final String APP_ID = "018f9c5a-7e26-7000-a000-000000000099";
  static final long OGM_ID = 99L;
  static final long COLLECTION_OGM_ID = 7L;
  static final String CALLER = "alice";

  @Mock
  LabJournalEntryDAO labJournalEntryDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  LabJournalRenderRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new LabJournalRenderRest();
    resource.labJournalEntryDAO = labJournalEntryDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.render(APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void returns404WhenAppIdUnknown() {
    when(entityIdResolver.resolveLong(APP_ID)).thenThrow(new NotFoundException("no such entity"));
    Response r = resource.render(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void returns404WhenEntryNotFound() {
    when(entityIdResolver.resolveLong(APP_ID)).thenReturn(OGM_ID);
    when(labJournalEntryDAO.findByNeo4jId(OGM_ID)).thenReturn(null);
    Response r = resource.render(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void returns403WhenCallerLacksReadPermission() {
    when(entityIdResolver.resolveLong(APP_ID)).thenReturn(OGM_ID);
    when(labJournalEntryDAO.findByNeo4jId(OGM_ID)).thenReturn(entryWithContent("hello"));
    when(permissionsService.isAccessTypeAllowedForUser(COLLECTION_OGM_ID, AccessType.Read, CALLER)).thenReturn(false);
    Response r = resource.render(APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void returns200WithPlainTextBody() {
    when(entityIdResolver.resolveLong(APP_ID)).thenReturn(OGM_ID);
    when(labJournalEntryDAO.findByNeo4jId(OGM_ID)).thenReturn(entryWithContent("plain text"));
    when(permissionsService.isAccessTypeAllowedForUser(COLLECTION_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    Response r = resource.render(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    String html = (String) r.getEntity();
    assertTrue(html.contains("plain text"), "plain text must appear in rendered HTML");
  }

  @Test
  void returns200WithMarkdownRenderedToHtml() {
    String markdown = "**bold** and [link](https://example.com)";
    when(entityIdResolver.resolveLong(APP_ID)).thenReturn(OGM_ID);
    when(labJournalEntryDAO.findByNeo4jId(OGM_ID)).thenReturn(entryWithContent(markdown));
    when(permissionsService.isAccessTypeAllowedForUser(COLLECTION_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    Response r = resource.render(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    String html = (String) r.getEntity();
    assertTrue(html.contains("<strong>bold</strong>"), "bold markdown must be rendered as <strong>");
    assertTrue(html.contains("<a href=\"https://example.com\""), "link markdown must be rendered as <a>");
  }

  @Test
  void xssInjectionInBodyIsStripped() {
    String xss = "safe text <script>alert('xss')</script>";
    when(entityIdResolver.resolveLong(APP_ID)).thenReturn(OGM_ID);
    when(labJournalEntryDAO.findByNeo4jId(OGM_ID)).thenReturn(entryWithContent(xss));
    when(permissionsService.isAccessTypeAllowedForUser(COLLECTION_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    Response r = resource.render(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    String html = (String) r.getEntity();
    assertTrue(!html.contains("<script>"), "<script> tag must be stripped by sanitiser");
    assertTrue(html.contains("safe text"), "safe text must survive sanitisation");
  }

  private LabJournalEntry entryWithContent(String content) {
    Collection collection = new Collection(COLLECTION_OGM_ID);
    DataObject dataObject = new DataObject();
    dataObject.setCollection(collection);
    LabJournalEntry entry = new LabJournalEntry();
    entry.setContent(content);
    entry.setDataObject(dataObject);
    return entry;
  }
}
