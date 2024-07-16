package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.BasicEntity;

public class BasicEntityDAO extends GenericDAO<BasicEntity> {

  @Override
  public Class<BasicEntity> getEntityType() {
    return BasicEntity.class;
  }
}
