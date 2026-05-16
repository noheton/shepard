package de.dlr.shepard.v2.labjournal.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.services.LabJournalRenderService;
import de.dlr.shepard.v2.labjournal.io.LabJournalRenderIO;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * J1a — Mockito unit tests for {@link LabJournalRenderRest}.
 *
 * <p>No CDI container or network required — fields are injected directly
 * (matching the pattern from {@code TimeseriesAnnotationRestTest}).
 */
class LabJournalRenderRestTest {

  static final String APP_ID = "01957000-0000-7000-8000-000000000001";
  static final long DO_OGM_ID = 42L;
  static final String CALLER = "alice";
  static final String MARKDOWN = "**hello**";

  @Mock
  LabJournalEntryDAO labJournalEntryDAO;

  @Mock
  LabJournalRenderService renderService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  LabJournalRenderRest resource;
  DataObject dataObject;
  LabJournalEntry entry;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    resource = new LabJournalRenderRest();
    resource.labJournalEntryDAO = labJournalEntryDAO;
    resource.renderService = renderService;
    resource.permissionsService = permissionsService;

    dataObject = new DataObject();
    dataObject.setId(DO_OGM_ID);

    entry = new LabJournalEntry();
    entry.setAppId(APP_ID);
    entry.setContent(MARKDOWN);
    entry.setDataObject(dataObject);

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(labJournalEntryDAO.findByAppId(APP_ID)).thenReturn(entry);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(true);
    when(renderService.renderToHtml(MARKDOWN)).thenReturn("<p><strong>hello</strong></p>\n");
  }

  @Test
  void render_returns200WithHtmlBody_forKnownEntry() {
    var r = resource.render(APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (LabJournalRenderIO) r.getEntity();
    assertThat(io.getHtml()).contains("<strong>hello</strong>");
    assertThat(io.getSourceLength()).isEqualTo(MARKDOWN.length());
  }

  @Test
  void render_returns404_forUnknownAppId() {
    when(labJournalEntryDAO.findByAppId("unknown")).thenReturn(null);
    assertThat(resource.render("unknown", sc).getStatus()).isEqualTo(404);
  }

  @Test
  void render_returns404_forDeletedEntry() {
    entry.setDeleted(true);
    assertThat(resource.render(APP_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void render_returns403_whenCallerLacksReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(false);
    assertThat(resource.render(APP_ID, sc).getStatus()).isEqualTo(403);
  }

  @Test
  void render_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.render(APP_ID, sc).getStatus()).isEqualTo(401);
  }
}
