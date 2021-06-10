package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.apache.http.HttpStatus;

import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.neo4Core.services.SubscriptionService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.USERS + "/{" + Constants.USERNAME + "}/" + Constants.SUBSCRIPTIONS)
@Log4j2
public class SubscriptionRestImpl implements SubscriptionRest {

	private SubscriptionService service = new SubscriptionService();

	@Context
	private SecurityContext securityContext;

	@Override
	public Response getAllSubscriptions(String username) {
		log.info("GET ALL request with parameters: username: {} from user {}", username,
				securityContext.getUserPrincipal().getName());

		var subscriptions = service.getAllSubscriptions(securityContext.getUserPrincipal().getName());
		var result = new ArrayList<SubscriptionIO>(subscriptions.size());
		for (var sub : subscriptions) {
			result.add(new SubscriptionIO(sub));
		}
		return Response.ok(result).build();
	}

	@Override
	public Response getSubscription(String username, long subscriptionId) {
		log.info("GET request with parameters: username: {}, id: {} from user {}", username, subscriptionId,
				securityContext.getUserPrincipal().getName());

		Subscription subscription = service.getSubscription(subscriptionId);
		return subscription != null ? Response.ok(new SubscriptionIO(subscription)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@Override
	public Response createSubscription(String username, SubscriptionIO subscription) {
		log.info("POST request with parameters: username: {} from user {}", username,
				securityContext.getUserPrincipal().getName());

		Subscription created = service.createSubscription(subscription, securityContext.getUserPrincipal().getName());
		return created != null ? Response.status(Status.CREATED).entity(new SubscriptionIO(created)).build()
				: Response.status(Status.BAD_REQUEST).build();
	}

	@Override
	public Response deleteSubscription(String username, long subscriptionId) {
		log.info("DELETE request with parameters: username: {}, id: {} from user {}", username, subscriptionId,
				securityContext.getUserPrincipal().getName());

		return service.deleteSubscription(subscriptionId) ? Response.status(204).build()
				: Response.status(HttpStatus.SC_NOT_FOUND).build();
	}

}
