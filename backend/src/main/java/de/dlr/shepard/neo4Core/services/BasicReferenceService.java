package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class BasicReferenceService {

  private BasicReferenceDAO basicReferenceDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;

  BasicReferenceService() {}

  @Inject
  public BasicReferenceService(BasicReferenceDAO basicReferenceDAO, UserDAO userDAO, DateHelper dateHelper) {
    this.basicReferenceDAO = basicReferenceDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
  }

  /**
   * Searches the neo4j database for a BasicReference
   *
   * @param shepardId identifies the searched BasicReference
   *
   * @return the BasicReference with the given id or null
   */
  public BasicReference getReferenceByShepardId(long shepardId) {
    BasicReference basicReference = basicReferenceDAO.findByShepardId(shepardId);
    if (basicReference == null || basicReference.isDeleted()) {
      Log.errorf("Basic Reference with id %s is null or deleted", shepardId);
      return null;
    }
    return basicReference;
  }

  /**
   * Searches the database for BasicReferences.
   *
   * @param dataObjectShepardId identifies the DataObject
   * @param params              encapsulates possible parameters
   * @return a List of BasicReferences
   */
  public List<BasicReference> getAllBasicReferencesByDataObjectShepardId(
    long dataObjectShepardId,
    QueryParamHelper params
  ) {
    var references = basicReferenceDAO.findByDataObjectShepardId(dataObjectShepardId, params);
    return references;
  }

  /**
   * Set the deleted flag for the Reference
   *
   * @param basicReferenceShepardId identifies the BasicReference to be deleted
   * @param username                identifies the user
   * @return a boolean to identify if the BasicReference was successfully removed
   */
  public boolean deleteReferenceByShepardId(long basicReferenceShepardId, String username) {
    var user = userDAO.find(username);

    var basicReference = basicReferenceDAO.findByShepardId(basicReferenceShepardId);
    basicReference.setDeleted(true);
    basicReference.setUpdatedAt(dateHelper.getDate());
    basicReference.setUpdatedBy(user);

    basicReferenceDAO.createOrUpdate(basicReference);
    return true;
  }
}
