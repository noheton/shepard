package de.dlr.shepard.common.subscription.endpoints;

import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.subscription.io.EventIO;
import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.common.subscription.services.SubscriptionService;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.SHEPARD_API + "/" + Constants.USERS + "/{" + Constants.USERNAME + "}/" + Constants.SUBSCRIPTIONS)
@RequestScoped
public class SubscriptionRest {

  @Inject
  SubscriptionService subscriptionService;

  @GET
  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Get all subscriptions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SubscriptionIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERNAME)
  public Response getAllSubscriptions(@PathParam(Constants.USERNAME) @NotBlank String username) {
    var subscriptions = subscriptionService.getAllSubscriptions(username);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERNAME)
  @Parameter(name = Constants.SUBSCRIPTION_ID)
  public Response getSubscription(
    @PathParam(Constants.USERNAME) @NotBlank String username,
    @PathParam(Constants.SUBSCRIPTION_ID) @NotNull @PositiveOrZero Long subscriptionId
  ) {
    Subscription subscription = subscriptionService.getSubscription(subscriptionId, username);
    return Response.ok(new SubscriptionIO(subscription)).build();
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
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
  @Parameter(name = Constants.USERNAME)
  public Response createSubscription(
    @PathParam(Constants.USERNAME) @NotBlank String username,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SubscriptionIO.class))
    ) @Valid SubscriptionIO subscription
  ) {
    Subscription created = subscriptionService.createSubscription(subscription, username);
    return Response.status(Status.CREATED).entity(new SubscriptionIO(created)).build();
  }

  @DELETE
  @Path("/{" + Constants.SUBSCRIPTION_ID + "}")
  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Delete subscription")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERNAME)
  @Parameter(name = Constants.SUBSCRIPTION_ID)
  public Response deleteSubscription(
    @PathParam(Constants.USERNAME) @NotBlank String username,
    @PathParam(Constants.SUBSCRIPTION_ID) @NotNull @PositiveOrZero Long subscriptionId
  ) {
    subscriptionService.deleteSubscription(subscriptionId, username);
    return Response.status(Status.NO_CONTENT).build();
  }
}
