package de.dlr.shepard.v2.project.daos;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.project.io.ProjectByAnnotationItemIO;
import de.dlr.shepard.v2.project.io.ProjectIO;
import de.dlr.shepard.v2.project.io.SubCollectionItemIO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.session.Session;

/**
 * PROJ-REST-1 + PROJ-REST-2 — read-only DAO for Project-flavoured queries.
 *
 * <p>Storage convention assumed throughout this DAO:
 * <ul>
 *   <li>{@code urn:shepard:project} is encoded as a {@link
 *       de.dlr.shepard.context.semantic.entities.SemanticAnnotation} carrying
 *       {@code subjectAppId = <Collection.appId>}, {@code subjectKind = "Collection"},
 *       {@code propertyIRI = "urn:shepard:project"}, {@code valueName = "true"}.</li>
 *   <li>{@code urn:shepard:partOf} is encoded the same way with
 *       {@code propertyIRI = "urn:shepard:partOf"} and {@code valueName} set
 *       to the parent Project's appId (UUID v7) as a literal string — NOT as
 *       {@code valueIRI} (which would violate the v6 literal-XOR-IRI invariant).</li>
 *   <li>{@code urn:shepard:programme} is encoded with
 *       {@code propertyIRI = "urn:shepard:programme"} and {@code valueName}
 *       set to the free-text programme name.</li>
 * </ul>
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3.1 + §3.2 + §3.3}.
 */
@ApplicationScoped
public class ProjectsDAO {

  static final String PRED_PROJECT   = "urn:shepard:project";
  static final String PRED_PART_OF   = "urn:shepard:partOf";
  static final String PRED_PROGRAMME = "urn:shepard:programme";

  /** Returns true when the Collection at {@code appId} carries urn:shepard:project = "true". */
  public boolean isProject(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return false;
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return false;

    String cypher =
      "MATCH (proj:SemanticAnnotation { " +
      "  subjectAppId: $appId, propertyIRI: $predProject }) " +
      "WHERE proj.valueName = 'true' " +
      "RETURN count(proj) > 0 AS isProject";

    var result = session.query(cypher, Map.of(
      "appId", collectionAppId,
      "predProject", PRED_PROJECT
    ));
    for (var row : result) {
      Object isProject = row.get("isProject");
      return isProject instanceof Boolean b && b;
    }
    return false;
  }

  /**
   * Returns true when a Collection node exists at {@code appId} and is not soft-deleted.
   */
  public boolean collectionExists(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return false;
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return false;
    String cypher =
      "MATCH (c:Collection {appId: $appId}) " +
      "WHERE (c.deleted IS NULL OR c.deleted = false) " +
      "RETURN count(c) > 0 AS exists";
    var result = session.query(cypher, Map.of("appId", collectionAppId));
    for (var row : result) {
      Object exists = row.get("exists");
      return exists instanceof Boolean b && b;
    }
    return false;
  }

  /**
   * Load a Project envelope by appId. Returns {@code null} when the appId
   * does not resolve to an existing, non-deleted Collection OR when that
   * Collection is not a Project.
   */
  public ProjectIO findProject(String projectAppId) {
    if (projectAppId == null || projectAppId.isBlank()) return null;
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return null;

    // 1) Base Collection row (also serves as the existence check).
    String cypher =
      "MATCH (c:Collection {appId: $appId}) " +
      "WHERE (c.deleted IS NULL OR c.deleted = false) " +
      "OPTIONAL MATCH (proj:SemanticAnnotation { " +
      "  subjectAppId: $appId, propertyIRI: $predProject }) " +
      "WHERE proj.valueName = 'true' " +
      "RETURN c.appId AS appId, c.shepardId AS shepardId, c.name AS name, " +
      "       c.description AS description, c.ownerGroup AS ownerGroup, " +
      "       count(proj) > 0 AS isProject";

    var result = session.query(cypher, Map.of(
      "appId", projectAppId,
      "predProject", PRED_PROJECT
    ));

    ProjectIO io = null;
    for (var row : result) {
      Object isProjectObj = row.get("isProject");
      boolean isProject = isProjectObj instanceof Boolean b && b;
      if (!isProject) return null;
      io = new ProjectIO();
      io.setAppId((String) row.get("appId"));
      io.setId(asLong(row.get("shepardId")));
      io.setName((String) row.get("name"));
      io.setDescription((String) row.get("description"));
      io.setOwnerGroup((String) row.get("ownerGroup"));
      io.setProject(true);
      break;
    }
    if (io == null) return null;

    // 2) Programmes — separate single-shot query, scalar-list result.
    io.setProgrammes(findProgrammes(projectAppId));

    // 3) Sub-Collection count + aggregate DataObject count + last-activity rollup.
    String aggCypher =
      "MATCH (child:Collection) " +
      "WHERE (child.deleted IS NULL OR child.deleted = false) " +
      "  AND EXISTS { " +
      "    MATCH (po:SemanticAnnotation { " +
      "      subjectAppId: child.appId, " +
      "      propertyIRI: $predPartOf, valueName: $appId }) " +
      "  } " +
      "WITH count(DISTINCT child) AS subCollectionCount, collect(DISTINCT child) AS children " +
      "UNWIND children AS childUw " +
      "OPTIONAL MATCH (childUw)-[:`" + Constants.HAS_DATAOBJECT + "`]->(do:DataObject) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "RETURN subCollectionCount, " +
      "       count(do) AS aggregateDoCount, " +
      "       max(coalesce(do.updatedAt, do.createdAt)) AS lastActivityMillis";
    var agg = session.query(aggCypher, Map.of(
      "appId", projectAppId,
      "predPartOf", PRED_PART_OF
    ));
    for (var row : agg) {
      io.setSubCollectionCount(asLongOrZero(row.get("subCollectionCount")));
      io.setAggregateDoCount(asLongOrZero(row.get("aggregateDoCount")));
      Long la = asLong(row.get("lastActivityMillis"));
      if (la != null && la > 0L) io.setLastActivityMillis(la);
    }

    return io;
  }

