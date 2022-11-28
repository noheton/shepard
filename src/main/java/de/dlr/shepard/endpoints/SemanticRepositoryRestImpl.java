package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.SemanticRepositoryIO;
import de.dlr.shepard.neo4Core.services.SemanticRepositoryService;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.SEMANTIC_REPOSITORIES)
public class SemanticRepositoryRestImpl implements SemanticRepositoryRest {
	private SemanticRepositoryService semanticRepositoryService = new SemanticRepositoryService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllSemanticRepositories() {
		var repositories = semanticRepositoryService.getAllRepositories(null);
		var result = new ArrayList<SemanticRepositoryIO>(repositories.size());
		for (var repository : repositories) {
			result.add(new SemanticRepositoryIO(repository));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.SEMANTIC_REPOSITORY_ID + "}")
	@Override
	public Response getSemanticRepository(@PathParam(Constants.SEMANTIC_REPOSITORY_ID) long semanticRepositoryId) {
		var result = semanticRepositoryService.getRepository(semanticRepositoryId);
		return Response.ok(new SemanticRepositoryIO(result)).build();
	}

	@POST
	@Override
	public Response createSemanticRepository(SemanticRepositoryIO semanticRepository) {
		var result = semanticRepositoryService.createRepository(semanticRepository,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new SemanticRepositoryIO(result)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.SEMANTIC_REPOSITORY_ID + "}")
	@Subscribable
	@Override
	public Response deleteSemanticRepository(@PathParam(Constants.SEMANTIC_REPOSITORY_ID) long semanticRepositoryId) {
		var result = semanticRepositoryService.deleteRepository(semanticRepositoryId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

}
