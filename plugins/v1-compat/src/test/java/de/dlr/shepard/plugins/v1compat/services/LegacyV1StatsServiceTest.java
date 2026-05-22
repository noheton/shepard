package de.dlr.shepard.plugins.v1compat.services;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugins.v1compat.io.LegacyV1StatsIO;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — covers the in-memory stats counters service end-to-
 * end. The store is the load-bearing data structure behind
 * {@code /v2/admin/legacy/v1/stats}; the WARN-once-per-pair dedup is
 * the load-bearing decision that keeps the home-showcase MQTT
 * collector from flooding the log.
 */
class LegacyV1StatsServiceTest {

  private AtomicLong clock;
  private LegacyV1StatsService stats;

  @BeforeEach
  void setUp() {
    clock = new AtomicLong(1_700_000_000_000L);
    stats = new LegacyV1StatsService(clock::get);
  }

  @Test
  void recordHit_incrementsAllCounters() {
    stats.recordHit("/shepard/api/collections", "alice");
    stats.recordHit("/shepard/api/collections", "alice");
    stats.recordHit("/shepard/api/dataObjects", "bob");

    LegacyV1StatsIO snap = stats.snapshot();
    assertThat(snap.totalHits()).isEqualTo(3);
    assertThat(snap.byEndpoint())
      .extracting(LegacyV1StatsIO.EndpointCount::pathPattern, LegacyV1StatsIO.EndpointCount::hits)
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("/shepard/api/collections", 2L),
        org.assertj.core.groups.Tuple.tuple("/shepard/api/dataObjects", 1L)
      );
    assertThat(snap.byPrincipal())
      .extracting(LegacyV1StatsIO.PrincipalCount::principalSub, LegacyV1StatsIO.PrincipalCount::hits)
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("alice", 2L),
        org.assertj.core.groups.Tuple.tuple("bob", 1L)
      );
  }

  @Test
  void recordHit_stampsTimestamps() {
    stats.recordHit("/shepard/api/users", "alice");
    Date firstHit1 = stats.snapshot().firstHitAt();
    Date mostRecent1 = stats.snapshot().mostRecentHitAt();
    assertThat(firstHit1).isNotNull();
    assertThat(mostRecent1).isEqualTo(firstHit1);

    clock.addAndGet(5_000L);
    stats.recordHit("/shepard/api/users", "alice");
    LegacyV1StatsIO snap2 = stats.snapshot();
    assertThat(snap2.firstHitAt()).isEqualTo(firstHit1);
    assertThat(snap2.mostRecentHitAt()).isAfter(firstHit1);
  }

  @Test
  void checkAndMarkFirstHit_isOncePerPair() {
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/x", "alice")).isTrue();
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/x", "alice")).isFalse();
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/x", "alice")).isFalse();

    // Different pair → fresh first-hit
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/y", "alice")).isTrue();
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/x", "bob")).isTrue();
  }

  @Test
  void checkAndMarkFirstHit_nullPrincipal_treatedAsAnonymous() {
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/x", null)).isTrue();
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/x", null)).isFalse();
    // "" should hit the same anonymous bucket
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/x", "")).isFalse();
  }

  @Test
  void emptyState_snapshotShape() {
    LegacyV1StatsIO snap = stats.snapshot();
    assertThat(snap.totalHits()).isZero();
    assertThat(snap.byEndpoint()).isEmpty();
    assertThat(snap.byPrincipal()).isEmpty();
    assertThat(snap.firstHitAt()).isNull();
    assertThat(snap.mostRecentHitAt()).isNull();
  }

  @Test
  void snapshot_respectsTopN() {
    for (int i = 0; i < 200; i++) {
      stats.recordHit("/shepard/api/endpoint-" + i, "user-" + i);
    }

    LegacyV1StatsIO snap = stats.snapshot(10);
    assertThat(snap.byEndpoint()).hasSize(10);
    assertThat(snap.byPrincipal()).hasSize(10);
    // Sanity: total still reflects all 200 hits
    assertThat(snap.totalHits()).isEqualTo(200);
  }

  @Test
  void snapshot_ordersByHitsDescThenKey() {
    stats.recordHit("/shepard/api/a", "alice");
    stats.recordHit("/shepard/api/b", "bob");
    stats.recordHit("/shepard/api/b", "bob");
    stats.recordHit("/shepard/api/c", "carol");
    stats.recordHit("/shepard/api/c", "carol");
    stats.recordHit("/shepard/api/c", "carol");

    LegacyV1StatsIO snap = stats.snapshot();
    assertThat(snap.byEndpoint())
      .extracting(LegacyV1StatsIO.EndpointCount::pathPattern)
      .containsExactly("/shepard/api/c", "/shepard/api/b", "/shepard/api/a");
  }

  @Test
  void reset_clearsAllCounters() {
    stats.recordHit("/shepard/api/x", "alice");
    stats.checkAndMarkFirstHit("/shepard/api/x", "alice");

    stats.reset();

    LegacyV1StatsIO snap = stats.snapshot();
    assertThat(snap.totalHits()).isZero();
    assertThat(snap.byEndpoint()).isEmpty();
    assertThat(snap.byPrincipal()).isEmpty();
    assertThat(snap.firstHitAt()).isNull();
    // After reset, the (path, principal) pair is re-eligible for a
    // first-hit log — critical so an admin reset doesn't permanently
    // squelch future WARN lines.
    assertThat(stats.checkAndMarkFirstHit("/shepard/api/x", "alice")).isTrue();
  }

  @Test
  void getTotalHits_matchesSnapshot() {
    stats.recordHit("/shepard/api/x", "alice");
    stats.recordHit("/shepard/api/y", "bob");
    assertThat(stats.getTotalHits()).isEqualTo(stats.snapshot().totalHits()).isEqualTo(2);
  }
}
