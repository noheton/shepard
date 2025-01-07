package de.dlr.shepard.context.references;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import java.util.List;

public interface IReferenceService<T extends BasicReference, S extends BasicReferenceIO> {
  List<T> getAllReferencesByDataObjectShepardId(long dataObjectShepardId);

  T getReferenceByShepardId(long shepardId);

  T createReferenceByShepardId(long DataObjectShepardId, S referenceIO, String username);

  boolean deleteReferenceByShepardId(long shepardId, String username);
}
