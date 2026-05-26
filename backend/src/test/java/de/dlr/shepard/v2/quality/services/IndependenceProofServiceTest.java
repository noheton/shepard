package de.dlr.shepard.v2.quality.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.quality.io.IndependenceProofQuery;
import de.dlr.shepard.v2.quality.io.IndependenceProofRequestIO;
import de.dlr.shepard.v2.quality.io.IndependenceProofResultIO;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * TPL11 — unit tests for {@link IndependenceProofService}.
 *
 * <p>All tests use Mockito-stubbed Neo4j sessions — no live Neo4j instance required.
 * The mock pattern follows {@code InternalSemanticConnectorTest}: stub
 * {@code session.query(...)} to return a {@link Result} whose
 * {@code queryResults()} returns a list of row maps.
 *
 * <p>Test matrix:
 * <ol>
 *   <li>Disjoint sets (no ancestor rows, no shared annotations) → independent=true</li>
 *   <li>Sets sharing one ancestor → independent=false, one entry in sharedAncestors</li>
 *   <li>Sets sharing one annotation key-value pair → independent=false,
 *       one entry in sharedAnnotations</li>
 *   <li>Empty ancestors from query → treated as independent on the ancestor dimension</li>
 *   <li>Sets overlapping on ancestor AND annotation → both fields populated</li>
 *   <li>Annotation check: blank attribute values are excluded</li>
 * </ol>
 */
class IndependenceProofServiceTest {

  // ── fixtures ─────────────────────────────────────────────────────────────

  private static final String DO_A1 = "appid-a1";
  private static final String DO_A2 = "appid-a2";
  private static final String DO_B1 = "appid-b1";
  private static final String DO_B2 = "appid-b2";
  private static final String ANCESTOR = "appid-ancestor";

  private Session session;
  private IndependenceProofService svc;

  @BeforeEach
  void setUp() {
    session = mock(Session.class);
    svc = new IndependenceProofService(session);
  }

  // ── helper factory methods ────────────────────────────────────────────────

  /** Build a Result whose queryResults() returns the given list of row maps. */
  private static Result rows(List<Map<String, Object>> rowList) {
    Result r = mock(Result.class);
    when(r.queryResults()).thenReturn(rowList);
    return r;
  }

  private static Result emptyResult() {
    return rows(Collections.emptyList());
  }

