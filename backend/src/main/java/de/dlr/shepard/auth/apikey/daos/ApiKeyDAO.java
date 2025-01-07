package de.dlr.shepard.auth.apikey.daos;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;

@RequestScoped
public class ApiKeyDAO extends GenericDAO<ApiKey> {

  /**
   * Find an apiKey by uid
   *
   * @param id Identifies the apiKey
   * @return the found apiKey
   */
  public ApiKey find(UUID id) {
    ApiKey entity = session.load(getEntityType(), id, DEPTH_ENTITY);
    return entity;
  }

  /**
   * Find an apiKey by uid
   *
   * @param id Identifies the apiKey
   * @return true if deletion was successful
   */
  public boolean delete(UUID id) {
    ApiKey entity = session.load(getEntityType(), id);
    if (entity != null) {
      session.delete(entity);
      return true;
    }
    return false;
  }

  @Override
  public Class<ApiKey> getEntityType() {
    return ApiKey.class;
  }
}
