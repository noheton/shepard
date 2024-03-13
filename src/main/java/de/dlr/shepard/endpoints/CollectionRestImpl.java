package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.neo4Core.services.CollectionService;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.validation.Valid;
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
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

@Path(Constants.COLLECTIONS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollectionRestImpl implements CollectionRest {
	private CollectionService collectionService = new CollectionService();
	private PermissionsService permissionsService = new PermissionsService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllCollections(@QueryParam(Constants.QP_NAME) String name,
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size,
			@QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) DataObjectAttributes orderBy,
			@QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc) {
		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (orderBy != null)
			params = params.withOrderByAttribute(orderBy, orderDesc);
		var collections = collectionService.getAllCollectionsByShepardId(params,
				securityContext.getUserPrincipal().getName());

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
		Collection collection = collectionService.getCollectionByShepardId(collectionId);
		return Response.ok(new CollectionIO(collection)).build();
	}

	@POST
	@Override
	public Response createCollection(CollectionIO collection) {
		Collection newCollection = collectionService.createCollection(collection,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new CollectionIO(newCollection)).status(Status.CREATED).build();
	}

	@PUT
	@Path("/{" + Constants.COLLECTION_ID + "}")
	@Subscribable
	@Override
	public Response updateCollection(@PathParam(Constants.COLLECTION_ID) long collectionId, CollectionIO collection) {
		Collection updatedCollection = collectionService.updateCollectionByShepardId(collectionId, collection,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new CollectionIO(updatedCollection)).build();
	}

	@DELETE
	@Path("/{" + Constants.COLLECTION_ID + "}")
	@Subscribable
	@Override
	public Response deleteCollection(@PathParam(Constants.COLLECTION_ID) long collectionId) {
		return collectionService.deleteCollectionByShepardId(collectionId, securityContext.getUserPrincipal().getName())
				? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.PERMISSIONS)
	@Override
	public Response getCollectionPermissions(@PathParam(Constants.COLLECTION_ID) long collectionId) {
		var perms = permissionsService.getPermissionsByShepardId(collectionId);
		return perms != null ? Response.ok(new PermissionsIO(perms)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@PUT
	@Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.PERMISSIONS)
	@Override
	public Response editCollectionPermissions(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@Valid PermissionsIO permissions) {
		var perms = permissionsService.updatePermissionsByShepardId(permissions, collectionId);
		return perms != null ? Response.ok(new PermissionsIO(perms)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@GET
	@Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.ROLES)
	@Override
	public Response getCollectionRoles(@PathParam(Constants.COLLECTION_ID) long collectionId) {
		var roles = new PermissionsUtil().getRolesByShepardId(collectionId,
				securityContext.getUserPrincipal().getName());
		return roles != null ? Response.ok(roles).build() : Response.status(Status.NOT_FOUND).build();
	}

}
