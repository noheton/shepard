package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.EventIO;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;
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

public interface SubscriptionRest {
  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Get all subscriptions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SubscriptionIO.class))
  )
  Response getAllSubscriptions(String username);

  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Get subscription")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = SubscriptionIO.class))
  )
  Response getSubscription(String username, long subscriptionId);

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
  Response createSubscription(
    String username,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SubscriptionIO.class))
    ) @Valid SubscriptionIO subscription
  );

  @Tag(name = Constants.SUBSCRIPTION)
  @Operation(description = "Delete subscription")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteSubscription(String username, long subscriptionId);
}
