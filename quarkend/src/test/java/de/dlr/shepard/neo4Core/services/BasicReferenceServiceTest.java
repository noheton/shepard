package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class BasicReferenceServiceTest extends BaseTestCase {

  @Mock
  private BasicReferenceDAO dao;

  @Mock
  private UserDAO userDAO;

  @Mock
  private DateHelper dateHelper;

  @InjectMocks
  private BasicReferenceService service;

  @Test
  public void getBasicReferenceByShepardIdTest_successful() {
    BasicReference ref = new BasicReference(1L);
    ref.setShepardId(15L);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    BasicReference actual = service.getReferenceByShepardId(ref.getShepardId());
    assertEquals(ref, actual);
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
    var actual = service.getAllBasicReferencesByDataObjectShepardId(dataObjectShepardId, params);
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("bob");
    Date date = new Date(30L);
    BasicReference ref = new BasicReference(1L);
    ref.setShepardId(15L);
    BasicReference expected = new BasicReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    boolean actual = service.deleteReferenceByShepardId(ref.getShepardId(), user.getUsername());

    verify(dao).createOrUpdate(expected);
    assertTrue(actual);
  }
}
