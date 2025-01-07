package de.dlr.shepard.context.references.dataobject.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.dataobject.daos.DataObjectReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class DataObjectReferenceServiceTest {

  @InjectMock
  DataObjectReferenceDAO dao;

  @InjectMock
  DataObjectDAO dataObjectDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  VersionDAO versionDAO;

  @Inject
  DataObjectReferenceService service;

  @Test
  public void getDataObjectReferenceByShepardIdTest_successful() {
    DataObjectReference ref = new DataObjectReference(1L);
    ref.setShepardId(15L);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    DataObjectReference actual = service.getReferenceByShepardId(ref.getShepardId());
    assertEquals(ref, actual);
  }

  @Test
  public void getDataObjectReferenceByShepardIdTest_notFound() {
    Long shepardId = 1L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    var actual = service.getReferenceByShepardId(shepardId);
    assertNull(actual);
  }

  @Test
  public void getDataObjectReferenceByShepardIdTest_deleted() {
    var ref = new DataObjectReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    DataObjectReference actual = service.getReferenceByShepardId(ref.getShepardId());
    assertNull(actual);
  }

  @Test
  public void getAllDataObjectReferencesByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    DataObjectReference ref1 = new DataObjectReference(1L);
    ref1.setShepardId(15L);
    DataObjectReference ref2 = new DataObjectReference(2L);
    ref2.setShepardId(25L);
    DataObjectReference ref3 = new DataObjectReference(3L);
    ref3.setShepardId(35L);
    ref3.setDeleted(true);
    dataObject.setReferences(List.of(ref1, ref2, ref3));
    when(dao.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));
    List<DataObjectReference> actual = service.getAllReferencesByDataObjectShepardId(dataObject.getShepardId());
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createDataObjectReferenceByShepardIdTest() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Date date = new Date(30L);
    DataObject referenced = new DataObject(100L);
    referenced.setShepardId(1005L);
    Version version = new Version(new UUID(1L, 2L));
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    DataObjectReference toCreate = new DataObjectReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setReferencedDataObject(referenced);
        setRelationship(input.getRelationship());
      }
    };
    DataObjectReference created = new DataObjectReference() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(toCreate.getName());
        setReferencedDataObject(toCreate.getReferencedDataObject());
        setRelationship(toCreate.getRelationship());
      }
    };
    DataObjectReference createdWithShepardId = new DataObjectReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(created.getName());
        setReferencedDataObject(created.getReferencedDataObject());
        setRelationship(created.getRelationship());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectDAO.findLightByShepardId(referenced.getShepardId())).thenReturn(referenced);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    DataObjectReference actual = service.createReferenceByShepardId(
      dataObject.getShepardId(),
      input,
      user.getUsername()
    );
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createDataObjectReferenceByShepardIdTest_ReferencedIsNull() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Long nullDataObjectShepardId = 100L;
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(nullDataObjectShepardId);
        setRelationship("MyRelationship");
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectDAO.findLightByShepardId(nullDataObjectShepardId)).thenReturn(null);
    assertThrows(InvalidBodyException.class, () ->
      service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void createDataObjectReferenceByShepardIdTest_ReferencedIsDeleted() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    DataObject referenced = new DataObject(100L);
    referenced.setShepardId(1005L);
    referenced.setDeleted(true);
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectDAO.findLightByShepardId(referenced.getShepardId())).thenReturn(referenced);
    assertThrows(InvalidBodyException.class, () ->
      service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    DataObjectReference ref = new DataObjectReference(1L);
    ref.setShepardId(15L);
    DataObjectReference expected = new DataObjectReference(ref.getId());
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

  @Test
  public void getPayloadByShepardIdTest() {
    DataObject referenced = new DataObject(100L);
    referenced.setShepardId(1005L);
    DataObjectReference reference = new DataObjectReference(1L);
    reference.setShepardId(15L);
    reference.setReferencedDataObject(referenced);
    when(dao.findByShepardId(reference.getShepardId())).thenReturn(reference);
    when(dataObjectDAO.findByShepardId(referenced.getShepardId())).thenReturn(referenced);
    var actual = service.getPayloadByShepardId(reference.getShepardId());
    assertEquals(referenced, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_Deleted() {
    DataObject referenced = new DataObject(100L);
    referenced.setShepardId(1005L);
    referenced.setDeleted(true);
    DataObjectReference reference = new DataObjectReference(1L);
    reference.setShepardId(15L);
    reference.setReferencedDataObject(referenced);
    when(dao.findByShepardId(reference.getShepardId())).thenReturn(reference);
    when(dataObjectDAO.findByShepardId(referenced.getShepardId())).thenReturn(referenced);
    DataObject actual = service.getPayloadByShepardId(reference.getShepardId());
    assertNull(actual);
  }
}
