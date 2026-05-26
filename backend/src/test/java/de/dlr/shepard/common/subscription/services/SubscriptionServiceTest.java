package de.dlr.shepard.common.subscription.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.subscription.daos.SubscriptionDAO;
import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.RequestMethod;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.cypher.Filter;

@QuarkusComponentTest
public class SubscriptionServiceTest {

  @InjectMock
  SubscriptionDAO dao;

  @InjectMock
  UserService userService;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  SubscriptionService service;

  @Test
  public void getSubscriptionTest() {
    var sub = new Subscription(1L);
    var subscriptionUser = new User("bob");
    sub.setCreatedBy(subscriptionUser);
    when(dao.findByNeo4jId(1L)).thenReturn(sub);
    var actual = service.getSubscription(1L, "bob");
    assertEquals(sub, actual);
  }

  @Test
  public void getSubscriptionTestNull() {
    when(dao.findByNeo4jId(1L)).thenThrow(InvalidPathException.class);

    assertThrows(InvalidPathException.class, () -> service.getSubscription(1L, "bob"));
  }

  @Test
  public void getAllSubscriptionsTest() {
    var sub = new Subscription(1L);
    var user = new User("bob");
    user.setSubscriptions(List.of(sub));

    when(userService.getUser("bob")).thenReturn(user);
    var actual = service.getAllSubscriptions("bob");

    assertEquals(List.of(sub), actual);
  }

  @Test
  public void getMatchingSubscriptionsTest() {
    var sub = new Subscription(1L);

    when(dao.countAll()).thenReturn(1L);
    // unfortunately two equally created filters are not equal,
    // so we have to use any here
    when(dao.findMatching(any(Filter.class))).thenReturn(List.of(sub));
    var actual = service.getMatchingSubscriptions(RequestMethod.GET);

    assertEquals(List.of(sub), actual);
  }

  @Test
  public void getMatchingSubscriptions_emptyRegistry_skipsIndexScan() {
    when(dao.countAll()).thenReturn(0L);

    var actual = service.getMatchingSubscriptions(RequestMethod.GET);

    assertEquals(List.of(), actual);
    verify(dao, never()).findMatching(any(Filter.class));
  }

  @Test
  public void getAllSubscriptionsTest_noUser() {
    when(userService.getUser("bob")).thenThrow(InvalidPathException.class);

    assertThrows(InvalidPathException.class, () -> service.getAllSubscriptions("bob"));
  }

  @Test
  public void deleteSubscriptionTest() {
    var sub = new Subscription(1L);
    var subscriptionUser = new User("bob");
    sub.setCreatedBy(subscriptionUser);
    when(dao.findByNeo4jId(1L)).thenReturn(sub);
    when(dao.deleteByNeo4jId(1L)).thenReturn(true);
    var actual = service.deleteSubscription(1L, "bob");

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

    when(userService.getUser("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);

    var actual = service.createSubscription(input, "bob");
    assertEquals(created, actual);
  }
}
