package de.dlr.shepard.context.references;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import java.util.List;
import java.util.UUID;

public interface IReferenceService<T extends BasicReference, S extends BasicReferenceIO> {
  List<T> getAllReferencesByDataObjectShepardId(long dataObjectShepardId, UUID versionUID);

  T getReferenceByShepardId(long shepardId, UUID versionUID);

  T createReferenceByShepardId(long DataObjectShepardId, S referenceIO, String username);

  boolean deleteReferenceByShepardId(long shepardId, String username);
}
