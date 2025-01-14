package de.dlr.shepard.context.version.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.services.CollectionService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.Iterator;
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
}
