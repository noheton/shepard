package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import org.apache.http.HttpStatus;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.orderBy.CollectionAttributes;
import de.dlr.shepard.neo4Core.services.CollectionService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
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
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size,
			@QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) CollectionAttributes orderBy,
			@QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc) {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());

		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (orderBy != null)
			params = params.withOrderByAttribute(orderBy, orderDesc);
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
