package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import java.util.List;

public interface IReferenceService<T extends BasicReference, S extends BasicReferenceIO> {
  List<T> getAllReferencesByDataObjectShepardId(long dataObjectShepardId);

  T getReferenceByShepardId(long shepardId);

  T createReferenceByShepardId(long DataObjectShepardId, S referenceIO, String username);

  boolean deleteReferenceByShepardId(long shepardId, String username);
}
