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
   * NEO-AUDIT-009 — application-scoped companion bean that caches whether any
   * subscriptions exist. {@code @RequestScoped} beans cannot hold
   * application-lifetime state, so the flag lives in this separate bean and is
   * injected here to survive across requests.
   */
  @Inject
  SubscriptionExistenceCache existenceCache;

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
    Subscription created = subscriptionDAO.createOrUpdate(toCreate);
    // NEO-AUDIT-009: a new subscription means "has subscriptions" is now true.
    existenceCache.invalidate();
    return created;
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

    boolean deleted = subscriptionDAO.deleteByNeo4jId(subscriptionId);
    // NEO-AUDIT-009: removal may have reduced count to zero; force a re-check.
    existenceCache.invalidate();
    return deleted;
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
   * <p>NEO-AUDIT-009: before walking the {@code idx_Subscription_requestMethod}
   * index, check the {@link SubscriptionExistenceCache}. Deployments with zero
   * subscriptions pay only a volatile-field read per authenticated write request
   * instead of a Neo4j round-trip.
   *
   * @param method The request method to match against
   * @return A list of matching subscriptions
   */
  public List<Subscription> getMatchingSubscriptions(RequestMethod method) {
    // Fast path: if we know there are no subscriptions, skip the DB walk.
    if (existenceCache.isValid() && !existenceCache.hasSubscriptions()) {
      return List.of();
    }

    Filter methodFilter = new Filter("requestMethod", ComparisonOperator.EQUALS, method);
    var subscriptions = subscriptionDAO.findMatching(methodFilter);

    // Update the cache based on whether ANY subscriptions exist across all methods.
    // We use the size of this method-filtered result as a proxy: if there are
    // subscriptions for this method there are certainly subscriptions overall.
    // The cache is invalidated on create/delete, so a false-empty is impossible.
    if (!existenceCache.isValid()) {
      existenceCache.update(!subscriptions.isEmpty());
    }

    var result = new ArrayList<Subscription>(subscriptions.size());
    result.addAll(subscriptions);
    return result;
  }

  /**
   * Returns the subscriptions whose URL pattern matches {@code url} for the given request method,
   * delegating the regex match to {@link SubscriptionMatcher} so the export pipeline shares the
   * exact same algorithm the runtime {@code SubscriptionFilter} uses.
   *
   * <p>Used by the RO-Crate export walker to discover, per exported entity, which subscriptions
   * would have fired had a successful {@code GET} hit that entity's canonical URL.
   *
   * @param url canonical URL to test against each candidate subscription's pattern
   * @param method request method (the export uses {@link RequestMethod#GET})
   * @return matched subscriptions in DAO order; empty list if none match
   */
  public List<Subscription> getMatchingSubscriptionsForUrl(String url, RequestMethod method) {
    if (url == null || url.isEmpty()) return List.of();
    return SubscriptionMatcher.matchAll(getMatchingSubscriptions(method), url);
  }
}
