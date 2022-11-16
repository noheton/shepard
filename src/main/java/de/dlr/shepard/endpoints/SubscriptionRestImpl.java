package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.neo4Core.services.SubscriptionService;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.USERS + "/{" + Constants.USERNAME + "}/" + Constants.SUBSCRIPTIONS)
public class SubscriptionRestImpl implements SubscriptionRest {

	private SubscriptionService service = new SubscriptionService();

	@GET
	@Override
	public Response getAllSubscriptions(@PathParam(Constants.USERNAME) String username) {
		var subscriptions = service.getAllSubscriptions(username);
		var result = new ArrayList<SubscriptionIO>(subscriptions.size());
		for (var sub : subscriptions) {
			result.add(new SubscriptionIO(sub));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.SUBSCRIPTION_ID + "}")
	@Override
	public Response getSubscription(@PathParam(Constants.USERNAME) String username,
			@PathParam(Constants.SUBSCRIPTION_ID) long subscriptionId) {
		Subscription subscription = service.getSubscription(subscriptionId);
		return subscription != null ? Response.ok(new SubscriptionIO(subscription)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@POST
	@Override
	public Response createSubscription(@PathParam(Constants.USERNAME) String username, SubscriptionIO subscription) {
		Subscription created = service.createSubscription(subscription, username);
		return created != null ? Response.status(Status.CREATED).entity(new SubscriptionIO(created)).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@DELETE
	@Path("/{" + Constants.SUBSCRIPTION_ID + "}")
	@Override
	public Response deleteSubscription(@PathParam(Constants.USERNAME) String username,
			@PathParam(Constants.SUBSCRIPTION_ID) long subscriptionId) {
		return service.deleteSubscription(subscriptionId) ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.NOT_FOUND).build();
	}

}
