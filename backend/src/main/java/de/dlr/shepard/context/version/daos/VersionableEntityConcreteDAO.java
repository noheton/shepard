package de.dlr.shepard.context.version.daos;

import de.dlr.shepard.context.version.entities.VersionableEntity;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class VersionableEntityConcreteDAO extends VersionableEntityDAO<VersionableEntity> {

  @Override
  public Class<VersionableEntity> getEntityType() {
    return VersionableEntity.class;
  }
}
