package de.dlr.shepard.common.neo4j.daos;

import de.dlr.shepard.common.neo4j.entities.BasicEntity;

public class BasicEntityDAO extends GenericDAO<BasicEntity> {

  @Override
  public Class<BasicEntity> getEntityType() {
    return BasicEntity.class;
  }
}
