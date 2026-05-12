package de.dlr.shepard.provenance.daos;

import de.dlr.shepard.common.neo4j.NeoConnector;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import java.util.HashMap;
import java.util.Map;
import org.neo4j.ogm.session.Session;

/**
 * Computes the per-Collection / instance-wide "what's in here" entity
 * census that fills the {@code aidocs/55 §5} "3.2 GB files captured /
 * 18M timeseries points / 42 DataObjects added" tiles next to the
 * activity sparkline.
 *
 * <p>v1 of PROV1-content-stats ships **counts only** — bytes / point
 * totals need either (a) on-demand GridFS round-trips (expensive) or
 * (b) a stored {@code fileSize} on {@code :ShepardFile} that hasn't
 * landed yet (gated on FB1a's FileBundle rename). Defer to
 * PROV1-content-stats-2 once the FB1 rename ships.
 *
 * <p>One Cypher round-trip per scope. The query unions sub-counts
 * by entity kind so the result is a single map.
 */
@RequestScoped
public class ContentCensusDAO {

  Session session;

  @PostConstruct
  void init() {
    // Same pattern as GenericDAO — pull the OGM session from
    // NeoConnector rather than CDI (the Session bean isn't exposed
    // as a CDI producer in this codebase).
    session = NeoConnector.getInstance().getNeo4jSession();
  }

  /** Test seam — let tests inject a mock Session. */
  void setSessionForTest(Session session) {
    this.session = session;
  }

  /**
   * Census narrowed to one Collection (resolved by {@code appId}).
   * Returns a fixed-keyed map: {@code dataObjects} /
   * {@code fileReferences} / {@code timeseriesReferences} /
   * {@code structuredDataReferences} / {@code spatialDataReferences} /
   * {@code labJournalEntries}. Missing or unreachable entity kinds
   * yield {@code 0}.
   */
  public Map<String, Long> censusForCollection(String collectionAppId) {
    String cypher =
      "MATCH (c:Collection {appId: $cAppId}) " +
      "OPTIONAL MATCH (c)-[:HAS_DATAOBJECT*0..]->(d:DataObject) " +
      "WITH c, count(DISTINCT d) AS dataObjects " +
      "OPTIONAL MATCH (c)-[:HAS_DATAOBJECT*0..]->(:DataObject)-[:HAS_REFERENCE]->(fr:FileReference) " +
      "WITH c, dataObjects, count(DISTINCT fr) AS fileReferences " +
      "OPTIONAL MATCH (c)-[:HAS_DATAOBJECT*0..]->(:DataObject)-[:HAS_REFERENCE]->(tr:TimeseriesReference) " +
      "WITH c, dataObjects, fileReferences, count(DISTINCT tr) AS timeseriesReferences " +
      "OPTIONAL MATCH (c)-[:HAS_DATAOBJECT*0..]->(:DataObject)-[:HAS_REFERENCE]->(sdr:StructuredDataReference) " +
      "WITH c, dataObjects, fileReferences, timeseriesReferences, count(DISTINCT sdr) AS structuredDataReferences " +
      "OPTIONAL MATCH (c)-[:HAS_DATAOBJECT*0..]->(:DataObject)-[:HAS_REFERENCE]->(spr:SpatialDataReference) " +
      "WITH c, dataObjects, fileReferences, timeseriesReferences, structuredDataReferences, count(DISTINCT spr) AS spatialDataReferences " +
      "OPTIONAL MATCH (c)-[:HAS_DATAOBJECT*0..]->(:DataObject)-[:HAS_LABJOURNAL_ENTRY|HAS_LABJOURNALENTRY]->(lje:LabJournalEntry) " +
      "RETURN dataObjects, fileReferences, timeseriesReferences, structuredDataReferences, spatialDataReferences, count(DISTINCT lje) AS labJournalEntries";
    var result = session.query(cypher, Map.of("cAppId", collectionAppId));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return emptyCensus();
    return toCensusMap(it.next());
  }

  /**
   * Instance-wide census — counts every entity of each kind on the graph.
   * Used by the admin dashboard.
   */
  public Map<String, Long> censusInstanceWide() {
    String cypher =
      "OPTIONAL MATCH (d:DataObject) WITH count(d) AS dataObjects " +
      "OPTIONAL MATCH (fr:FileReference) WITH dataObjects, count(fr) AS fileReferences " +
      "OPTIONAL MATCH (tr:TimeseriesReference) WITH dataObjects, fileReferences, count(tr) AS timeseriesReferences " +
      "OPTIONAL MATCH (sdr:StructuredDataReference) WITH dataObjects, fileReferences, timeseriesReferences, count(sdr) AS structuredDataReferences " +
      "OPTIONAL MATCH (spr:SpatialDataReference) WITH dataObjects, fileReferences, timeseriesReferences, structuredDataReferences, count(spr) AS spatialDataReferences " +
      "OPTIONAL MATCH (lje:LabJournalEntry) " +
      "RETURN dataObjects, fileReferences, timeseriesReferences, structuredDataReferences, spatialDataReferences, count(lje) AS labJournalEntries";
    var result = session.query(cypher, Map.of());
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return emptyCensus();
    return toCensusMap(it.next());
  }

  private Map<String, Long> emptyCensus() {
    Map<String, Long> empty = new HashMap<>();
    for (String key : KEYS) empty.put(key, 0L);
    return empty;
  }

  private Map<String, Long> toCensusMap(Map<String, Object> row) {
    Map<String, Long> out = new java.util.LinkedHashMap<>();
    for (String key : KEYS) {
      Object v = row.get(key);
      out.put(key, v instanceof Number n ? n.longValue() : 0L);
    }
    return out;
  }

  private static final String[] KEYS = {
    "dataObjects",
    "fileReferences",
    "timeseriesReferences",
    "structuredDataReferences",
    "spatialDataReferences",
    "labJournalEntries",
  };
}
