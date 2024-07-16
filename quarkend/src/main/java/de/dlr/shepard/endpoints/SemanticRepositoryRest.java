package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.SemanticRepositoryIO;
import de.dlr.shepard.neo4Core.orderBy.SemanticRepositoryAttributes;
import de.dlr.shepard.util.Constants;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;

public interface SemanticRepositoryRest {
  @Tag(name = Constants.SEMANTIC_REPOSITORY)
  @Operation(description = "Get all semantic repositories")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticRepositoryIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllSemanticRepositories(
    String name,
    Integer page,
    Integer size,
    SemanticRepositoryAttributes orderAttribute,
    Boolean orderDesc
  );

  @Tag(name = Constants.SEMANTIC_REPOSITORY)
  @Operation(description = "Get semantic repository")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getSemanticRepository(long semanticRepositoryId);

  @Tag(name = Constants.SEMANTIC_REPOSITORY)
  @Operation(description = "Create a new semantic repository")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createSemanticRepository(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class))
    ) @Valid SemanticRepositoryIO semanticRepository
  );

  @Tag(name = Constants.SEMANTIC_REPOSITORY)
  @Operation(description = "Delete semantic repository")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteSemanticRepository(long semanticRepositoryId);
}
