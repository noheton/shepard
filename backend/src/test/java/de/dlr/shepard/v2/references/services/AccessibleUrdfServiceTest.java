package de.dlr.shepard.v2.references.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO.UrdfCandidate;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.references.io.AccessibleUrdfIO;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * URDF-FILEREF-PICKER-SEARCHABLE — plain-Mockito unit tests for
 * {@link AccessibleUrdfService}. Covers the permission narrowing, the {@code q}
 * name filter, pagination, the URDF exclusion predicate, and the fail-soft
 * empty-page contract.
 */
class AccessibleUrdfServiceTest {

  private static final String CALLER = "alice";

  @Mock
  SingletonFileReferenceDAO dao;

  @Mock
  PermissionsService permissionsService;

  AccessibleUrdfService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new AccessibleUrdfService();
    service.singletonFileReferenceDAO = dao;
    service.permissionsService = permissionsService;
  }

  /** kr210-style: fileKind=urdf but the name carries NO .urdf suffix. */
  private UrdfCandidate kr210() {
    return new UrdfCandidate("ref-kr210", "kr210-r2700-urdf", "urdf", "do-A", "coll-A", "MFFD RDK");
  }

  private UrdfCandidate namedUrdf() {
    return new UrdfCandidate("ref-arm", "arm.urdf", null, "do-B", "coll-B", "Arm Lab");
  }

  private void allowAll(List<UrdfCandidate> candidates) {
    when(permissionsService.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(candidates.stream().map(UrdfCandidate::dataObjectAppId).collect(Collectors.toSet()));
  }

  @Test
  void returnsAccessibleUrdfs_matchingByFileKindAndBySuffix() {
    var candidates = List.of(kr210(), namedUrdf());
    when(dao.findAllUrdfCandidates()).thenReturn(candidates);
    allowAll(candidates);

    PagedResponseIO<AccessibleUrdfIO> res = service.listAccessible(CALLER, null, 0, 50);

    assertEquals(2, res.total());
    var appIds = res.items().stream().map(AccessibleUrdfIO::appId).collect(Collectors.toSet());
    assertTrue(appIds.contains("ref-kr210"), "kr210 (fileKind=urdf, no .urdf suffix) must be found");
    assertTrue(appIds.contains("ref-arm"), "arm.urdf (by suffix) must be found");
    // the disambiguating label source travels with each row
    var kr = res.items().stream().filter(i -> i.appId().equals("ref-kr210")).findFirst().orElseThrow();
    assertEquals("MFFD RDK", kr.collectionName());
  }

  @Test
  void excludesReferencesTheCallerCannotRead() {
    var candidates = List.of(kr210(), namedUrdf());
    when(dao.findAllUrdfCandidates()).thenReturn(candidates);
    // only do-A (kr210) is readable
    when(permissionsService.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of("do-A"));

    PagedResponseIO<AccessibleUrdfIO> res = service.listAccessible(CALLER, null, 0, 50);

    assertEquals(1, res.total());
    assertEquals("ref-kr210", res.items().get(0).appId());
  }

  @Test
  void excludesNonUrdfCandidatesLeakedFromTheDao() {
    // Defense-in-depth: even if the DAO leaks a non-URDF row, the service drops it.
    var leaked = new UrdfCandidate("ref-pdf", "report.pdf", "pdf", "do-C", "coll-C", "Docs");
    var candidates = List.of(kr210(), leaked);
    when(dao.findAllUrdfCandidates()).thenReturn(candidates);
    when(permissionsService.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of("do-A", "do-C"));

    PagedResponseIO<AccessibleUrdfIO> res = service.listAccessible(CALLER, null, 0, 50);

    assertEquals(1, res.total());
    assertEquals("ref-kr210", res.items().get(0).appId());
  }

  @Test
  void appliesCaseInsensitiveNameQuery() {
    var candidates = List.of(kr210(), namedUrdf());
    when(dao.findAllUrdfCandidates()).thenReturn(candidates);
    allowAll(candidates);

    PagedResponseIO<AccessibleUrdfIO> res = service.listAccessible(CALLER, "KR210", 0, 50);

    assertEquals(1, res.total());
    assertEquals("ref-kr210", res.items().get(0).appId());
  }

  @Test
  void paginatesTotalReflectsVisibleCount() {
    var a = new UrdfCandidate("r1", "a.urdf", "urdf", "do1", "c", "C");
    var b = new UrdfCandidate("r2", "b.urdf", "urdf", "do2", "c", "C");
    var c = new UrdfCandidate("r3", "c.urdf", "urdf", "do3", "c", "C");
    var candidates = List.of(a, b, c);
    when(dao.findAllUrdfCandidates()).thenReturn(candidates);
    allowAll(candidates);

    PagedResponseIO<AccessibleUrdfIO> page0 = service.listAccessible(CALLER, null, 0, 2);
    assertEquals(3, page0.total());
    assertEquals(2, page0.items().size());

    PagedResponseIO<AccessibleUrdfIO> page1 = service.listAccessible(CALLER, null, 1, 2);
    assertEquals(3, page1.total());
    assertEquals(1, page1.items().size());
  }

  @Test
  void blankCaller_returnsEmpty() {
    PagedResponseIO<AccessibleUrdfIO> res = service.listAccessible("  ", null, 0, 50);
    assertEquals(0, res.total());
    assertTrue(res.items().isEmpty());
  }

  @Test
  void failSoft_onDaoException_returnsEmptyNotThrow() {
    when(dao.findAllUrdfCandidates()).thenThrow(new RuntimeException("neo4j down"));
    PagedResponseIO<AccessibleUrdfIO> res = service.listAccessible(CALLER, null, 0, 50);
    assertEquals(0, res.total());
    assertTrue(res.items().isEmpty());
  }

  @Test
  void urdfPredicate_matchesSuffixOrFileKind_only() {
    assertTrue(AccessibleUrdfService.isUrdfCandidate("robot.urdf", null));
    assertTrue(AccessibleUrdfService.isUrdfCandidate("Robot.URDF", null));
    assertTrue(AccessibleUrdfService.isUrdfCandidate("kr210-r2700-urdf", "urdf"));
    assertFalse(AccessibleUrdfService.isUrdfCandidate("report.pdf", "pdf"));
    assertFalse(AccessibleUrdfService.isUrdfCandidate("urdf-notes.txt", null));
    assertFalse(AccessibleUrdfService.isUrdfCandidate(null, null));
  }
}