  /**
   * Build a row map representing a shared ancestor as returned by
   * {@link IndependenceProofQuery#SHARED_ANCESTORS}.
   */
  private static Map<String, Object> ancestorRow(String ancestorId, List<String> fromA, List<String> fromB) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("sharedAncestorAppId", ancestorId);
    row.put("reachableFromA", fromA);
    row.put("reachableFromB", fromB);
    return row;
  }

  /**
   * Build a row map for the attribute-fetch query, simulating a DataObject
   * with attributes stored as {@code attributes||<key>} Neo4j properties.
   *
   * @param doAppId    the DataObject appId
   * @param attributes map of annotation key → value (will be wrapped as {@code attributes||key})
   */
  private static Map<String, Object> attrRow(String doAppId, Map<String, String> attributes) {
    Map<String, Object> props = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : attributes.entrySet()) {
      props.put("attributes||" + e.getKey(), e.getValue());
    }
    props.put("appId", doAppId);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("doAppId", doAppId);
    row.put("props", props);
    return row;
  }

  // ── stub helpers ─────────────────────────────────────────────────────────

  /**
   * Stub the ancestor Cypher query to return the given rows.
   *
   * <p>Result mock is built BEFORE the when() call to avoid Mockito's
   * "unfinished stubbing" error that occurs when mock/when is called inside
   * another when().thenReturn() chain.
   */
  private void stubAncestors(List<Map<String, Object>> ancestorRows) {
    Result r = rows(ancestorRows);
    when(session.query(eq(IndependenceProofQuery.SHARED_ANCESTORS), any())).thenReturn(r);
  }

  /**
   * Stub the attribute-fetch query for a specific appId set to return the given rows.
   * Result mock is pre-built before the when() chain for the same reason.
   *
   * <p>The parameter map is matched by exact equality on the appIds list so the
   * stub distinguishes setA queries from setB queries (both use the same Cypher
   * string but different parameter maps).
   */
  private void stubAttributesForSet(List<String> appIds, List<Map<String, Object>> attrRows) {
    Result r = rows(attrRows);
    when(session.query(
        eq(
          "MATCH (do:DataObject) " +
          "WHERE do.appId IN $appIds " +
          "  AND (do.deleted IS NULL OR do.deleted = false) " +
          "RETURN do.appId AS doAppId, properties(do) AS props"
        ),
        eq(Map.of("appIds", appIds))
      ))
      .thenReturn(r);
  }


  // ── test cases ───────────────────────────────────────────────────────────

  /**
   * Two completely disjoint sets with no shared ancestors and no shared
   * annotations → independent=true, both lists empty.
   * Per-set stubs ensure setA's "LOX/LH2" and setB's "RP-1" are treated separately.
   */
  @Test
  void independent_whenNoSharedAncestorsAndNoSharedAnnotations() {
    stubAncestors(List.of());
    // setA contains DO_A1 with propellant=LOX/LH2
    stubAttributesForSet(List.of(DO_A1), List.of(
      attrRow(DO_A1, Map.of("propellant", "LOX/LH2"))
    ));
    // setB contains DO_B1 with propellant=RP-1 — different value → not shared
    stubAttributesForSet(List.of(DO_B1), List.of(
      attrRow(DO_B1, Map.of("propellant", "RP-1"))
    ));

    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of(DO_A1));
    req.setSetB(List.of(DO_B1));

    IndependenceProofResultIO result = svc.check(req);

    assertThat(result.isIndependent()).isTrue();
    assertThat(result.getSharedAncestors()).isEmpty();
    assertThat(result.getSharedAnnotations()).isEmpty();
    assertThat(result.getCheckedAt()).isNotNull();
  }

  /**
   * Sets sharing one common ancestor → independent=false, one entry in
   * sharedAncestors with correct fromA/fromB lists.
   */
  @Test
  void notIndependent_whenOneSharedAncestor() {
    stubAncestors(List.of(
      ancestorRow(ANCESTOR, List.of(DO_A1), List.of(DO_B1))
    ));
    // No attributes in either set → no shared annotation
    stubAttributesForSet(List.of(DO_A1), List.of());
    stubAttributesForSet(List.of(DO_B1), List.of());

    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of(DO_A1));
    req.setSetB(List.of(DO_B1));

    IndependenceProofResultIO result = svc.check(req);

    assertThat(result.isIndependent()).isFalse();
    assertThat(result.getSharedAncestors()).hasSize(1);
    assertThat(result.getSharedAncestors().get(0).getAncestorAppId()).isEqualTo(ANCESTOR);
    assertThat(result.getSharedAncestors().get(0).getReachableFromA()).containsExactly(DO_A1);
    assertThat(result.getSharedAncestors().get(0).getReachableFromB()).containsExactly(DO_B1);
    assertThat(result.getSharedAnnotations()).isEmpty();
  }

  /**
   * Sets sharing an annotation key-value pair (same key AND same value) →
   * independent=false, one entry in sharedAnnotations.
   * Per-set stubs ensure DO_A1 is in setA's query result and DO_B1 is in setB's.
   */
  @Test
  void notIndependent_whenOneSharedAnnotation() {
    stubAncestors(List.of()); // no shared ancestors
    stubAttributesForSet(List.of(DO_A1), List.of(
      attrRow(DO_A1, Map.of("propellant", "LOX/LH2"))
    ));
    stubAttributesForSet(List.of(DO_B1), List.of(
      attrRow(DO_B1, Map.of("propellant", "LOX/LH2"))  // same key + value
    ));

    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of(DO_A1));
    req.setSetB(List.of(DO_B1));

    IndependenceProofResultIO result = svc.check(req);

    assertThat(result.isIndependent()).isFalse();
    assertThat(result.getSharedAncestors()).isEmpty();
    assertThat(result.getSharedAnnotations()).hasSize(1);
    assertThat(result.getSharedAnnotations().get(0).getKey()).isEqualTo("propellant");
    assertThat(result.getSharedAnnotations().get(0).getValue()).isEqualTo("LOX/LH2");
    assertThat(result.getSharedAnnotations().get(0).getFromA()).containsExactly(DO_A1);
    assertThat(result.getSharedAnnotations().get(0).getFromB()).containsExactly(DO_B1);
  }

  /**
   * Sets sharing both a common ancestor and a shared annotation →
   * both fields populated, independent=false.
   */
  @Test
  void notIndependent_whenBothAncestorAndAnnotationOverlap() {
    stubAncestors(List.of(
      ancestorRow(ANCESTOR, List.of(DO_A1), List.of(DO_B1))
    ));
    stubAttributesForSet(List.of(DO_A1), List.of(attrRow(DO_A1, Map.of("bench", "TB-1"))));
    stubAttributesForSet(List.of(DO_B1), List.of(attrRow(DO_B1, Map.of("bench", "TB-1"))));

    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of(DO_A1));
    req.setSetB(List.of(DO_B1));

    IndependenceProofResultIO result = svc.check(req);

    assertThat(result.isIndependent()).isFalse();
    assertThat(result.getSharedAncestors()).hasSize(1);
    assertThat(result.getSharedAnnotations()).hasSize(1);
  }

  /**
   * Annotation values that are blank/empty strings do not count as shared
   * even when the same key appears in both sets.
   */
  @Test
  void independent_whenAnnotationValueIsBlankInBothSets() {
    stubAncestors(List.of());
    stubAttributesForSet(List.of(DO_A1), List.of(attrRow(DO_A1, Map.of("note", ""))));  // blank → excluded
    stubAttributesForSet(List.of(DO_B1), List.of(attrRow(DO_B1, Map.of("note", ""))));  // blank → excluded

    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of(DO_A1));
    req.setSetB(List.of(DO_B1));

    IndependenceProofResultIO result = svc.check(req);

    assertThat(result.isIndependent()).isTrue();
    assertThat(result.getSharedAnnotations()).isEmpty();
  }

  /**
   * Multiple DataObjects in each set, only one pair shares an ancestor.
   * The ancestor row should list the specific DO appIds that reach it.
   */
  @Test
  void sharedAncestor_listsBothReachableMembers() {
    // A1 reaches ANCESTOR; A2 does not. B1 and B2 both reach ANCESTOR.
    stubAncestors(List.of(
      ancestorRow(ANCESTOR, List.of(DO_A1), List.of(DO_B1, DO_B2))
    ));
    // No attribute overlap — stub each set separately with empty results
    stubAttributesForSet(List.of(DO_A1, DO_A2), List.of());
    stubAttributesForSet(List.of(DO_B1, DO_B2), List.of());

    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of(DO_A1, DO_A2));
    req.setSetB(List.of(DO_B1, DO_B2));

    IndependenceProofResultIO result = svc.check(req);

    assertThat(result.isIndependent()).isFalse();
    assertThat(result.getSharedAncestors()).hasSize(1);
    assertThat(result.getSharedAncestors().get(0).getReachableFromA()).containsExactly(DO_A1);
    assertThat(result.getSharedAncestors().get(0).getReachableFromB()).containsExactlyInAnyOrder(DO_B1, DO_B2);
  }
}
