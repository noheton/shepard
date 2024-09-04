package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.URIReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.URIReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.util.DateHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class URIReferenceServiceTest {

  @InjectMock
  URIReferenceDAO dao;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  DataObjectDAO dataObjectDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  URIReferenceService service;

  @Test
  public void getURIReferenceByShepardIdTest_successful() {
    URIReference ref = new URIReference(1L);
    ref.setShepardId(15L);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    URIReference actual = service.getReferenceByShepardId(ref.getShepardId());
    assertEquals(ref, actual);
  }

  @Test
  public void getURIReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    URIReference actual = service.getReferenceByShepardId(shepardId);
    assertNull(actual);
  }

  @Test
  public void getURIReferenceByShepardIdTestIsDeleted() {
    Long shepardId = 15L;
    URIReference ref = new URIReference(20L);
    ref.setShepardId(shepardId);
    ref.setDeleted(true);
    when(dao.findByShepardId(shepardId)).thenReturn(ref);
    URIReference actual = service.getReferenceByShepardId(shepardId);
    assertNull(actual);
  }

  @Test
  public void getAllURIReferencesByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    URIReference ref1 = new URIReference(1L);
    ref1.setShepardId(15L);
    URIReference ref2 = new URIReference(2L);
    ref2.setShepardId(25L);
    dataObject.setReferences(List.of(ref1, ref2));
    when(dao.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));
    List<URIReference> actual = service.getAllReferencesByDataObjectShepardId(dataObject.getShepardId());
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createURIReferenceByShepardIdTest() {
    User user = new User("Bob");
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Date date = new Date(30L);
    URIReferenceIO input = new URIReferenceIO() {
      {
        setName("MyName");
        setUri("http;//example.com");
      }
    };
    URIReference toCreate = new URIReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setUri(input.getUri());
      }
    };
    URIReference created = new URIReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setUri(toCreate.getUri());
      }
    };
    URIReference createdWithShepardId = new URIReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setUri(created.getUri());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    URIReference actual = service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername());
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    URIReference ref = new URIReference(1L);
    ref.setShepardId(15L);
    URIReference expected = new URIReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    boolean actual = service.deleteReferenceByShepardId(ref.getShepardId(), user.getUsername());
    verify(dao).createOrUpdate(expected);
    assertTrue(actual);
  }
}
