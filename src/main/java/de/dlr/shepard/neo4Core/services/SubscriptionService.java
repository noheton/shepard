package de.dlr.shepard.neo4Core.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import de.dlr.shepard.neo4Core.dao.SubscriptionDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.RequestMethod;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class SubscriptionService {
	private SubscriptionDAO subscriptionDAO = new SubscriptionDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	/**
	 * Creates an Subscription and stores it in Neo4J
	 * 
	 * @param subscription to be stored
	 * @param username     of the related user
	 * @return the stored Subscription with the auto generated id
	 */
	public Subscription createSubscription(SubscriptionIO subscription, String username) {
		var user = userDAO.find(username);

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
	 * Searches the neo4j database for an Subscription
	 * 
	 * @param id identifies the searched Subscription
	 * @return the Subscription with the given id
	 */
	public Subscription getSubscription(long id) {
		Subscription subscription = subscriptionDAO.find(id);
		return subscription;
	}

	/**
	 * Updates an Subscription with new attributes
	 * 
	 * @param id           identifies the subscription
	 * @param subscription contains the new attributes.
	 * @return the old Subscription with updated attributes.
	 */
	public Subscription updateSubscription(long id, SubscriptionIO subscription) {
		var old = getSubscription(id);

		old.setCallbackURL(subscription.getCallbackURL());
		old.setName(subscription.getName());
		old.setRequestMethod(subscription.getRequestMethod());
		old.setSubscribedURL(subscription.getSubscribedURL());
		return subscriptionDAO.createOrUpdate(old);
	}

	/**
	 * Delete the given subscription
	 * 
	 * @param subscriptionId identifies the Subscription to be deleted
	 * @return a boolean to identify if the Subscription was successfully removed
	 */
	public boolean deleteSubscription(long subscriptionId) {
		return subscriptionDAO.delete(subscriptionId);
	}

	/**
	 * Searches the database for subscriptions.
	 * 
	 * @param username The name of the user
	 * @return a List of Subscriptions
	 */
	public List<Subscription> getAllSubscriptions(String username) {
		var user = userDAO.find(username);
		if (user != null) {
			return user.getSubscriptions();
		}
		return Collections.emptyList();
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
		subscriptions.forEach(result::add);
		return result;
	}
}
