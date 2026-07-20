package de.dlr.shepard.context.version.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.version.entities.Version;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class VersionDAOQuarkusTest {

  @Inject
  VersionDAO versionDAO;

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  CollectionService collectionService;

  @Test
  @Transactional
  public void createLinkTest() {
    UUID collectionNameUUID = UUID.randomUUID();
    String collectionName = "collection" + collectionNameUUID;
    UUID versionUUID = UUID.randomUUID();
    String setUpQuery =
      "CREATE (v:Version), (c:Collection:VersionableEntity) SET c.name='" +
      collectionName +
      "', v.uid='" +
      versionUUID +
      "'";
    versionDAO.runQuery(setUpQuery, Collections.emptyMap());
    String findCollectionQuery = "MATCH (c:Collection) WHERE c.name='" + collectionName + "' RETURN c";
    var collections = collectionDAO.findByQuery(findCollectionQuery, Collections.emptyMap());
    Iterator<Collection> collectionIterator = collections.iterator();
    Collection collection = collectionIterator.next();
    long collectionId = collection.getId();
    versionDAO.createLink(collectionId, versionUUID);
    String testLinkQuery =
      "MATCH (ve:VersionableEntity)-[:has_version]->(v:Version) WHERE id(ve)=" +
      collectionId +
      " AND v.uid='" +
      versionUUID +
      "' RETURN ve";
    var linkTestmonial = collectionDAO.findByQuery(testLinkQuery, Collections.emptyMap());
    assertEquals(linkTestmonial.iterator().next().getId(), collectionId);
  }

  /**
   * SUPERNODE-F1-VERSION regression against real Neo4j (the mockito tests only
   * prove the query string is built, not that it runs). Validates:
   * <ul>
   *   <li>{@code find(uid)} resolves by the String form of the uid (the raw
   *       Cypher param is not run through {@code UuidStringConverter}) — a
   *       mismatch here 500s in prod while every mockito test stays green;</li>
   *   <li>the directed OUTGOING return hydrates the two OUTGOING single edges
   *       {@code createdBy} + {@code predecessor} that every caller reads;</li>
   *   <li>the INCOMING {@code has_version} edges (the ~25k supernode fan-in,
   *       simulated here by 3 DataObjects) are NOT needed for correctness —
   *       find / findHEADVersion / findAllVersions all return the fully-hydrated
   *       Version regardless.</li>
   * </ul>
   */
  @Test
  @Transactional
  public void find_hydratesOutgoingEdges_ignoresIncomingHasVersion() {
    UUID headUid = UUID.randomUUID();
    UUID predUid = UUID.randomUUID();
    String username = "tester-" + UUID.randomUUID();
    long shepardId = System.nanoTime();

    String setup =
      "CREATE (u:User {username:'" +
      username +
      "'}), " +
      "(pred:Version {uid:'" +
      predUid +
      "', name:'v1', isHEADVersion:false}), " +
      "(head:Version {uid:'" +
      headUid +
      "', name:'v2', isHEADVersion:true}), " +
      "(col:Collection:VersionableEntity {shepardId:" +
      shepardId +
      ", name:'col-" +
      shepardId +
      "', deleted:false}), " +
      "(head)-[:created_by]->(u), (head)-[:has_predecessor]->(pred), (col)-[:has_version]->(head), " +
      // simulate the incoming has_version supernode fan-in (must NOT be traversed)
      "(:DataObject)-[:has_version]->(head), (:DataObject)-[:has_version]->(head), (:DataObject)-[:has_version]->(head)";
    versionDAO.runQuery(setup, Collections.emptyMap());

    // find(uid): resolves by String uid + hydrates createdBy and predecessor
    Version found = versionDAO.find(headUid);
    assertNotNull(found, "find(uid) must resolve the Version by its String uid");
    assertEquals("v2", found.getName());
    assertNotNull(found.getCreatedBy(), "createdBy must be hydrated by the OUTGOING return");
    assertEquals(username, found.getCreatedBy().getUsername());
    assertNotNull(found.getPredecessor(), "predecessor must be hydrated by the OUTGOING return");
    assertEquals(predUid, found.getPredecessor().getUid());

    // findHEADVersion(collectionId): head resolves + predecessor hydrated
    Version head = versionDAO.findHEADVersion(shepardId);
    assertNotNull(head, "findHEADVersion must resolve the HEAD Version");
    assertEquals(headUid, head.getUid());
    assertNotNull(head.getPredecessor());
    assertEquals(predUid, head.getPredecessor().getUid());

    // findAllVersions(collectionId): single query, version returned with createdBy hydrated
    List<Version> all = versionDAO.findAllVersions(shepardId);
    assertTrue(all.stream().anyMatch(v -> headUid.equals(v.getUid())), "findAllVersions must return the linked version");
    Version headFromList = all.stream().filter(v -> headUid.equals(v.getUid())).findFirst().orElseThrow();
    assertEquals(username, headFromList.getCreatedBy().getUsername());
  }
}
