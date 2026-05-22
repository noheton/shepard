package de.dlr.shepard.plugins.v1compat.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.v1compat.daos.LegacyV1ConfigDAO;
import de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config;
import jakarta.enterprise.context.control.RequestContextController;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — exercises {@link LegacyV1ConfigService} without
 * booting Quarkus or a Neo4j testcontainer. Mirrors the
 * UH1a precedent ({@code UnhideConfigServiceTest}) — service-level
 * invariants only; the migration / integration coverage lands later.
 *
 * <p>Coverage targets:
 *
 * <ul>
 *   <li>seed-if-needed is idempotent and respects the deploy default</li>
 *   <li>hot-path {@code isEnabled()} reads through cache; refreshes
 *       on TTL expiry</li>
 *   <li>cache fail-open: DAO failures don't 410-storm callers</li>
 *   <li>{@code setEnabled} flips the row, invalidates the cache,
 *       and writes an updatedAt/updatedBy audit pair</li>
 *   <li>{@code current()} seeds on demand when the DB row is missing</li>
 * </ul>
 */
class LegacyV1ConfigServiceTest {

  private LegacyV1ConfigDAO dao;
  private RequestContextController contextController;
  private AtomicLong clock;
  private LegacyV1ConfigService service;

  @BeforeEach
  void setUp() {
    dao = mock(LegacyV1ConfigDAO.class);
    // createOrUpdate returns its argument verbatim so post-save state
    // assertions don't require a real OGM session.
    when(dao.createOrUpdate(any(LegacyV1Config.class))).thenAnswer(inv -> inv.getArgument(0));

    contextController = mock(RequestContextController.class);
    // RequestContextController.activate may legitimately return true
    // or false in production; either is fine for the unit-level path.
    when(contextController.activate()).thenReturn(false);

    clock = new AtomicLong(1_700_000_000_000L);
    service = new LegacyV1ConfigService(dao, contextController, true, clock::get);
  }

  // ─── seed-on-first-start ─────────────────────────────────────────────────

  @Test
  void seedIfNeeded_createsSingletonWithDeployDefault_whenAbsent() {
    when(dao.findSingleton()).thenReturn(null);

    LegacyV1Config seeded = service.seedIfNeeded();

    assertNotNull(seeded);
    assertTrue(seeded.isEnabled(), "deploy default true seeds enabled=true");
    assertEquals(1_700_000_000_000L, seeded.getCreatedAt());
    assertEquals(1_700_000_000_000L, seeded.getUpdatedAt());
    verify(dao).createOrUpdate(any(LegacyV1Config.class));
  }

  @Test
  void seedIfNeeded_idempotent_returnsExistingRow() {
    LegacyV1Config existing = new LegacyV1Config();
    existing.setAppId("01HF-EXISTING");
    existing.setEnabled(false);
    when(dao.findSingleton()).thenReturn(existing);

    LegacyV1Config result = service.seedIfNeeded();

    assertEquals("01HF-EXISTING", result.getAppId());
    assertFalse(result.isEnabled(), "existing enabled preserved verbatim");
    verify(dao, never()).createOrUpdate(any(LegacyV1Config.class));
  }

  @Test
  void seedIfNeeded_respectsDeployDefaultFalse() {
    service = new LegacyV1ConfigService(dao, contextController, false, clock::get);
    when(dao.findSingleton()).thenReturn(null);

    LegacyV1Config seeded = service.seedIfNeeded();

    assertFalse(seeded.isEnabled(), "deploy default false seeds enabled=false");
  }

  // ─── hot-path read + cache ─────────────────────────────────────────────────

  @Test
  void isEnabled_cachesFirstRead_DAOnotHitAgainWithinTTL() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    when(dao.findSingleton()).thenReturn(row);

    assertTrue(service.isEnabled(), "first read populates cache");
    assertTrue(service.isEnabled(), "second read hits cache");
    assertTrue(service.isEnabled(), "third read still hits cache");

