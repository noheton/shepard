package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.io.EventIO;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.neo4Core.services.SubscriptionService;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
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
import java.util.ArrayList;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.callbacks.Callback;
import org.eclipse.microprofile.openapi.annotations.callbacks.CallbackOperation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.links.Link;
import org.eclipse.microprofile.openapi.annotations.links.LinkParameter;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.USERS + "/{" + Constants.USERNAME + "}/" + Constants.SUBSCRIPTIONS)
public class SubscriptionRest {

  private SubscriptionService service = new SubscriptionService();

  @GET
  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Get all subscriptions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SubscriptionIO.class))
  )
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
  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Get subscription")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = SubscriptionIO.class))
  )
  public Response getSubscription(
    @PathParam(Constants.USERNAME) String username,
    @PathParam(Constants.SUBSCRIPTION_ID) long subscriptionId
  ) {
    Subscription subscription = service.getSubscription(subscriptionId);
    return subscription != null
      ? Response.ok(new SubscriptionIO(subscription)).build()
      : Response.status(Status.NOT_FOUND).build();
  }

  @POST
  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Create a new subscription")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SubscriptionIO.class)),
    links = @Link(
      name = "unsubscribe",
      operationId = "deleteSubscription",
      parameters = @LinkParameter(name = Constants.SUBSCRIPTION_ID, expression = "$response.body#/id")
    )
  )
  @Callback(
    name = "SubscriptionIO",
    operations = @CallbackOperation(
      summary = "Notify the client about an event",
      method = "post",
      responses = @APIResponse(responseCode = "200", description = "Notification received"),
      requestBody = @RequestBody(
        description = "Notification about an event",
        content = @Content(schema = @Schema(implementation = EventIO.class)),
        required = true
      )
    ),
    callbackUrlExpression = "{$request.body#/callbackUrl}"
  )
  public Response createSubscription(
    @PathParam(Constants.USERNAME) String username,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SubscriptionIO.class))
    ) @Valid SubscriptionIO subscription
  ) {
    Subscription created = service.createSubscription(subscription, username);
    return created != null
      ? Response.status(Status.CREATED).entity(new SubscriptionIO(created)).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @DELETE
  @Path("/{" + Constants.SUBSCRIPTION_ID + "}")
  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Delete subscription")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  public Response deleteSubscription(
    @PathParam(Constants.USERNAME) String username,
    @PathParam(Constants.SUBSCRIPTION_ID) long subscriptionId
  ) {
    return service.deleteSubscription(subscriptionId)
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.NOT_FOUND).build();
  }
}
