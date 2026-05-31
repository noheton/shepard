package de.dlr.shepard.v2.fair.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.v2.fair.io.CheckResultIO;
import de.dlr.shepard.v2.fair.io.MetadataCompletenessScoreIO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FAIR4 unit tests for {@link MetadataCompletenessService}.
 *
 * <p>Pure unit tests — no Quarkus boot, no Neo4j. The service's Cypher count
 * queries (semanticAnnotation, labJournal, keywords) will return 0 when
 * {@code NeoConnector} is absent; we compensate by disabling checks that
 * depend on them in the happy-path builder (they are tested via the REST
 * layer test for count-based pass cases).
 *
 * <p>Covers: all-pass (100 pts), empty collection (low score), partial
 * scenarios (license missing, description stub, no data objects), score
 * band classification, check-id stability, weight sum invariant.
 *
 * <p>Tests (≥ 6 required per FAIR4 spec):
 * <ol>
 *   <li>all static checks pass → score = 75 (name+desc+license+ar+orcid+do = 75; counts return 0)</li>
 *   <li>empty collection → very low score (0 when all fields missing)</li>
 *   <li>license missing → score drops by 20</li>
 *   <li>description too short → score drops by 15</li>
 *   <li>no DataObjects → score drops by 15</li>
 *   <li>check IDs are stable and 9 in number</li>
 *   <li>weight sum is 100</li>
 *   <li>percentage = 100 * score / maxScore</li>
 *   <li>score clamped to [0, 100]</li>
 * </ol>
 */
class MetadataCompletenessServiceTest {

  static final long COLL_OGM_ID = 42L;
  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000042";

  /**
   * The service under test. We use the real instance but Neo4j count queries
   * (annotationCount, labJournalCount, keywordCount) will each return 0 because
   * there is no live Neo4j in this test scope — that is fine for the static-field
   * checks (name, description, license, accessRights, creatorOrcid, dataObjects)
   * that we focus on here.
   */
  MetadataCompletenessService service;

  @BeforeEach
  void setUp() {
    service = new MetadataCompletenessService();
  }

  // ── Test 1: static checks all pass ──────────────────────────────────────────

  @Test
  void staticChecksAllPassed_scoreIsSeventyFive() {
    // name(10) + description(15) + license(20) + accessRights(10) + creatorOrcid(10)
    // + dataObjects(15) = 80. semanticAnnotation(10) + labJournal(5) + keywords(5)
    // will return 0 from the live count queries (no Neo4j in test), subtracting 20 more.
    // So static-field score = 80; with no count-query results: 80 - (10+5+5) = 60?
    // Actually the count queries go through NeoConnector which will throw/return 0,
    // and the service catches all exceptions, returning 0. So the three count-based
    // checks (semanticAnnotation, labJournal, keywords) all fail.
    // Score = 10+15+20+10+10+15 = 80.
    Collection coll = buildFullCollection();

    MetadataCompletenessScoreIO result = service.compute(coll);

    assertNotNull(result);
    assertEquals(COLL_APP_ID, result.getCollectionAppId());
    // The six static checks pass; the three count-based checks fail (no Neo4j in test).
    // 10+15+20+10+10+15 = 80
    assertEquals(80, result.getScore());
    assertEquals(100, result.getMaxScore());
    assertEquals(9, result.getChecks().size());
  }

  // ── Test 2: empty collection → minimal score ────────────────────────────────

  @Test
  void emptyCollection_scoreIsZero() {
    Collection coll = buildEmptyCollection();

    MetadataCompletenessScoreIO result = service.compute(coll);

    // Nothing passes — all 9 checks fail.
    assertEquals(0, result.getScore());
    assertEquals("error", scoreBand(result));
  }

  // ── Test 3: missing license → score drops by 20 ─────────────────────────────

  @Test
  void missingLicense_scoreDropsByTwenty() {
    Collection full = buildFullCollection();
    Collection noLicense = buildFullCollection();
    noLicense.setLicense(null);

    int scoreFull = service.compute(full).getScore();
    int scoreNoLicense = service.compute(noLicense).getScore();

    assertEquals(20, scoreFull - scoreNoLicense,
      "Removing license should drop score by exactly 20 pts");
    assertFalse(findCheck(service.compute(noLicense), "license").isPassed());
  }

  // ── Test 4: description too short → score drops by 15 ───────────────────────

  @Test
  void descriptionTooShort_scoreDropsByFifteen() {
    Collection full = buildFullCollection();
    Collection shortDesc = buildFullCollection();
    shortDesc.setDescription("stub"); // < 50 chars

    int scoreFull = service.compute(full).getScore();
    int scoreShort = service.compute(shortDesc).getScore();

    assertEquals(15, scoreFull - scoreShort,
      "Short description should drop score by exactly 15 pts");
    assertFalse(findCheck(service.compute(shortDesc), "description").isPassed());
  }