    verify(dao, times(1)).findSingleton();
  }

  @Test
  void isEnabled_refreshesAfterTTL() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    when(dao.findSingleton()).thenReturn(row);

    assertTrue(service.isEnabled(), "first read");
    verify(dao, times(1)).findSingleton();

    // Advance clock past 5 s TTL
    clock.addAndGet(LegacyV1ConfigService.CACHE_TTL_MILLIS + 1_000L);

    // Mid-flight row mutation
    row.setEnabled(false);
    when(dao.findSingleton()).thenReturn(row);

    assertFalse(service.isEnabled(), "post-TTL read picks up flip");
    verify(dao, times(2)).findSingleton();
  }

  @Test
  void isEnabled_failOpenOnDaoException() {
    when(dao.findSingleton()).thenThrow(new RuntimeException("simulated Neo4j hiccup"));

    // Deploy default is true; the gate filter should NOT 410-storm
    // legitimate callers because the DB is briefly unreachable.
    assertTrue(service.isEnabled(), "fail-open: DB error ⇒ deploy default wins");
  }

  @Test
  void isEnabled_returnsDeployDefaultWhenNoRowExistsYet() {
    when(dao.findSingleton()).thenReturn(null);

    assertTrue(service.isEnabled(), "no row yet ⇒ deploy default true");
  }

  @Test
  void refreshCache_forcesDaoRead() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    when(dao.findSingleton()).thenReturn(row);

    service.isEnabled();
    service.refreshCache();
    service.isEnabled();

    // 2 calls: one from the initial isEnabled, one from refreshCache.
    // The second isEnabled hits the freshly-populated cache.
    verify(dao, times(2)).findSingleton();
  }

  // ─── setEnabled (admin PATCH path) ─────────────────────────────────────────

  @Test
  void setEnabled_flipsRow_invalidatesCache_stampsAudit() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    row.setAppId("01HF-AAA");
    when(dao.findSingleton()).thenReturn(row);

    // Warm the cache with the pre-PATCH value (1st DAO read)
    assertTrue(service.isEnabled());

    clock.addAndGet(1_000L);
    // setEnabled calls current() → findSingleton (2nd DAO read), then writes
    LegacyV1Config patched = service.setEnabled(false, "admin@example");

    assertFalse(patched.isEnabled());
    assertEquals(1_700_000_001_000L, patched.getUpdatedAt());
    assertEquals("admin@example", patched.getUpdatedBy());
    verify(dao).createOrUpdate(any(LegacyV1Config.class));

    // Cache invalidation contract: next isEnabled() refreshes (3rd DAO read).
    // Counts the cache-invalidation path explicitly: had setEnabled NOT
    // invalidated the cache, the post-PATCH isEnabled() would have hit the
    // stale cache and findSingleton would still show 2 calls.
    service.isEnabled();
    verify(dao, times(3)).findSingleton();
  }

  @Test
  void setEnabled_noOpWhenAlreadyAtValue() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    when(dao.findSingleton()).thenReturn(row);

    LegacyV1Config result = service.setEnabled(true, "admin@example");

    assertTrue(result.isEnabled());
    // No DB write because the value didn't change.
    verify(dao, never()).createOrUpdate(any(LegacyV1Config.class));
    assertNull(result.getUpdatedBy(), "no audit stamp on a no-op patch");
  }

  @Test
  void setEnabled_seedsRowIfMissing_thenApplies() {
    when(dao.findSingleton()).thenReturn(null);

    LegacyV1Config result = service.setEnabled(false, "admin@example");

    assertFalse(result.isEnabled());
    assertEquals("admin@example", result.getUpdatedBy());
    // Two writes: one for the implicit seed (createdAt set) and one for the
    // setEnabled flip. The exact wire shape is service-internal; we assert
    // the public outcome.
    verify(dao, times(2)).createOrUpdate(any(LegacyV1Config.class));
  }

  // ─── current() (admin GET path) ────────────────────────────────────────────

  @Test
  void current_returnsExistingRow_whenPresent() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    row.setAppId("01HF-AAA");
    when(dao.findSingleton()).thenReturn(row);

    LegacyV1Config result = service.current();

    assertEquals("01HF-AAA", result.getAppId());
    assertTrue(result.isEnabled());
    // current() should NOT trigger a write when the row already exists.
    verify(dao, never()).createOrUpdate(any(LegacyV1Config.class));
  }

  @Test
  void current_seedsOnDemand_whenAbsent() {
    when(dao.findSingleton()).thenReturn(null);

    LegacyV1Config result = service.current();

    assertNotNull(result);
    assertTrue(result.isEnabled(), "post-seed reflects deploy default true");
    verify(dao).createOrUpdate(any(LegacyV1Config.class));
  }

  @Test
  void invalidateCache_forcesNextReadToRefresh() {
    LegacyV1Config row = new LegacyV1Config();
    row.setEnabled(true);
    when(dao.findSingleton()).thenReturn(row);

    service.isEnabled();
    verify(dao, times(1)).findSingleton();

    service.invalidateCache();
    service.isEnabled();
    verify(dao, times(2)).findSingleton();
  }
}
