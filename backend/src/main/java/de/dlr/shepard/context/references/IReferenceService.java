package de.dlr.shepard.context.references;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import java.util.List;
import java.util.UUID;

public interface IReferenceService<T extends BasicReference, S extends BasicReferenceIO> {
  List<T> getAllReferencesByDataObjectId(long collectionShepardId, long dataObjectShepardId, UUID versionUID);

  T getReference(long collectionShepardId, long shepardDataObjectId, long shepardId, UUID versionUID);

  T createReference(long collectionShepardId, long dataObjectShepardId, S referenceIO);

  void deleteReference(long collectionShepardId, long dataObjectShepardId, long shepardId);
}
