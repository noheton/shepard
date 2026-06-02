package de.dlr.shepard.v2.timeseries.services;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.model.Result;

/**
 * TS-CROSS-DO-VIEW-1 — resolves a DataObject (by appId) + a channel-predicate
 * IRI to the underlying TimeseriesContainer + channel-locator 5-tuple
 * (measurement, device, location, symbolicName, field).
 *
 * <p>The resolution traverses:
 * <pre>
 *   (:DataObject {appId})
 *     -[:has_reference]-> (:TimeseriesReference)
 *     -[:has_payload]-> (:Timeseries)
 *   AND the same Timeseries' appId is shared with
 *   (:AnnotatableTimeseries {appId})
 *     -[:has_annotation]-> (:SemanticAnnotation {propertyIRI = $predicateIri})
 * </pre>
 *
 * <p>The TimeseriesReference carries the containing TimeseriesContainer
 * via {@code is_in_container}. Read off in the same single Cypher call.
 *
 * <p>When the same DataObject has multiple matching channels (e.g. one
 * predicate annotated on several physical channels), the result list is
 * the full set; callers needing determinism pick the first one by
 * {@code symbolicName} ascending — documented at the REST surface.
 *
 * <p>This is a thin DAO-style facade — the heavy lifting is one Cypher
 * query returning scalar columns, no OGM hydration. Cheaper than going
 * through {@link de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO}
 * which is OGM-hydrated and would walk relationships at depth 2.
 */
@RequestScoped
public class CrossDoChannelResolver extends GenericDAO<de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries> {

  @Override
  public Class<de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries> getEntityType() {
    return de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries.class;
  }

  /**
   * One resolved channel hit: the 5-tuple + the containing container id.
   * Bag-of-fields shape, ordered by symbolic name ASC.
   */
  public record ResolvedChannel(
    long containerId,
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field
  ) {}

  /**
   * Resolve all channels under a DataObject (by appId) whose annotation
   * carries {@code propertyIRI = predicateIri}. Returns an empty list when
   * the DataObject has no TimeseriesReference, no matching channel
   * annotation, or no annotation at all.
   *
   * <p>Caller decides which hit to use; documented contract on the REST
   * surface is "first by symbolicName ASC". Ordering is enforced here so
   * downstream picks are deterministic without further sort.
   *
   * @param dataObjectAppId DataObject appId (UUID v7 string)
   * @param predicateIri    canonical channel-key annotation predicate IRI
   * @return matching channels ordered by symbolicName ASC; empty list when none
   */
  public List<ResolvedChannel> resolveChannelsByPredicate(String dataObjectAppId, String predicateIri) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank() ||
        predicateIri == null || predicateIri.isBlank()) {
      return List.of();
    }
    String cypher =
      "MATCH (d:DataObject {appId: $doAppId})-[:has_reference]->(r:TimeseriesReference)-[:has_payload]->(ts:Timeseries) " +
      "MATCH (r)-[:is_in_container]->(c:TimeseriesContainer) " +
      "MATCH (at:AnnotatableTimeseries {appId: ts.appId})-[:has_annotation]->(a:SemanticAnnotation) " +
      "WHERE a.propertyIRI = $piri " +
      "RETURN id(c) AS cid, " +
      "ts.measurement AS measurement, ts.device AS device, ts.location AS location, " +
      "ts.symbolicName AS symbolicName, ts.field AS field " +
      "ORDER BY ts.symbolicName ASC";
    Map<String, Object> params = Map.of(
      "doAppId", dataObjectAppId,
      "piri", predicateIri
    );
    Result result = session.query(cypher, params);
    List<ResolvedChannel> out = new ArrayList<>();
    for (Map<String, Object> row : result.queryResults()) {
      Object cid = row.get("cid");
      if (cid == null) continue;
      long containerId = ((Number) cid).longValue();
      out.add(new ResolvedChannel(
        containerId,
        asString(row.get("measurement")),
        asString(row.get("device")),
        asString(row.get("location")),
        asString(row.get("symbolicName")),
        asString(row.get("field"))
      ));
    }
    return out;
  }

  private static String asString(Object o) {
    return o == null ? null : o.toString();
  }
}