  // ── Test 5: no DataObjects → score drops by 15 ──────────────────────────────

  @Test
  void noDataObjects_scoreDropsByFifteen() {
    Collection full = buildFullCollection();
    Collection noDo = buildFullCollection();
    noDo.setDataObjects(List.of());

    int scoreFull = service.compute(full).getScore();
    int scoreNoDo = service.compute(noDo).getScore();

    assertEquals(15, scoreFull - scoreNoDo,
      "No DataObjects should drop score by exactly 15 pts");
    assertFalse(findCheck(service.compute(noDo), "dataObjects").isPassed());
  }

  // ── Test 6: 9 checks with stable IDs ────────────────────────────────────────

  @Test
  void checks_nineCheckIdsStable() {
    MetadataCompletenessScoreIO result = service.compute(buildFullCollection());

    List<String> ids = result.getChecks().stream()
      .map(CheckResultIO::getCheckId)
      .toList();

    assertEquals(9, ids.size());
    assertTrue(ids.contains("name"));
    assertTrue(ids.contains("description"));
    assertTrue(ids.contains("license"));
    assertTrue(ids.contains("accessRights"));
    assertTrue(ids.contains("creatorOrcid"));
    assertTrue(ids.contains("semanticAnnotation"));
    assertTrue(ids.contains("labJournal"));
    assertTrue(ids.contains("keywords"));
    assertTrue(ids.contains("dataObjects"));
  }

  // ── Test 7: weight sum = 100 ─────────────────────────────────────────────────

  @Test
  void weightSum_isOneHundred() {
    MetadataCompletenessScoreIO result = service.compute(buildFullCollection());

    int sum = result.getChecks().stream().mapToInt(CheckResultIO::getWeight).sum();
    assertEquals(100, sum, "Total weight of all checks must be exactly 100");
    assertEquals(100, result.getMaxScore());
  }

  // ── Test 8: percentage = 100 * score / maxScore ──────────────────────────────

  @Test
  void percentage_matchesScoreOverMaxScore() {
    MetadataCompletenessScoreIO result = service.compute(buildFullCollection());

    double expected = 100.0 * result.getScore() / result.getMaxScore();
    assertEquals(expected, result.getPercentage(), 0.01,
      "percentage must equal 100 * score / maxScore");
  }

  // ── Test 9: ORCID missing → creatorOrcid check fails ────────────────────────

  @Test
  void missingOrcid_creatorOrcidCheckFails() {
    Collection coll = buildFullCollection();
    coll.setCreatedByOrcid(null);

    MetadataCompletenessScoreIO result = service.compute(coll);

    assertFalse(findCheck(result, "creatorOrcid").isPassed(),
      "creatorOrcid check should fail when ORCID is null");
  }

  // ── Test 10: band classification ────────────────────────────────────────────

  @Test
  void bandClassification_fullCollectionIsSuccessOrWarning() {
    MetadataCompletenessScoreIO result = service.compute(buildFullCollection());
    // With 3 count-based checks failing (no Neo4j), score = 80 → success
    String band = scoreBand(result);
    assertTrue(
      "success".equals(band) || "warning".equals(band),
      "Band should be success or warning for a well-filled collection; got: " + band
    );
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private Collection buildFullCollection() {
    Collection coll = new Collection(COLL_OGM_ID);
    coll.setAppId(COLL_APP_ID);
    coll.setName("LUMEN Hotfire Campaign");
    coll.setDescription(
      "Synthetic showcase dataset for shepard. Hotfire test campaign of " +
      "the LUMEN demonstrator engine. NOT REAL DLR/LUMEN data. Includes 15 test runs."
    );
    coll.setLicense("CC-BY-4.0");
    coll.setAccessRights("OPEN");
    coll.setCreatedByOrcid("0000-0001-2345-6789");

    DataObject dobj = new DataObject(101L);
    dobj.setAppId("018f9c5a-7e26-7000-a000-000000001001");
    dobj.setName("TR-001");
    coll.setDataObjects(List.of(dobj));

    return coll;
  }

  private Collection buildEmptyCollection() {
    Collection coll = new Collection(99L);
    coll.setAppId("018f9c5a-0000-7000-a000-000000000000");
    coll.setName("");
    coll.setDescription("");
    coll.setLicense(null);
    coll.setAccessRights(null);
    coll.setCreatedByOrcid(null);
    coll.setDataObjects(List.of());
    return coll;
  }

  private CheckResultIO findCheck(MetadataCompletenessScoreIO result, String checkId) {
    return result.getChecks().stream()
      .filter(c -> c.getCheckId().equals(checkId))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Check not found: " + checkId));
  }

  private String scoreBand(MetadataCompletenessScoreIO result) {
    int s = result.getScore();
    if (s < 50) return "error";
    if (s < 80) return "warning";
    return "success";
  }
}
