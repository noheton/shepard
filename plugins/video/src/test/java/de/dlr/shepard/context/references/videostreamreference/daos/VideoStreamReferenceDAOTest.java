package de.dlr.shepard.context.references.videostreamreference.daos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * VIDEO-HEVC-TRANSCODE-BACKFILL-2026-07-01 — pure-string tests for the
 * Cypher query built by {@link VideoStreamReferenceDAO#buildBackfillCypher}.
 * Testcontainer-free: the assertions are on the query text, so this runs in
 * the plugin's standard {@code mvn test} pass without a Neo4j fixture.
 *
 * <p>Historic bug shape covered: the inline property-match
 * {@code {deleted: FALSE}} on the {@code MATCH} pattern silently excluded
 * rows where {@code r.deleted} is unset. The fix is an explicit
 * {@code (r.deleted IS NULL OR r.deleted = false)} WHERE clause — this test
 * asserts the fix stays applied.
 */
class VideoStreamReferenceDAOTest {

  @Test
  void backfillCypher_matchIsUnfiltered_deletedGuardIsInWhere() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher(null, 0);
    // The MATCH must be a bare label match — NOT `(r:VideoStreamReference {deleted: FALSE})`.
    assertThat(cypher).contains("MATCH (r:VideoStreamReference) ");
    assertThat(cypher).doesNotContain("{deleted: FALSE}");
    assertThat(cypher).doesNotContain("{ deleted: FALSE }");
    // The WHERE clause carries the deleted-guard, tolerant to unset rows.
    assertThat(cypher).contains("r.deleted IS NULL OR r.deleted = false");
  }

  @Test
  void backfillCypher_proxyStatusGuard_matchesNullAndFailed() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher(null, 0);
    assertThat(cypher).contains("r.proxyStatus IS NULL OR r.proxyStatus = 'FAILED'");
  }

  @Test
  void backfillCypher_storageLocatorGuard_alwaysApplied() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher(null, 0);
    assertThat(cypher).contains("r.storageLocator IS NOT NULL");
    assertThat(cypher).contains("r.storageLocator <> ''");
  }

  @Test
  void backfillCypher_codecFilter_omitted_whenNull() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher(null, 0);
    assertThat(cypher).doesNotContain("$codec");
    assertThat(cypher).doesNotContain("videoCodec");
  }

  @Test
  void backfillCypher_codecFilter_omitted_whenBlank() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher("   ", 0);
    assertThat(cypher).doesNotContain("$codec");
  }

  @Test
  void backfillCypher_codecFilter_appliedLowerCase_asParam() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher("HEVC", 0);
    assertThat(cypher).contains("toLower(r.videoCodec) = $codec");
  }

  @Test
  void backfillCypher_limit_omitted_whenZero() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher(null, 0);
    assertThat(cypher).doesNotContain(" LIMIT ");
  }

  @Test
  void backfillCypher_limit_omitted_whenNegative() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher(null, -1);
    assertThat(cypher).doesNotContain(" LIMIT ");
  }

  @Test
  void backfillCypher_limit_appliedInline_whenPositive() {
    String cypher = VideoStreamReferenceDAO.buildBackfillCypher(null, 42);
    assertThat(cypher).contains(" LIMIT 42");
  }
}
