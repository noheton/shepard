package de.dlr.shepard.provenance.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.MirroredUserDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.MirroredUser;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.MirroredUserEnrichmentCache;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.admin.provenance.services.ProvenanceConfigService;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PROV-USER-ENRICH — unit tests for the
 * {@link ProvenanceCaptureFilter#resolveMirroredUserAppId} enrichment path.
 *
 * <p>All tests operate at the Mockito level; no Quarkus boot required.
 * Focus areas:
 * <ul>
 *   <li>Header present → {@code :MirroredUser} upserted, appId returned</li>
 *   <li>Header absent → no side-effects</li>
 *   <li>Header present but DAO fails → {@code null} returned (best-effort)</li>
 *   <li>Duplicate header calls → cache prevents duplicate DB writes (idempotent)</li>
 *   <li>Local-user backfill → blank firstName/lastName enriched from display name</li>
 *   <li>Local-user backfill → already-populated names not overwritten</li>
 * </ul>
 */
class MirroredUserLookupTest {

  @Mock
  MirroredUserDAO mirroredUserDAO;

  @Mock
  UserDAO userDAO;

  @Mock
  ContainerRequestContext request;

  ProvenanceCaptureFilter filter;
  MirroredUserEnrichmentCache cache;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    cache = new MirroredUserEnrichmentCache();

    ProvenanceConfigService provenanceConfigService = mock(ProvenanceConfigService.class);
    when(provenanceConfigService.effectiveEnabled()).thenReturn(true);
    when(provenanceConfigService.effectiveCaptureReads()).thenReturn(false);

    filter = new ProvenanceCaptureFilter();
    filter.provenance = mock(ProvenanceService.class);
    filter.provenanceConfigService = provenanceConfigService;
    filter.mirroredUserDAO = mirroredUserDAO;
    filter.userDAO = userDAO;
    filter.enrichmentCache = cache;
    filter.targetEntityResolver = mock(TargetEntityResolver.class);

    // Default: no X-Source-User-* headers → all return null.
    when(request.getHeaderString(any())).thenReturn(null);
  }

  // ─── Header present → MirroredUser upserted ──────────────────────────────

  @Test
  void headerPresent_upsertsMirroredUser_returnsAppId() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME))
      .thenReturn("fkrebs");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE))
      .thenReturn("https://cube3.dlr.de");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_DISPLAY_NAME))
      .thenReturn("Florian Krebs");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_EMAIL))
      .thenReturn("flo@dlr.de");

    MirroredUser saved = mirroredUser("mu-001", "https://cube3.dlr.de", "fkrebs");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any())).thenReturn(saved);

    String appId = filter.resolveMirroredUserAppId(request, "fkrebs");

    assertEquals("mu-001", appId);
    verify(mirroredUserDAO, times(1)).createOrUpdateBySourceKey(any());
  }

  @Test
  void headerPresent_upsertsWithCorrectFields() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME))
      .thenReturn("jdoe");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE))
      .thenReturn("https://nuclide.local");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_DISPLAY_NAME))
      .thenReturn("John Doe");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_EMAIL))
      .thenReturn("jdoe@example.com");

    MirroredUser saved = mirroredUser("mu-002", "https://nuclide.local", "jdoe");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any())).thenReturn(saved);

    filter.resolveMirroredUserAppId(request, "jdoe");

    ArgumentCaptor<MirroredUser> captor = ArgumentCaptor.forClass(MirroredUser.class);
    verify(mirroredUserDAO).createOrUpdateBySourceKey(captor.capture());
    MirroredUser passed = captor.getValue();
    assertEquals("https://nuclide.local", passed.getSourceInstance());
    assertEquals("jdoe", passed.getSourceUsername());
    assertEquals("John Doe", passed.getSourceDisplayName());
    assertEquals("jdoe@example.com", passed.getSourceEmail());
  }

  // ─── Header absent → no side-effects ─────────────────────────────────────

  @Test
  void headerAbsent_noSideEffects_returnsNull() {
    // HDR_SOURCE_USERNAME is null (default mock)
    String appId = filter.resolveMirroredUserAppId(request, "alice");

    assertNull(appId);
    verify(mirroredUserDAO, never()).createOrUpdateBySourceKey(any());
    verify(userDAO, never()).createOrUpdate(any());
  }

  @Test
  void headerBlank_noSideEffects_returnsNull() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME))
      .thenReturn("   ");

    String appId = filter.resolveMirroredUserAppId(request, "alice");

    assertNull(appId);
    verify(mirroredUserDAO, never()).createOrUpdateBySourceKey(any());
  }

  // ─── DAO failure → best-effort: null returned, request not blocked ────────

  @Test
  void daoFails_returnsNull_doesNotThrow() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME))
      .thenReturn("fkrebs");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE))
      .thenReturn("https://cube3.dlr.de");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any()))
      .thenThrow(new RuntimeException("Neo4j unavailable"));

    // Must not throw — provenance is observability, not contract.
    String appId = filter.resolveMirroredUserAppId(request, "fkrebs");

    assertNull(appId);
  }

  // ─── Duplicate calls → cache prevents duplicate DB writes ────────────────

  @Test
  void duplicateHeaders_cacheHit_onlyOneDbWrite() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME))
      .thenReturn("fkrebs");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE))
      .thenReturn("https://cube3.dlr.de");

    MirroredUser saved = mirroredUser("mu-001", "https://cube3.dlr.de", "fkrebs");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any())).thenReturn(saved);

    // First call — cache miss, DB write.
    String first = filter.resolveMirroredUserAppId(request, "fkrebs");
    // Second call — cache hit, no additional DB write.
    String second = filter.resolveMirroredUserAppId(request, "fkrebs");

    assertEquals("mu-001", first);
    assertEquals("mu-001", second);
    verify(mirroredUserDAO, times(1)).createOrUpdateBySourceKey(any()); // exactly once
  }

  @Test
  void differentSourceUsers_eachGetOwnDbWrite() {
    MirroredUser u1 = mirroredUser("mu-001", "https://cube3.dlr.de", "fkrebs");
    MirroredUser u2 = mirroredUser("mu-002", "https://cube3.dlr.de", "jdoe");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any()))
      .thenReturn(u1)
      .thenReturn(u2);

    // First user
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn("fkrebs");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE)).thenReturn("https://cube3.dlr.de");
    String appId1 = filter.resolveMirroredUserAppId(request, "fkrebs");

    // Second user (different request object in practice; reuse mock with different stub)
    ContainerRequestContext request2 = mock(ContainerRequestContext.class);
    when(request2.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn("jdoe");
    when(request2.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE)).thenReturn("https://cube3.dlr.de");
    String appId2 = filter.resolveMirroredUserAppId(request2, "jdoe");

    assertEquals("mu-001", appId1);
    assertEquals("mu-002", appId2);
    verify(mirroredUserDAO, times(2)).createOrUpdateBySourceKey(any());
  }

  // ─── Missing sourceInstance header → defaults to "unknown" ──────────────

  @Test
  void missingSourceInstance_defaultsToUnknown() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn("fkrebs");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE)).thenReturn(null);

    MirroredUser saved = mirroredUser("mu-003", "unknown", "fkrebs");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any())).thenReturn(saved);

    String appId = filter.resolveMirroredUserAppId(request, "fkrebs");

    assertEquals("mu-003", appId);
    ArgumentCaptor<MirroredUser> captor = ArgumentCaptor.forClass(MirroredUser.class);
    verify(mirroredUserDAO).createOrUpdateBySourceKey(captor.capture());
    assertEquals("unknown", captor.getValue().getSourceInstance());
  }

  // ─── Local-user backfill ─────────────────────────────────────────────────

  @Test
  void backfill_populatesBlankFirstLastName() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn("kreb_fl");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE)).thenReturn("https://cube3.dlr.de");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_DISPLAY_NAME)).thenReturn("Florian Krebs");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_EMAIL)).thenReturn("flo@dlr.de");

    MirroredUser saved = mirroredUser("mu-004", "https://cube3.dlr.de", "kreb_fl");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any())).thenReturn(saved);

    User localUser = new User("kreb_fl"); // blank firstName/lastName from constructor
    when(userDAO.find("kreb_fl")).thenReturn(localUser);

    filter.resolveMirroredUserAppId(request, "kreb_fl");

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userDAO).createOrUpdate(userCaptor.capture());
    User updated = userCaptor.getValue();
    assertEquals("Florian", updated.getFirstName());
    assertEquals("Krebs",   updated.getLastName());
    assertEquals("flo@dlr.de", updated.getEmail());
  }

  @Test
  void backfill_doesNotOverwriteExistingNames() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn("fkrebs");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE)).thenReturn("https://cube3.dlr.de");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_DISPLAY_NAME)).thenReturn("Should Not Override");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_EMAIL)).thenReturn("other@dlr.de");

    MirroredUser saved = mirroredUser("mu-005", "https://cube3.dlr.de", "fkrebs");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any())).thenReturn(saved);

    User localUser = new User("fkrebs", "Florian", "Krebs", "florian@dlr.de"); // already populated
    when(userDAO.find("fkrebs")).thenReturn(localUser);

    filter.resolveMirroredUserAppId(request, "fkrebs");

    // userDAO.createOrUpdate must NOT be called — nothing to backfill.
    verify(userDAO, never()).createOrUpdate(any());
  }

  @Test
  void backfill_userNotInDb_noOp() {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn("ghost");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE)).thenReturn("https://cube3.dlr.de");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_DISPLAY_NAME)).thenReturn("Ghost User");

    MirroredUser saved = mirroredUser("mu-006", "https://cube3.dlr.de", "ghost");
    when(mirroredUserDAO.createOrUpdateBySourceKey(any())).thenReturn(saved);
    when(userDAO.find("ghost")).thenReturn(null); // not in DB

    // Must not throw.
    String appId = filter.resolveMirroredUserAppId(request, "ghost");

    assertEquals("mu-006", appId);
    verify(userDAO, never()).createOrUpdate(any());
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static MirroredUser mirroredUser(String appId, String instance, String username) {
    MirroredUser u = new MirroredUser();
    u.setAppId(appId);
    u.setSourceInstance(instance);
    u.setSourceUsername(username);
    return u;
  }
}
