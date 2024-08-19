package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.VersionableEntity;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class VersionableEntityConcreteDAO extends VersionableEntityDAO<VersionableEntity> {

  @Override
  public Class<VersionableEntity> getEntityType() {
    return VersionableEntity.class;
  }
}
