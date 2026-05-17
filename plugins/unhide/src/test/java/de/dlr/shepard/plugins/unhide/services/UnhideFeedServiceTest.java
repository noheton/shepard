package de.dlr.shepard.plugins.unhide.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.CollectionProperties;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.FeedEntryIO;
import de.dlr.shepard.plugins.unhide.io.FeedIO;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnhideFeedServiceTest {

  private CollectionDAO collectionDAO;
  private CollectionPropertiesDAO collectionPropertiesDAO;
  private ActivityDAO activityDAO;
  private PublicationDAO publicationDAO;
  private UnhideFeedService service;

  @BeforeEach
  void setUp() {
    collectionDAO = mock(CollectionDAO.class);
    collectionPropertiesDAO = mock(CollectionPropertiesDAO.class);
    activityDAO = mock(ActivityDAO.class);
    publicationDAO = mock(PublicationDAO.class);
    service = new UnhideFeedService();
    service.collectionDAO = collectionDAO;
    service.collectionPropertiesDAO = collectionPropertiesDAO;
    service.activityDAO = activityDAO;
    service.publicationDAO = publicationDAO;
    // The renderer is pure / @ApplicationScoped — use the real
    // implementation so the test exercises the actual m4i shape
    // (the renderer has its own unit tests; we trust them).
    service.provJsonLdRenderer = new ProvJsonLdRenderer();
    service.provenanceWindow = UnhideFeedService.DEFAULT_PROVENANCE_WINDOW;

    // Safe defaults — most tests don't care about activities /
    // publications and the absence (null returns / empty lists) is
    // the expected baseline.
    when(activityDAO.list(any(), any(), anyString(), any(), any(), anyInt()))
      .thenReturn(List.of());
    when(publicationDAO.findByEntityAppId(anyString())).thenReturn(List.of());
    // UH1d default: no CollectionProperties node → treat as publishToHelmholtzKG=true.
    when(collectionPropertiesDAO.findByCollectionAppId(anyString()))
      .thenReturn(Optional.empty());
  }

  // ─── pagination ──────────────────────────────────────────────────────────

  @Test
  void clampPageSize_belowOne_substitutesDefault() {
    assertEquals(UnhideFeedService.DEFAULT_PAGE_SIZE, UnhideFeedService.clampPageSize(0));
    assertEquals(UnhideFeedService.DEFAULT_PAGE_SIZE, UnhideFeedService.clampPageSize(-1));
  }

  @Test
  void clampPageSize_capsAtMax() {
    assertEquals(UnhideFeedService.MAX_PAGE_SIZE, UnhideFeedService.clampPageSize(1_000_000));
    assertEquals(UnhideFeedService.MAX_PAGE_SIZE, UnhideFeedService.clampPageSize(UnhideFeedService.MAX_PAGE_SIZE + 1));
  }

  @Test
  void clampPageSize_passesThroughInRange() {
    assertEquals(7, UnhideFeedService.clampPageSize(7));
    assertEquals(UnhideFeedService.MAX_PAGE_SIZE, UnhideFeedService.clampPageSize(UnhideFeedService.MAX_PAGE_SIZE));
  }

  // ─── provenance-window clamping (UH1b) ───────────────────────────────────

  @Test
  void clampProvenanceWindow_belowOne_substitutesDefault() {
    assertEquals(UnhideFeedService.DEFAULT_PROVENANCE_WINDOW, UnhideFeedService.clampProvenanceWindow(0));
    assertEquals(UnhideFeedService.DEFAULT_PROVENANCE_WINDOW, UnhideFeedService.clampProvenanceWindow(-3));
  }

  @Test
  void clampProvenanceWindow_capsAtMax() {
    assertEquals(UnhideFeedService.MAX_PROVENANCE_WINDOW, UnhideFeedService.clampProvenanceWindow(1_000));
  }

  @Test
  void clampProvenanceWindow_passesThroughInRange() {
    assertEquals(7, UnhideFeedService.clampProvenanceWindow(7));
  }

  // ─── feed assembly ───────────────────────────────────────────────────────

  @Test
  void buildFeed_returnsEmptyGraph_whenNoCollections() {
    when(collectionDAO.findAll()).thenReturn(List.of());
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://shepard.example.dlr.de/", 0, 100);

    assertNotNull(feed);
    assertEquals(0, feed.graph().size());
    assertEquals(3, feed.context().size(),
      "context carries schema.org URI, m4i URI, and the inline shepard/dcat/m4i prefix map");
    assertTrue(feed.meta().containsKey("totalEntries"));
    assertEquals(0, feed.meta().get("totalEntries"));
    assertEquals(0, feed.meta().get("totalPages"));
  }

  @Test
  void buildFeed_projectsCollectionsOntoJsonLdEntries() {
    Collection c = newCollection("01HFTEST", "Test campaign", "A test campaign", "alice", "0000-0002-1825-0097");
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setContactEmail("ops@example.dlr.de");

    FeedIO feed = service.buildFeed(cfg, "https://shepard.example.dlr.de", 0, 100);

    assertEquals(1, feed.graph().size());
    FeedEntryIO entry = feed.graph().get(0);
    assertEquals("https://shepard.example.dlr.de/v2/collections/01HFTEST", entry.id());
    assertEquals(List.of("schema:Dataset", "m4i:Dataset"), entry.type());
    assertEquals("Test campaign", entry.name());
    assertEquals("A test campaign", entry.description());
    assertNotNull(entry.creator());

    @SuppressWarnings("unchecked")
    Map<String, Object> creator = (Map<String, Object>) entry.creator();
    assertEquals("schema:Person", creator.get("@type"));
    assertEquals("alice", creator.get("name"));
    assertEquals("https://orcid.org/0000-0002-1825-0097", creator.get("@id"));

    assertEquals("ops@example.dlr.de", feed.meta().get("contactEmail"));
  }

  @Test
  void buildFeed_skipsDeletedCollections() {
    Collection alive = newCollection("01HFLIVE", "Live", "live", "alice", null);
    Collection dead = newCollection("01HFDEAD", "Dead", "dead", "alice", null);
    dead.setDeleted(true);
    when(collectionDAO.findAll()).thenReturn(List.of(alive, dead));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://example", 0, 100);

    assertEquals(1, feed.graph().size());
    assertTrue(feed.graph().get(0).id().endsWith("01HFLIVE"));
  }

  // ─── UH1d — per-Collection publishToHelmholtzKG toggle ───────────────────

  @Test
  void buildFeed_excludesCollection_whenPublishToHelmholtzKGIsFalse() {
    Collection included = newCollection("COL-IN",  "Included",  "d", "alice", null);
    Collection excluded = newCollection("COL-OUT", "Excluded", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(included, excluded));

    // Set the opt-out flag on COL-OUT.
    var propsOut = new CollectionProperties("props-out");
    propsOut.setPublishToHelmholtzKG(false);
    when(collectionPropertiesDAO.findByCollectionAppId("COL-OUT"))
      .thenReturn(Optional.of(propsOut));

    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    assertEquals(1, feed.graph().size(),
      "COL-OUT with publishToHelmholtzKG=false must be excluded from the feed");
    assertTrue(feed.graph().get(0).id().endsWith("COL-IN"),
      "only the opted-in Collection should appear");
  }

  @Test
  void buildFeed_includesCollection_whenPropertiesNodeAbsent() {
    // Legacy Collection — no properties node at all.
    Collection c = newCollection("COL-LEGACY", "Legacy", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    // collectionPropertiesDAO returns Optional.empty() by default (setUp stub).

    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    assertEquals(1, feed.graph().size(),
      "Collection with no properties node must default to included (publishToHelmholtzKG=true)");
  }

  @Test
  void buildFeed_includesCollection_whenPublishToHelmholtzKGIsTrue() {
    Collection c = newCollection("COL-EXPLICIT-TRUE", "Explicit true", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    var props = new CollectionProperties("props-true");
    props.setPublishToHelmholtzKG(true);
    when(collectionPropertiesDAO.findByCollectionAppId("COL-EXPLICIT-TRUE"))
      .thenReturn(Optional.of(props));

    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    assertEquals(1, feed.graph().size(),
      "Collection with publishToHelmholtzKG=true must appear in the feed");
  }

  @Test
  void buildFeed_paginationTotals_reflectFilteredCount() {
    // Two Collections: one in, one out. totalEntries should count only included ones.
    Collection included = newCollection("COL-IN2",  "In2",  "d", "alice", null);
    Collection excluded = newCollection("COL-OUT2", "Out2", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(included, excluded));

    var propsOut = new CollectionProperties("props-out2");
    propsOut.setPublishToHelmholtzKG(false);
    when(collectionPropertiesDAO.findByCollectionAppId("COL-OUT2"))
      .thenReturn(Optional.of(propsOut));

    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    assertEquals(1, feed.meta().get("totalEntries"),
      "totalEntries must count only feed-eligible Collections");
    assertEquals(1, feed.meta().get("totalPages"));
  }

  @Test
  void buildFeed_paginatesAcrossPages() {
    List<Collection> many = new ArrayList<>();
    long t = 1_700_000_000_000L;
    for (int i = 0; i < 7; i++) {
      Collection c = newCollection("appid-" + i, "C" + i, "d" + i, "alice", null);
      c.setCreatedAt(new Date(t + i)); // stable order
      many.add(c);
    }
    when(collectionDAO.findAll()).thenReturn(many);
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO p0 = service.buildFeed(cfg, "https://e", 0, 3);
    FeedIO p1 = service.buildFeed(cfg, "https://e", 1, 3);
    FeedIO p2 = service.buildFeed(cfg, "https://e", 2, 3);

    assertEquals(3, p0.graph().size());
    assertEquals(3, p1.graph().size());
    assertEquals(1, p2.graph().size(), "last page has the remainder");
    assertEquals(3, p0.meta().get("totalPages"));
    assertEquals(7, p0.meta().get("totalEntries"));
    // Disjoint windows.
    assertTrue(p0.graph().get(0).id().endsWith("appid-0"));
    assertTrue(p1.graph().get(0).id().endsWith("appid-3"));
    assertTrue(p2.graph().get(0).id().endsWith("appid-6"));
  }

  @Test
  void buildFeed_negativePage_clampsToZero() {
    when(collectionDAO.findAll()).thenReturn(List.of());
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://e", -5, 100);

    assertEquals(0, feed.meta().get("page"));
  }

  @Test
  void buildFeed_pageBeyondTotal_returnsEmptyWindow() {
    Collection c = newCollection("only", "only", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://e", 99, 100);

    assertEquals(0, feed.graph().size());
    assertEquals(1, feed.meta().get("totalEntries"));
  }

  @Test
  void buildFeed_omitsContactEmail_whenBlank() {
    when(collectionDAO.findAll()).thenReturn(List.of());
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setContactEmail("  ");

    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    assertNull(feed.meta().get("contactEmail"));
  }

  // ─── UH1b — m4i:hasProcessingStep ────────────────────────────────────────

  @Test
  void buildFeed_emitsHasProcessingStepArray_whenActivitiesExist() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    when(activityDAO.list(any(), any(), eq("01HFTEST"), any(), any(), anyInt()))
      .thenReturn(List.of(
        newActivity("a-1", "CREATE", "alice", "Collection", "01HFTEST", 1L),
        newActivity("a-2", "UPDATE", "bob",   "Collection", "01HFTEST", 2L),
        newActivity("a-3", "READ",   "carol", "Collection", "01HFTEST", 3L)
      ));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertNotNull(entry.hasProcessingStep(), "feed entry should carry m4i:hasProcessingStep when activities exist");
    assertEquals(3, entry.hasProcessingStep().size());
    // First node is structurally PROV-O + m4i typed.
    @SuppressWarnings("unchecked")
    Map<String, Object> firstNode = (Map<String, Object>) entry.hasProcessingStep().get(0);
    assertNotNull(firstNode.get("@id"));
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) firstNode.get("@type");
    assertTrue(types.contains("m4i:ProcessingStep"),
      "activity body should carry m4i:ProcessingStep type per PROV1h m4i profile");
    assertTrue(types.contains("prov:Activity"),
      "activity body should retain prov:Activity parent type so PROV-O-only readers parse it");
  }

  @Test
  void buildFeed_omitsHasProcessingStep_whenNoActivities() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    // activityDAO.list default-stub returns empty list

    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertNull(entry.hasProcessingStep(),
      "feed entry must omit (not empty-array) m4i:hasProcessingStep when no activities exist — JSON-LD 'absent' semantics");
  }

  @Test
  void buildFeed_respectsProvenanceWindow_size() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    // Eight activities exist but only 5 should be returned given window=5.
    List<Activity> eight = new ArrayList<>();
    for (int i = 0; i < 8; i++) eight.add(newActivity("a-" + i, "CREATE", "alice", "Collection", "01HFTEST", (long) i));
    // The DAO honours the limit param, so simulate the cap here —
    // the service passes the window through unchanged.
    when(activityDAO.list(any(), any(), eq("01HFTEST"), any(), any(), eq(5)))
      .thenReturn(eight.subList(0, 5));
    service.provenanceWindow = 5;
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertEquals(5, entry.hasProcessingStep().size(),
      "window=5 + 8 activities → 5 most-recent embedded");
    verify(activityDAO).list(any(), any(), eq("01HFTEST"), any(), any(), eq(5));
  }

  @Test
  void buildFeed_provenanceFetchFailure_omitsField() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    when(activityDAO.list(any(), any(), eq("01HFTEST"), any(), any(), anyInt()))
      .thenThrow(new RuntimeException("simulated DB hiccup"));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertNull(entry.hasProcessingStep(),
      "provenance fetch hiccup on one Collection must not poison the page — field omits gracefully");
  }

  // ─── UH1c — KIP citation ─────────────────────────────────────────────────

  @Test
  void buildFeed_emitsKipCitation_whenPublicationExists() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    Publication pub = newPublication("mock:shepard:collections:01HFTEST:1700000000", 1_700_000_000_000L);
    when(publicationDAO.findByEntityAppId("01HFTEST")).thenReturn(List.of(pub));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://shepard.example.dlr.de", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertNotNull(entry.schemaIdentifier(),
      "feed entry should carry schema:identifier when a Publication exists");
    @SuppressWarnings("unchecked")
    Map<String, Object> ident = (Map<String, Object>) entry.schemaIdentifier();
    assertEquals("PropertyValue", ident.get("@type"));
    assertEquals("pid", ident.get("propertyID"));
    assertEquals("mock:shepard:collections:01HFTEST:1700000000", ident.get("value"));
    assertEquals(
      "https://shepard.example.dlr.de/v2/.well-known/kip/mock:shepard:collections:01HFTEST:1700000000",
      entry.schemaUrl()
    );
    assertEquals("mock:shepard:collections:01HFTEST:1700000000", entry.m4iHasIdentifier());
  }

  @Test
  void buildFeed_kipCitation_picksMostRecentPublication() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    Publication stale1  = newPublication("pid-stale-1",  1_700_000_000_000L);
    Publication stale2  = newPublication("pid-stale-2",  1_700_000_001_000L);
    Publication current = newPublication("pid-current",  1_700_000_002_000L);
    // Return in arbitrary order — service must pick the max(mintedAt).
    when(publicationDAO.findByEntityAppId("01HFTEST")).thenReturn(List.of(stale1, current, stale2));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertEquals("pid-current", entry.m4iHasIdentifier(),
      "three publications → most-recent mintedAt is the cited KIP record");
    @SuppressWarnings("unchecked")
    Map<String, Object> ident = (Map<String, Object>) entry.schemaIdentifier();
    assertEquals("pid-current", ident.get("value"));
    assertTrue(entry.schemaUrl().endsWith("/v2/.well-known/kip/pid-current"));
  }

  @Test
  void buildFeed_omitsKipCitation_whenNoPublication() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    // publicationDAO.findByEntityAppId default-stub returns empty.

    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertNull(entry.schemaIdentifier(),
      "schema:identifier omitted when no Publication exists — absent-field, not null");
    assertNull(entry.schemaUrl(),
      "schema:url omitted when no Publication exists");
    assertNull(entry.m4iHasIdentifier(),
      "m4i:hasIdentifier omitted when no Publication exists");
  }

  @Test
  void buildFeed_kipFetchFailure_omitsFields() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    when(publicationDAO.findByEntityAppId("01HFTEST")).thenThrow(new RuntimeException("simulated DB hiccup"));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertNull(entry.schemaIdentifier(),
      "publication fetch hiccup on one Collection must not poison the page — field omits gracefully");
    assertNull(entry.m4iHasIdentifier());
  }

  @Test
  void buildFeed_skipsPublicationWithBlankPid() {
    Collection c = newCollection("01HFTEST", "Test", "d", "alice", null);
    when(collectionDAO.findAll()).thenReturn(List.of(c));
    Publication blank = new Publication();
    blank.setMintedAt(1L);
    blank.setPid("   ");
    when(publicationDAO.findByEntityAppId("01HFTEST")).thenReturn(List.of(blank));
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);

    FeedIO feed = service.buildFeed(cfg, "https://e", 0, 100);

    FeedEntryIO entry = feed.graph().get(0);
    assertNull(entry.schemaIdentifier(),
      "a Publication with a blank PID is a programming error from KIP1a — omit the citation rather than emit garbage");
  }

  // ─── UH1b / UH1c — null-guard branches ───────────────────────────────────

  @Test
  void buildProcessingSteps_nullAppId_yieldsNull() {
    assertNull(service.buildProcessingSteps(null),
      "null Collection appId yields no provenance fetch — guard short-circuits");
  }

  @Test
  void buildProcessingSteps_blankAppId_yieldsNull() {
    assertNull(service.buildProcessingSteps("  "),
      "blank Collection appId yields no provenance fetch — guard short-circuits");
  }

  @Test
  void buildKipCitation_nullAppId_yieldsNull() {
    assertNull(service.buildKipCitation(null, "https://e"),
      "null Collection appId yields no publication fetch — guard short-circuits");
  }

  @Test
  void buildKipCitation_blankAppId_yieldsNull() {
    assertNull(service.buildKipCitation("", "https://e"),
      "blank Collection appId yields no publication fetch — guard short-circuits");
  }

  @Test
  void buildProcessingSteps_daoReturnsNull_yieldsNull() {
    // ActivityDAO contract says non-null but defence-in-depth: a future
    // adapter that returns null must not NPE the page.
    when(activityDAO.list(any(), any(), eq("01HFTEST"), any(), any(), anyInt())).thenReturn(null);
    assertNull(service.buildProcessingSteps("01HFTEST"),
      "DAO null return must be treated as empty (no provenance) rather than NPE");
  }

  @Test
  void buildKipCitation_publicationWithNullMintedAt_stillCited() {
    // A Publication with a null mintedAt (legacy data, KIP1c migration
    // gap) should still cite if the PID is present — the max() picks
    // it via the 0L fallback.
    Publication pub = new Publication();
    pub.setPid("legacy-pid");
    // mintedAt deliberately null
    when(publicationDAO.findByEntityAppId("01HFTEST")).thenReturn(List.of(pub));
    UnhideFeedService.KipCitation kip = service.buildKipCitation("01HFTEST", "https://e");
    assertNotNull(kip);
    assertEquals("legacy-pid", kip.m4iHasIdentifier);
  }

  // ─── creator-of helper ───────────────────────────────────────────────────

  @Test
  void creatorOf_returnsNull_forNullUser() {
    assertNull(UnhideFeedService.creatorOf(null));
  }

  @Test
  void creatorOf_usesDisplayName_whenPresent() {
    User u = new User();
    u.setUsername("alice");
    u.setDisplayName("Alice the Researcher");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) UnhideFeedService.creatorOf(u);
    assertEquals("Alice the Researcher", result.get("name"));
  }

  @Test
  void creatorOf_usesFirstLast_whenNoDisplayName() {
    User u = new User();
    u.setUsername("alice");
    u.setFirstName("Alice");
    u.setLastName("Researcher");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) UnhideFeedService.creatorOf(u);
    assertEquals("Alice Researcher", result.get("name"));
  }

  @Test
  void creatorOf_fallsBackToUsername() {
    User u = new User();
    u.setUsername("alice");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) UnhideFeedService.creatorOf(u);
    assertEquals("alice", result.get("name"));
  }

  @Test
  void creatorOf_emitsOrcid_whenPresent() {
    User u = new User();
    u.setUsername("alice");
    u.setOrcid("0000-0002-1825-0097");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) UnhideFeedService.creatorOf(u);
    assertEquals("https://orcid.org/0000-0002-1825-0097", result.get("@id"));
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static Collection newCollection(String appId, String name, String description, String username, String orcid) {
    Collection c = new Collection();
    c.setAppId(appId);
    c.setName(name);
    c.setDescription(description);
    c.setCreatedAt(new Date(1_700_000_000_000L));
    c.setUpdatedAt(new Date(1_700_000_100_000L));
    User u = new User();
    u.setUsername(username);
    if (orcid != null) u.setOrcid(orcid);
    c.setCreatedBy(u);
    return c;
  }

  private static Activity newActivity(String appId, String kind, String agent, String tKind, String tAppId, long startedAtMillis) {
    Activity a = new Activity();
    a.setAppId(appId);
    a.setActionKind(kind);
    a.setAgentUsername(agent);
    a.setTargetKind(tKind);
    a.setTargetAppId(tAppId);
    a.setStartedAtMillis(startedAtMillis);
    a.setEndedAtMillis(startedAtMillis + 100L);
    a.setSummary("test " + kind);
    a.setOriginInstance("local");
    return a;
  }

  private static Publication newPublication(String pid, long mintedAtMillis) {
    Publication p = new Publication();
    p.setPid(pid);
    p.setMintedAt(mintedAtMillis);
    p.setMinterId("mock");
    p.setEntityKind("collections");
    p.setEntityAppId("01HFTEST");
    return p;
  }

}