  /**
   * Load the sub-Collection rows for a Project. Returns an empty list when
   * the Project has no children. The caller is responsible for the
   * {@link #isProject(String)} check before calling this.
   */
  public List<SubCollectionItemIO> findSubCollections(String projectAppId) {
    if (projectAppId == null || projectAppId.isBlank()) return List.of();
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return List.of();

    String cypher =
      "MATCH (child:Collection) " +
      "WHERE (child.deleted IS NULL OR child.deleted = false) " +
      "  AND EXISTS { " +
      "    MATCH (po:SemanticAnnotation { " +
      "      subjectAppId: child.appId, " +
      "      propertyIRI: $predPartOf, valueName: $appId }) " +
      "  } " +
      "WITH DISTINCT child " +
      "OPTIONAL MATCH (child)-[:`" + Constants.HAS_DATAOBJECT + "`]->(do:DataObject) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "WITH child, count(do) AS doCount, max(coalesce(do.updatedAt, do.createdAt)) AS lastActivityMillis " +
      "OPTIONAL MATCH (other:SemanticAnnotation { " +
      "  subjectAppId: child.appId, propertyIRI: $predPartOf }) " +
      "WHERE other.valueName <> $appId " +
      "WITH child, doCount, lastActivityMillis, collect(DISTINCT other.valueName) AS alsoMemberOf " +
      "RETURN child.appId AS appId, child.shepardId AS shepardId, child.name AS name, " +
      "       child.ownerGroup AS ownerGroup, doCount, lastActivityMillis, alsoMemberOf " +
      "ORDER BY child.name";

    var result = session.query(cypher, Map.of(
      "appId", projectAppId,
      "predPartOf", PRED_PART_OF
    ));

    List<SubCollectionItemIO> items = new ArrayList<>();
    for (var row : result) {
      SubCollectionItemIO io = new SubCollectionItemIO();
      io.setAppId((String) row.get("appId"));
      io.setId(asLong(row.get("shepardId")));
      io.setName((String) row.get("name"));
      io.setOwnerGroup((String) row.get("ownerGroup"));
      io.setDoCount(asLongOrZero(row.get("doCount")));
      Long la = asLong(row.get("lastActivityMillis"));
      if (la != null && la > 0L) io.setLastActivityMillis(la);
      @SuppressWarnings("unchecked")
      List<String> also = (List<String>) row.get("alsoMemberOf");
      if (also != null) io.setAlsoMemberOf(also);
      items.add(io);
    }
    return items;
  }

  /**
   * Programme labels (urn:shepard:programme values) declared on a Project.
   * Returns an empty list when none are set.
   */
  public List<String> findProgrammes(String projectAppId) {
    if (projectAppId == null || projectAppId.isBlank()) return List.of();
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return List.of();

    String cypher =
      "MATCH (prog:SemanticAnnotation { " +
      "  subjectAppId: $appId, propertyIRI: $predProgramme }) " +
      "RETURN collect(DISTINCT prog.valueName) AS programmes";

    var result = session.query(cypher, Map.of(
      "appId", projectAppId,
      "predProgramme", PRED_PROGRAMME
    ));
    for (var row : result) {
      @SuppressWarnings("unchecked")
      List<String> programmes = (List<String>) row.get("programmes");
      return programmes != null ? programmes : List.of();
    }
    return List.of();
  }

