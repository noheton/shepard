package de.dlr.shepard.context.collection.io;

import de.dlr.shepard.context.version.entities.VersionableEntity;
import java.util.UUID;

public class IdentifierTriple {

  public Long id;
  public UUID versionUID;
  public boolean isHEAD;

  public IdentifierTriple(Long id, UUID versionUID, boolean isHEAD) {
    this.id = id;
    this.versionUID = versionUID;
    this.isHEAD = isHEAD;
  }

  public IdentifierTriple(VersionableEntity entity) {
    this.id = entity.getShepardId();
    this.versionUID = entity.getVersion().getUid();
    this.isHEAD = entity.getVersion().isHEADVersion();
  }
}
