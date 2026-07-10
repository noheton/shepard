package de.dlr.shepard.context.references.file.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO.UrdfCandidate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * URDF-FILEREF-PICKER-SEARCHABLE — unit tests for the pure Cypher-row →
 * {@link UrdfCandidate} projection. The static {@code mapRows} helper is the
 * testable core of {@code findAllUrdfCandidates} (only the {@code session.query}
 * call is untestable without a live Neo4j).
 */
class SingletonFileReferenceDAOTest {

  private Map<String, Object> row(String refAppId, String name, String fileKind,
      String doAppId, String collAppId, String collName) {
    // HashMap (not Map.of) so null values are permitted, mirroring OGM row maps.
    Map<String, Object> m = new HashMap<>();
    m.put("refAppId", refAppId);
    m.put("name", name);
    m.put("fileKind", fileKind);
    m.put("doAppId", doAppId);
    m.put("collAppId", collAppId);
    m.put("collName", collName);
    return m;
  }

  @Test
  void mapsFullRowToCandidate() {
    var rows = List.of(row("ref-kr210", "kr210-r2700-urdf", "urdf", "do-A", "coll-A", "MFFD RDK"));
    List<UrdfCandidate> out = SingletonFileReferenceDAO.mapRows(rows);
    assertEquals(1, out.size());
    var c = out.get(0);
    assertEquals("ref-kr210", c.refAppId());
    assertEquals("kr210-r2700-urdf", c.name());
    assertEquals("urdf", c.fileKind());
    assertEquals("do-A", c.dataObjectAppId());
    assertEquals("coll-A", c.collectionAppId());
    assertEquals("MFFD RDK", c.collectionName());
  }

  @Test
  void toleratesNullCollectionAndFileKind() {
    var rows = List.of(row("r1", "arm.urdf", null, "do-B", null, null));
    var c = SingletonFileReferenceDAO.mapRows(rows).get(0);
    assertNull(c.fileKind());
    assertNull(c.collectionAppId());
    assertNull(c.collectionName());
    assertEquals("arm.urdf", c.name());
  }

  @Test
  void fallsBackToAppIdWhenNameMissing() {
    var rows = List.of(row("r-noname", null, "urdf", "do-C", "coll-C", "C"));
    assertEquals("r-noname", SingletonFileReferenceDAO.mapRows(rows).get(0).name());
  }

  @Test
  void dropsRowsWithoutRefAppIdOrParentDataObject() {
    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(row("", "blank-ref", "urdf", "do-A", "coll-A", "A"));   // blank refAppId → drop
    rows.add(row(null, "null-ref", "urdf", "do-A", "coll-A", "A"));  // null refAppId → drop
    rows.add(row("r-orphan", "orphan.urdf", "urdf", "", "coll-A", "A")); // blank doAppId → drop
    rows.add(row("r-ok", "good.urdf", "urdf", "do-A", "coll-A", "A"));   // keep
    List<UrdfCandidate> out = SingletonFileReferenceDAO.mapRows(rows);
    assertEquals(1, out.size());
    assertEquals("r-ok", out.get(0).refAppId());
  }

  @Test
  void deduplicatesByRefAppId_versionableEntityGuard() {
    // Two rows for the same reference (e.g. a historical version reachable via
    // has_reference) must collapse to one — first wins.
    var rows = List.of(
      row("r-dup", "v2-name.urdf", "urdf", "do-A", "coll-A", "A"),
      row("r-dup", "v1-name.urdf", "urdf", "do-A", "coll-A", "A")
    );
    List<UrdfCandidate> out = SingletonFileReferenceDAO.mapRows(rows);
    assertEquals(1, out.size());
    assertEquals("v2-name.urdf", out.get(0).name());
  }

  @Test
  void emptyInputYieldsEmptyList() {
    assertTrue(SingletonFileReferenceDAO.mapRows(List.of()).isEmpty());
  }
}
