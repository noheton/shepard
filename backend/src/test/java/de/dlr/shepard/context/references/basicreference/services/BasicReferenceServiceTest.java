package de.dlr.shepard.context.references.basicreference.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.daos.BasicReferenceDAO;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class BasicReferenceServiceTest {

  @InjectMock
  BasicReferenceDAO dao;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  BasicReferenceService service;

  private final long collectionId = 123456L;
  private final User user = new User("Testuser");

  @Test
  public void getBasicReferenceByShepardIdTest_successful() {
    BasicReference ref = new BasicReference(1L);
    ref.setShepardId(15L);

    DataObject dataObject = new DataObject(5432L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);

    BasicReference actual = service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId());
    assertEquals(ref, actual);
  }

  @Test
  public void getBasicReferenceByShepardIdNotFoundTest() {
    BasicReference ref = new BasicReference(1L);
    ref.setShepardId(15L);

    DataObject dataObject = new DataObject(5432L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId())).thenReturn(null);

    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId())
    );
    assertEquals("ID ERROR - Basic Reference with id 15 is null or deleted", ex.getMessage());
  }

  @Test
  public void getBasicReferenceByShepardIdIsDeletedTest() {
    BasicReference ref = new BasicReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);

    DataObject dataObject = new DataObject(5432L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);

    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId())
    );
    assertEquals("ID ERROR - Basic Reference with id 15 is null or deleted", ex.getMessage());
  }

  @Test
  public void getAllBasicReferencesByShepardIdsTest() {
    var ref1 = new BasicReference(1L);
    ref1.setShepardId(15L);
    var ref2 = new BasicReference(2L);
    ref2.setShepardId(25L);
    var ref3 = new BasicReference(3L);
    ref3.setShepardId(35L);
    ref3.setDeleted(true);
    QueryParamHelper params = new QueryParamHelper().withName("test");
    Long dataObjectShepardId = 2005L;

    when(dao.findByDataObjectShepardId(dataObjectShepardId, params)).thenReturn(List.of(ref1, ref2));

    var actual = service.getAllBasicReferences(collectionId, dataObjectShepardId, params);
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    Date date = new Date(30L);
    BasicReference ref = new BasicReference(1L);
    ref.setShepardId(15L);

    DataObject dataObject = new DataObject(5432L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    BasicReference expected = new BasicReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);

    assertDoesNotThrow(() -> service.deleteReference(collectionId, dataObject.getShepardId(), ref.getShepardId()));
  }
}
