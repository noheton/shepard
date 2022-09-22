package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;

public interface IReferenceService<T extends BasicReference, S extends BasicReferenceIO> {

	List<T> getAllReferences(long dataObjectId);

	T getReference(long id);

	T createReference(long dataObjectId, S referenceIO, String username);

	boolean deleteReference(long id, String username);
}
