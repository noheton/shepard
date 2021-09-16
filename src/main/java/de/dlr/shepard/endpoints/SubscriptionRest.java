package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.EventIO;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.callbacks.Callback;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

public interface SubscriptionRest {

	@Tag(name = Constants.SUBSCRIPTION)
	@Operation(description = "Get all subscriptions")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriptionIO.class))))
	Response getAllSubscriptions(String username);

	@Tag(name = Constants.SUBSCRIPTION)
	@Operation(description = "Get subscription")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = SubscriptionIO.class)))
	Response getSubscription(String username, long subscriptionId);

	@Tag(name = Constants.SUBSCRIPTION)
	@Operation(description = "Create a new subscription")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = SubscriptionIO.class)), links = @Link(name = "unsubscribe", operationId = "deleteSubscription", parameters = @LinkParameter(name = Constants.SUBSCRIPTION_ID, expression = "$response.body#/id")))
	@Callback(name = "SubscriptionIO", operation = @Operation(operationId = "notifyClient", summary = "Notify the client about an event", method = "post", responses = @ApiResponse(responseCode = "200", description = "Notification received"), requestBody = @RequestBody(description = "Notification about an event", content = @Content(schema = @Schema(implementation = EventIO.class)), required = true)), callbackUrlExpression = "{$request.body#/callbackUrl}")
	Response createSubscription(String username,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = SubscriptionIO.class))) SubscriptionIO subscription);

	@Tag(name = Constants.SUBSCRIPTION)
	@Operation(description = "Delete subscription")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteSubscription(String username, long subscriptionId);

}
