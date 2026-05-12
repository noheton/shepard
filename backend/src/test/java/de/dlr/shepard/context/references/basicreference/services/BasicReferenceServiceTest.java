package de.dlr.shepard.context.references.basicreference.services;

import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aBasicReference;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aDataObject;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aUser;
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
  private final User user = aUser().username("Testuser").build();

  @Test
  public void getBasicReferenceByShepardIdTest_successful() {
    BasicReference ref = aBasicReference().id(1L).shepardId(15L).build();
    DataObject dataObject = aDataObject().id(5432L).shepardId(54321L).build();
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);

    BasicReference actual = service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId());
    assertEquals(ref, actual);
  }

  @Test
  public void getBasicReferenceByShepardIdNotFoundTest() {
    BasicReference ref = aBasicReference().id(1L).shepardId(15L).build();
    DataObject dataObject = aDataObject().id(5432L).shepardId(54321L).build();
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
    BasicReference ref = aBasicReference().id(1L).shepardId(15L).deleted(true).build();
    DataObject dataObject = aDataObject().id(5432L).shepardId(54321L).build();
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
    var ref1 = aBasicReference().id(1L).shepardId(15L).build();
    var ref2 = aBasicReference().id(2L).shepardId(25L).build();
    QueryParamHelper params = new QueryParamHelper().withName("test");
    Long dataObjectShepardId = 2005L;

    when(dao.findByDataObjectShepardId(dataObjectShepardId, params)).thenReturn(List.of(ref1, ref2));

    var actual = service.getAllBasicReferences(collectionId, dataObjectShepardId, params);
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    Date date = new Date(30L);
    BasicReference ref = aBasicReference().id(1L).shepardId(15L).build();
    DataObject dataObject = aDataObject().id(5432L).shepardId(54321L).build();
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);

    assertDoesNotThrow(() -> service.deleteReference(collectionId, dataObject.getShepardId(), ref.getShepardId()));
  }
}
