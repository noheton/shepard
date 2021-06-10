package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.http.HttpStatus;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.neo4Core.services.FileReferenceService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.FILE_REFERENCES)
@Log4j2
public class FileReferenceRestImpl implements FileReferenceRest {

	private FileReferenceService fileReferenceService = new FileReferenceService();
	@Context
	private SecurityContext securityContext;

	@Override
	public Response getAllFileReferences(long collectionId, long dataObjectId) {
		log.info("Received GET ALL request with collection {} and dataobject {} from user {}", collectionId,
				dataObjectId, securityContext.getUserPrincipal().getName());
		var references = fileReferenceService.getAllFileReferences(dataObjectId);
		var result = new ArrayList<FileReferenceIO>(references.size());
		for (var ref : references) {
			result.add(new FileReferenceIO(ref));
		}
		return Response.ok(result).build();
	}

	@Override
	public Response getFileReference(long collectionId, long dataObjectId, long referenceId) {
		log.info("Received POST request with collection {}, dataobject {} and reference {} from user {}", collectionId,
				dataObjectId, referenceId, securityContext.getUserPrincipal().getName());
		var ref = fileReferenceService.getFileReference(referenceId);
		return Response.ok(new FileReferenceIO(ref)).build();
	}

	@Override
	public Response createFileReference(long collectionId, long dataObjectId, FileReferenceIO fileReference)
			throws InvalidBodyException {
		log.info("Received POST request with collection {}, dataobject {} and new filereference {} from user {}",
				collectionId, dataObjectId, fileReference.getName(), securityContext.getUserPrincipal().getName());
		var ref = fileReferenceService.createFileReference(dataObjectId, fileReference,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new FileReferenceIO(ref)).status(HttpStatus.SC_CREATED).build();
	}

	@Override
	public Response deleteBasicReference(long collectionId, long dataObjectId, long fileReferenceId) {
		log.info(
				"Received DELETE request with parameters: collectionID {}, dataObjectID {}, fileReferenceID {} from user {}",
				collectionId, dataObjectId, fileReferenceId, securityContext.getUserPrincipal().getName());
		var result = fileReferenceService.deleteReference(fileReferenceId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@Override
	public Response getFilePayload(long collectionId, long dataObjectId, long fileReferenceId, String oid) {
		log.info("Received GET FILE PAYLOAD request with reference Id {} from user {}", fileReferenceId,
				securityContext.getUserPrincipal().getName());
		var payload = fileReferenceService.getPayload(fileReferenceId, oid);
		return Response.ok(payload.inputStream, MediaType.APPLICATION_OCTET_STREAM)
				.header("Content-Disposition", "attachment; filename=\"" + payload.name + "\"").build();
	}

	@Override
	public Response getAllFiles(long collectionId, long dataObjectId, long fileId) {
		log.info("Received GET ALL FILES request with reference Id {} from user {}", fileId,
				securityContext.getUserPrincipal().getName());
		ArrayList<File> ret = fileReferenceService.getFiles(fileId);
		return Response.ok(ret).build();
	}

}
