package de.dlr.shepard.common.subscription.services;

import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.subscription.daos.SubscriptionDAO;
import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.RequestMethod;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

@RequestScoped
public class SubscriptionService {

  @Inject
  SubscriptionDAO subscriptionDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  /**
   * Creates a Subscription and stores it in Neo4J
   *
   * @param subscription to be stored
   * @param username of the related user
   * @return the stored Subscription with the auto generated id
   * @throws InvalidPathException if the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request
   */
  public Subscription createSubscription(SubscriptionIO subscription, String username) {
    var user = userService.getUser(username);
    userService.assertCurrentUserEquals(username);

    var toCreate = new Subscription();
    toCreate.setCallbackURL(subscription.getCallbackURL());
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setName(subscription.getName());
    toCreate.setRequestMethod(subscription.getRequestMethod());
    toCreate.setSubscribedURL(subscription.getSubscribedURL());
    return subscriptionDAO.createOrUpdate(toCreate);
  }

  /**
   * Searches the neo4j database for a Subscription
   *
   * @param id identifies the searched Subscription
   * @param username of the related user
   * @return the Subscription with the given id
   * @throws InvalidPathException if the subscription or the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request or the subscription does not belong to the user
   */
  public Subscription getSubscription(long id, String username) {
    userService.getUser(username);
    userService.assertCurrentUserEquals(username);

    Subscription subscription = subscriptionDAO.findByNeo4jId(id);
    if (subscription == null) {
      throw new InvalidPathException("ID ERROR - Subscription does not exist");
    }
    if (!subscription.getCreatedBy().getUsername().equals(username)) {
      throw new InvalidAuthException("You do not have permissions for this Subscription.");
    }
    return subscription;
  }

  /**
   * Delete the given subscription
   *
   * @param subscriptionId identifies the Subscription to be deleted
   * @param username of the related user
   * @return a boolean to identify if the Subscription was successfully removed
   * @throws InvalidPathException if the subscription or the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request or the subscription does not belong to the user
   */
  public boolean deleteSubscription(long subscriptionId, String username) {
    getSubscription(subscriptionId, username);

    return subscriptionDAO.deleteByNeo4jId(subscriptionId);
  }

  /**
   * Searches the database for subscriptions.
   *
   * @param username The name of the user
   * @return a List of Subscriptions
   * @throws InvalidPathException if the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request
   */
  public List<Subscription> getAllSubscriptions(String username) {
    var user = userService.getUser(username);
    userService.assertCurrentUserEquals(username);

    return user.getSubscriptions();
  }

  /**
   * Return all subscriptions matching a given request.
   *
   * @param method The request method to match against
   * @return A list of matching subscriptions
   */
  public List<Subscription> getMatchingSubscriptions(RequestMethod method) {
    Filter methodFilter = new Filter("requestMethod", ComparisonOperator.EQUALS, method);
    var subscriptions = subscriptionDAO.findMatching(methodFilter);
    var result = new ArrayList<Subscription>(subscriptions.size());
    result.addAll(subscriptions);
    return result;
  }
}
