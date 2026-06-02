package de.dlr.shepard.v2.collection.daos;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.collection.io.SubCollectionEntryIO;
import de.dlr.shepard.v2.collection.io.SubCollectionsIO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DAO for the PROJ-REST-1 sub-collections query.
 *
 * <p>Executes a single Cypher traversal that returns in one result-set:
 * <ol>
 *   <li>Whether the parent Collection is a "project" (has a
 *       {@code urn:shepard:project = "true"} annotation).</li>
 *   <li>All {@code urn:shepard:programme} values on the parent.</li>
 *   <li>All child Collections that carry a
 *       {@code urn:shepard:partOf = <parentAppId>} annotation, plus each
 *       child's own additional {@code urn:shepard:partOf} values (for
 *       {@code alsoMemberOf}) and its DataObject count.</li>
 * </ol>
 *
 * <p>No OGM hydration is needed — the query returns scalar projections
 * only, matching the trimmed {@link SubCollectionEntryIO} shape.
 *
 * <p>Spec: {@code aidocs/integrations/121 §3.1}.
 */
@ApplicationScoped
public class SubCollectionsDAO {

  private static final String PREDICATE_PROJECT    = "urn:shepard:project";
  private static final String PREDICATE_PROGRAMME  = "urn:shepard:programme";
  private static final String PREDICATE_PART_OF    = "urn:shepard:partOf";
  private static final String VALUE_TRUE           = "true";

  /**
   * Cypher that answers: for parent {@code $parentAppId} —
   * <ul>
   *   <li>is it a project? ({@code isProject} — single boolean)</li>
   *   <li>which programmes is it part of? ({@code programmes} — list)</li>
   *   <li>which child collections point at it via {@code partOf}?
   *       ({@code childAppId}, {@code childId}, {@code childName},
   *       {@code doCount}, {@code alsoMemberOf})</li>
   * </ul>
   *
   * <p>The query intentionally uses {@code OPTIONAL MATCH} so it always
   * returns the parent-level scalars even when no children exist.
   *
   * <p>The {@code WHERE child &lt;&gt; c} guard prevents a Collection from
   * showing itself as its own sub-collection (edge case: a Collection with
   * a self-referential {@code partOf} annotation).
   */
  private static final String CYPHER =
    "MATCH (c:Collection {appId: $parentAppId}) " +
    "OPTIONAL MATCH (c)-[:HAS_SEMANTIC_ANNOTATION]->(proj:SemanticAnnotation " +
    "  {predicate: '" + PREDICATE_PROJECT + "', value: '" + VALUE_TRUE + "'}) " +
    "OPTIONAL MATCH (c)-[:HAS_SEMANTIC_ANNOTATION]->(prog:SemanticAnnotation " +
    "  {predicate: '" + PREDICATE_PROGRAMME + "'}) " +
    "OPTIONAL MATCH (child:Collection)-[:HAS_SEMANTIC_ANNOTATION]->(p:SemanticAnnotation " +
    "  {predicate: '" + PREDICATE_PART_OF + "', value: $parentAppId}) " +
    "WHERE child <> c " +
    "OPTIONAL MATCH (child)-[:HAS_SEMANTIC_ANNOTATION]->(also:SemanticAnnotation " +
    "  {predicate: '" + PREDICATE_PART_OF + "'}) " +
    "WHERE also.value <> $parentAppId " +
    "OPTIONAL MATCH (child)-[:HAS_DATAOBJECT]->(do:DataObject) " +
    "WHERE do.deleted IS NULL OR do.deleted = false " +
    "WITH c, " +
    "     proj, " +
    "     collect(DISTINCT prog.value) AS programmes, " +
    "     child, " +
    "     collect(DISTINCT also.value) AS alsoMemberOf, " +
    "     count(DISTINCT do) AS doCount " +
    "RETURN " +
    "  (proj IS NOT NULL) AS isProject, " +
    "  programmes, " +
    "  child.appId       AS childAppId, " +
    "  id(child)         AS childId, " +
    "  child.name        AS childName, " +
    "  child.heroImage   AS heroImage, " +
    "  doCount, " +
    "  alsoMemberOf " +
    "ORDER BY childName ASC";

  /**
   * Returns the sub-collections view for the given parent appId.
   *
   * <p>If the parent does not exist (the {@code MATCH} finds nothing),
   * returns {@code null} — the caller is responsible for the 404 response.
   *
   * <p>If the parent exists but has no children, returns a
   * {@link SubCollectionsIO} with an empty {@code subCollections} list.
   *
   * @param parentAppId the appId of the parent Collection
   * @return populated {@link SubCollectionsIO}, or {@code null} when the
   *         parent Collection does not exist
   */
  public SubCollectionsIO findSubCollections(String parentAppId) {
    if (parentAppId == null || parentAppId.isBlank()) return null;

    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return null;

    var result = session.query(CYPHER, Map.of("parentAppId", parentAppId));

    boolean parentFound = false;
    boolean isProject = false;
    List<String> programmes = new ArrayList<>();
    List<SubCollectionEntryIO> children = new ArrayList<>();

    for (var row : result) {
      parentFound = true; // at least one row means the parent MATCH succeeded

      // Parent-level scalars: read once (they're the same across all rows)
      if (programmes.isEmpty()) {
        Object progObj = row.get("programmes");
        if (progObj instanceof Iterable<?> iterable) {
          for (var v : iterable) {
            if (v != null) programmes.add(v.toString());
          }
        }
      }
      isProject = Boolean.TRUE.equals(row.get("isProject"));

      // Child-level scalars: null when no child matched (OPTIONAL MATCH returned null)
      String childAppId = (String) row.get("childAppId");
      if (childAppId == null) continue; // no child on this row

      Long childId = row.get("childId") != null
        ? ((Number) row.get("childId")).longValue()
        : null;
      String childName    = (String) row.get("childName");
      String heroImage    = (String) row.get("heroImage");
      long doCount        = row.get("doCount") != null
        ? ((Number) row.get("doCount")).longValue()
        : 0L;

      List<String> alsoMemberOf = new ArrayList<>();
      Object alsoObj = row.get("alsoMemberOf");
      if (alsoObj instanceof Iterable<?> iterable) {
        for (var v : iterable) {
          if (v != null) alsoMemberOf.add(v.toString());
        }
      }

      children.add(new SubCollectionEntryIO(
        childAppId, childId, childName, heroImage, doCount, null, null, alsoMemberOf
      ));
    }

    if (!parentFound) return null;

    return new SubCollectionsIO(parentAppId, isProject, programmes, children);
  }
}
