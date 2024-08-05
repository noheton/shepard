package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.SubscriptionDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.RequestMethod;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.cypher.Filter;

public class SubscriptionServiceTest extends BaseTestCase {

  @Mock
  private SubscriptionDAO dao;

  @Mock
  private UserDAO userDAO;

  @Mock
  private DateHelper dateHelper;

  @InjectMocks
  private SubscriptionService service;

  @Test
  public void getSubscriptionTest() {
    var sub = new Subscription(1L);
    when(dao.findByNeo4jId(1L)).thenReturn(sub);
    var actual = service.getSubscription(1L);
    assertEquals(sub, actual);
  }

  @Test
  public void getSubscriptionTestNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);
    var actual = service.getSubscription(1L);
    assertNull(actual);
  }

  @Test
  public void getAllSubscriptionsTest() {
    var sub = new Subscription(1L);
    var user = new User("bob");
    user.setSubscriptions(List.of(sub));

    when(userDAO.find("bob")).thenReturn(user);
    var actual = service.getAllSubscriptions("bob");

    assertEquals(List.of(sub), actual);
  }

  @Test
  public void getMatchingSubscriptionsTest() {
    var sub = new Subscription(1L);

    // unfortunately two equally created filters are not equal,
    // so we have to use any here
    when(dao.findMatching(any(Filter.class))).thenReturn(List.of(sub));
    var actual = service.getMatchingSubscriptions(RequestMethod.GET);

    assertEquals(List.of(sub), actual);
  }

  @Test
  public void getAllSubscriptionsTest_noUser() {
    when(userDAO.find("bob")).thenReturn(null);
    var actual = service.getAllSubscriptions("bob");

    assertEquals(Collections.emptyList(), actual);
  }

  @Test
  public void deleteSubscriptionTest() {
    when(dao.deleteByNeo4jId(1L)).thenReturn(true);
    var actual = service.deleteSubscription(1L);

    assertTrue(actual);
  }

  @Test
  public void createTest() {
    var user = new User("bob");
    var date = new Date(30L);

    var input = new SubscriptionIO() {
      {
        setCallbackURL("callback");
        setName("MySub");
        setRequestMethod(RequestMethod.GET);
        setSubscribedURL("subUrl");
      }
    };

    var toCreate = new Subscription() {
      {
        setCallbackURL("callback");
        setName("MySub");
        setRequestMethod(RequestMethod.GET);
        setSubscribedURL("subUrl");
        setCreatedAt(date);
        setCreatedBy(user);
      }
    };

    var created = new Subscription() {
      {
        setId(1L);
        setCallbackURL("callback");
        setName("MySub");
        setRequestMethod(RequestMethod.GET);
        setSubscribedURL("subUrl");
        setCreatedAt(date);
        setCreatedBy(user);
      }
    };

    when(userDAO.find("bob")).thenReturn(user);
    when(DateHelper.getDate()).thenReturn(date);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);

    var actual = service.createSubscription(input, "bob");
    assertEquals(created, actual);
  }

  @Test
  public void updateTest() {
    var user = new User("bob");
    var date = new Date(30L);

    var input = new SubscriptionIO() {
      {
        setCallbackURL("newCallback");
        setName("newMySub");
        setRequestMethod(RequestMethod.PUT);
        setSubscribedURL("newSubUrl");
      }
    };

    var old = new Subscription() {
      {
        setCallbackURL("callback");
        setName("MySub");
        setRequestMethod(RequestMethod.GET);
        setSubscribedURL("subUrl");
        setCreatedAt(date);
        setCreatedBy(user);
      }
    };

    var updated = new Subscription() {
      {
        setCallbackURL("newCallback");
        setName("newMySub");
        setRequestMethod(RequestMethod.PUT);
        setSubscribedURL("newSubUrl");
        setCreatedAt(date);
        setCreatedBy(user);
      }
    };

    when(dao.findByNeo4jId(1L)).thenReturn(old);
    when(dao.createOrUpdate(updated)).thenReturn(updated);

    var actual = service.updateSubscription(1L, input);
    assertEquals(updated, actual);
  }
}