  /**
   * Page of DataObjects across the Project's sub-Collections whose direct
   * semantic annotation matches the predicate + value (no parent walk).
   * Use {@link #countByAnnotation(String, String, String)} for the total.
   */
  public List<ProjectByAnnotationItemIO> pageByAnnotation(
      String projectAppId,
      String predicate,
      String value,
      boolean includeAnnotations,
      int page,
      int pageSize) {
    if (projectAppId == null || projectAppId.isBlank()) return List.of();
    if (predicate == null || predicate.isBlank()) return List.of();
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return List.of();

    int safePage = Math.max(page, 0);
    int safeSize = Math.max(1, Math.min(pageSize, 500));
    int skip = safePage * safeSize;

    String cypher =
      "MATCH (child:Collection) " +
      "WHERE (child.deleted IS NULL OR child.deleted = false) " +
      "  AND EXISTS { " +
      "    MATCH (po:SemanticAnnotation { " +
      "      subjectAppId: child.appId, " +
      "      propertyIRI: $predPartOf, valueName: $appId }) " +
      "  } " +
      "MATCH (child)-[:`" + Constants.HAS_DATAOBJECT + "`]->(do:DataObject) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "  AND EXISTS { " +
      "    MATCH (a:SemanticAnnotation { " +
      "      subjectAppId: do.appId, propertyIRI: $predicate }) " +
      "    WHERE (a.valueName = $value OR a.valueIRI = $value) " +
      "  } " +
      "RETURN do.appId AS doAppId, do.shepardId AS doShepardId, do.name AS doName, " +
      "       child.appId AS collAppId, child.name AS collName " +
      "ORDER BY do.name " +
      "SKIP " + skip + " LIMIT " + safeSize;

    var result = session.query(cypher, Map.of(
      "appId", projectAppId,
      "predPartOf", PRED_PART_OF,
      "predicate", predicate,
      "value", value == null ? "" : value
    ));

    List<ProjectByAnnotationItemIO> items = new ArrayList<>();
    for (var row : result) {
      ProjectByAnnotationItemIO io = new ProjectByAnnotationItemIO();
      io.setAppId((String) row.get("doAppId"));
      io.setId(asLong(row.get("doShepardId")));
      io.setName((String) row.get("doName"));
      io.setCollectionAppId((String) row.get("collAppId"));
      io.setCollectionName((String) row.get("collName"));
      if (includeAnnotations) {
        ProjectByAnnotationItemIO.MatchedAnnotation m = new ProjectByAnnotationItemIO.MatchedAnnotation();
        m.setPredicate(predicate);
        m.setValue(value);
        m.setSource("direct");
        io.addMatched(m);
      }
      items.add(io);
    }
    return items;
  }

  /** Count of DataObjects across the Project's sub-Collections matching the predicate + value. */
  public long countByAnnotation(String projectAppId, String predicate, String value) {
    if (projectAppId == null || projectAppId.isBlank()) return 0L;
    if (predicate == null || predicate.isBlank()) return 0L;
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return 0L;

    String cypher =
      "MATCH (child:Collection) " +
      "WHERE (child.deleted IS NULL OR child.deleted = false) " +
      "  AND EXISTS { " +
      "    MATCH (po:SemanticAnnotation { " +
      "      subjectAppId: child.appId, " +
      "      propertyIRI: $predPartOf, valueName: $appId }) " +
      "  } " +
      "MATCH (child)-[:`" + Constants.HAS_DATAOBJECT + "`]->(do:DataObject) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "  AND EXISTS { " +
      "    MATCH (a:SemanticAnnotation { " +
      "      subjectAppId: do.appId, propertyIRI: $predicate }) " +
      "    WHERE (a.valueName = $value OR a.valueIRI = $value) " +
      "  } " +
      "RETURN count(do) AS total";

    var result = session.query(cypher, Map.of(
      "appId", projectAppId,
      "predPartOf", PRED_PART_OF,
      "predicate", predicate,
      "value", value == null ? "" : value
    ));
    for (var row : result) {
      return asLongOrZero(row.get("total"));
    }
    return 0L;
  }

  /** Return appIds of every Collection that carries urn:shepard:project = "true". */
  public List<String> findAllProjectAppIds() {
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return List.of();
    String cypher =
      "MATCH (proj:SemanticAnnotation { propertyIRI: $predProject }) " +
      "WHERE proj.valueName = 'true' AND proj.subjectKind IN ['Collection', null] " +
      "WITH DISTINCT proj.subjectAppId AS appId " +
      "MATCH (c:Collection {appId: appId}) " +
      "WHERE (c.deleted IS NULL OR c.deleted = false) " +
      "RETURN c.appId AS appId " +
      "ORDER BY c.name";
    var result = session.query(cypher, Map.of("predProject", PRED_PROJECT));
    List<String> out = new ArrayList<>();
    for (var row : result) {
      String appId = (String) row.get("appId");
      if (appId != null) out.add(appId);
    }
    return out;
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static Long asLong(Object o) {
    if (o == null) return null;
    if (o instanceof Number n) return n.longValue();
    return null;
  }

  private static long asLongOrZero(Object o) {
    Long l = asLong(o);
    return l == null ? 0L : l;
  }
}
