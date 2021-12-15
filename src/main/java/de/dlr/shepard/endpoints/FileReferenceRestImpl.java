package de.dlr.shepard.endpoints;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.neo4Core.services.FileReferenceService;
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
import lombok.extern.slf4j.Slf4j;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.FILE_REFERENCES)
@Slf4j
public class FileReferenceRestImpl implements FileReferenceRest {

	private FileReferenceService fileReferenceService = new FileReferenceService();
	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllFileReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		log.info("Received GET ALL request with collection {} and dataobject {} from user {}", collectionId,
				dataObjectId, securityContext.getUserPrincipal().getName());
		var references = fileReferenceService.getAllFileReferences(dataObjectId);
		var result = new ArrayList<FileReferenceIO>(references.size());
		for (var ref : references) {
			result.add(new FileReferenceIO(ref));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.FILE_REFERENCE_ID + "}")
	@Override
	public Response getFileReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.FILE_REFERENCE_ID) long referenceId) {
		log.info("Received GET request with collection {}, dataobject {} and reference {} from user {}", collectionId,
				dataObjectId, referenceId, securityContext.getUserPrincipal().getName());
		var ref = fileReferenceService.getFileReference(referenceId);
		return Response.ok(new FileReferenceIO(ref)).build();
	}

	@POST
	@Override
	public Response createFileReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, FileReferenceIO fileReference)
			throws InvalidBodyException {
		log.info("Received POST request with collection {}, dataobject {} and new filereference {} from user {}",
				collectionId, dataObjectId, fileReference.getName(), securityContext.getUserPrincipal().getName());
		var ref = fileReferenceService.createFileReference(dataObjectId, fileReference,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new FileReferenceIO(ref)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.FILE_REFERENCE_ID + "}")
	@Override
	public Response deleteFileReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.FILE_REFERENCE_ID) long fileReferenceId) {
		log.info(
				"Received DELETE request with parameters: collectionID {}, dataObjectID {}, fileReferenceID {} from user {}",
				collectionId, dataObjectId, fileReferenceId, securityContext.getUserPrincipal().getName());
		var result = fileReferenceService.deleteReference(fileReferenceId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.FILE_REFERENCE_ID + "}/payload/{" + Constants.OID + "}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Override
	public Response getFilePayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.FILE_REFERENCE_ID) long fileReferenceId, @PathParam(Constants.OID) String oid) {
		log.info("Received GET FILE PAYLOAD request with reference Id {} and Oid {} from user {}", fileReferenceId, oid,
				securityContext.getUserPrincipal().getName());
		var payload = fileReferenceService.getPayload(fileReferenceId, oid);
		return payload != null
				? Response.ok(payload.inputStream, MediaType.APPLICATION_OCTET_STREAM)
						.header("Content-Disposition", "attachment; filename=\"" + payload.name + "\"").build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@GET
	@Path("/{" + Constants.FILE_REFERENCE_ID + "}/payload")
	@Override
	public Response getFiles(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.FILE_REFERENCE_ID) long fileId) {
		log.info("Received GET ALL FILES request with reference Id {} from user {}", fileId,
				securityContext.getUserPrincipal().getName());
		List<File> ret = fileReferenceService.getFiles(fileId);
		return Response.ok(ret).build();
	}

}
