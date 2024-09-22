package de.dlr.shepard.neo4Core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.VersionIO;
import de.dlr.shepard.util.DateHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@QuarkusComponentTest
public class VersionServiceTest extends BaseTestCase {

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  CollectionService collectionService;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  UUID uuid;

  @Captor
  ArgumentCaptor<Version> newVersionCaptor;

  @Captor
  ArgumentCaptor<Version> HEADVersionCaptor;

  @Captor
  ArgumentCaptor<Collection> collectionCaptor;

  @InjectMock
  CollectionDAO collectionDAO;

  @Inject
  VersionService service;

  @Test
  public void getAllVersionsTest() {
    Version ver1 = new Version();
    Version ver2 = new Version();
    ver1.setName("name1");
    ver2.setName("name2");
    long collectionId = 10L;
    when(versionDAO.findAllVersions(collectionId)).thenReturn(List.of(ver1, ver2));
    List<Version> allVersions = service.getAllVersions(collectionId);
    assertThat(allVersions.contains(ver1));
    assertThat(allVersions.contains(ver2));
  }

  @Test
  public void createVersionTest() {
    long collectionId = 15L;
    Collection collection = new Collection(collectionId);
    VersionIO versionIO = new VersionIO();
    versionIO.setDescription("new Version");
    String username = "username";
    User user = new User(username);
    Version HEADVersion = new Version();
    HEADVersion.setName("HEADVersion");
    Version newVersion = new Version();
    newVersion.setName("new Version");
    UUID HeadVersionUUID = new UUID(2L, 3L);
    HEADVersion.setUid(HeadVersionUUID);
    Version predecessorVersion = new Version();
    predecessorVersion.setName("predecessor");
    UUID predecessorUUID = new UUID(1L, 2L);
    predecessorVersion.setUid(predecessorUUID);
    HEADVersion.setPredecessor(predecessorVersion);
    Date date = new Date(10L);
    when(versionDAO.findHEADVersion(collectionId)).thenReturn(HEADVersion);
    when(userDAO.find(username)).thenReturn(user);
    when(collectionService.getCollectionByShepardId(collectionId, null)).thenReturn(collection);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.createOrUpdate(any())).thenReturn(predecessorVersion);
    service.createVersion(collectionId, versionIO, username);
    verify(versionDAO, times(2)).createOrUpdate(newVersionCaptor.capture());
    verify(collectionDAO, times(1)).createOrUpdate(any());
  }

  @Test
  public void copyDataObjectsWithParentsAndPredecessorsTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(0L, 1L);
    when(versionDAO.copyDataObjectsWithParentsAndPredecessors(sourceVersionUID, targetVersionUID)).thenReturn(true);
    assertTrue(service.copyDataObjectsWithParentsAndPredecessors(sourceVersionUID, targetVersionUID));
  }

  @Test
  public void getVersionTest() {
    UUID VersionUID = new UUID(0L, 1L);
    Version version = new Version(VersionUID);
    when(versionDAO.find(VersionUID)).thenReturn(version);
    assertEquals(version, service.getVersion(VersionUID));
  }
}
