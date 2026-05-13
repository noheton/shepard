package de.dlr.shepard.plugins.unhide.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.FeedEntryIO;
import de.dlr.shepard.plugins.unhide.io.FeedIO;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnhideFeedServiceTest {

  private CollectionDAO collectionDAO;
  private UnhideFeedService service;

  @BeforeEach
  void setUp() {
    collectionDAO = mock(CollectionDAO.class);
    service = new UnhideFeedService();
    service.collectionDAO = collectionDAO;
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
}
