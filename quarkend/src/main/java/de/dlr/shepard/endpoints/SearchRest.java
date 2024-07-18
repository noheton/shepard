package de.dlr.shepard.endpoints;

import de.dlr.shepard.search.container.ContainerSearchBody;
import de.dlr.shepard.search.container.ContainerSearchResult;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.user.UserSearchBody;
import de.dlr.shepard.search.user.UserSearchResult;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface SearchRest {
  @Tag(name = Constants.SEARCH)
  @Operation(description = "search")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ResponseBody.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response search(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SearchBody.class))
    ) @Valid SearchBody body
  );

  @Tag(name = Constants.SEARCH)
  @Operation(description = "Search containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ContainerSearchResult.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response searchContainers(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ContainerSearchBody.class))
    ) @Valid ContainerSearchBody containerSearchBody
  );

  @Tag(name = Constants.SEARCH)
  @Operation(description = "Search users")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserSearchResult.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response searchUsers(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserSearchBody.class))
    ) @Valid UserSearchBody userSearchBody
  );
}
