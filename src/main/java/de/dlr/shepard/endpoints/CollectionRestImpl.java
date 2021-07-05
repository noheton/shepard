package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.http.HttpStatus;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.services.CollectionService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Path(Constants.COLLECTIONS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Log4j2
public class CollectionRestImpl implements CollectionRest {
	private CollectionService collectionService = new CollectionService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllCollections(@QueryParam(Constants.QP_NAME) String name,
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size) {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());

		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		var collections = collectionService.getAllCollections(params);
		var result = new ArrayList<CollectionIO>(collections.size());

		for (var collection : collections) {
			result.add(new CollectionIO(collection));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.COLLECTION_ID + "}")
	@Override
	public Response getCollection(@PathParam(Constants.COLLECTION_ID) long collectionId) {
		log.info("Received GET request with parameters: collectionID: {} from user {}", collectionId,
				securityContext.getUserPrincipal().getName());

		Collection collection = collectionService.getCollection(collectionId);
		return Response.ok(new CollectionIO(collection)).build();
	}

	@POST
	@Override
	public Response createCollection(CollectionIO collection) {
		log.info("Received POST request with new entity: name: {} from user {}", collection.getName(),
				securityContext.getUserPrincipal().getName());

		Collection newCollection = collectionService.createCollection(collection,
				securityContext.getUserPrincipal().getName());

		return Response.ok(new CollectionIO(newCollection)).status(HttpStatus.SC_CREATED).build();
	}

	@PUT
	@Path("/{" + Constants.COLLECTION_ID + "}")
	@Override
	public Response updateCollection(@PathParam(Constants.COLLECTION_ID) long collectionId, CollectionIO collection) {
		log.info(
				"Received PUT request with parameters: collectionID: {} and new entity: collectionName: {} from user {}",
				collectionId, collection.getName(), securityContext.getUserPrincipal().getName());

		Collection updatedCollection = collectionService.updateCollection(collectionId, collection,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new CollectionIO(updatedCollection)).build();
	}

	@DELETE
	@Path("/{" + Constants.COLLECTION_ID + "}")
	@Override
	public Response deleteCollection(@PathParam(Constants.COLLECTION_ID) long collectionId) {
		log.info("Received DELETE request with parameters: collectionID: {} from user {}", collectionId,
				securityContext.getUserPrincipal().getName());

		return collectionService.deleteCollection(collectionId, securityContext.getUserPrincipal().getName())
				? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

}
