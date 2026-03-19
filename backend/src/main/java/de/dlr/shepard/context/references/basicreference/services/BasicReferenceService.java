package de.dlr.shepard.context.references.basicreference.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.basicreference.daos.BasicReferenceDAO;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class BasicReferenceService {

  @Inject
  BasicReferenceDAO basicReferenceDAO;

  @Inject
  UserService userService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  CollectionService collectionService;

  @Inject
  DateHelper dateHelper;

  /**
   * Searches the neo4j database for a BasicReference
   *
   * @param shepardId identifies the searched BasicReference
   *
   * @return the BasicReference with the given id or null
   * @throws InvalidPathException if dataobject or collection could not be found Id
   * @throws InvalidAuthException if user has no read permission on collection
   */
  public BasicReference getReference(long collectionShepardId, long dataObjectShepardId, long shepardId) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);

    BasicReference basicReference = basicReferenceDAO.findByShepardId(shepardId);
    if (basicReference == null || basicReference.isDeleted()) {
      String errorMsg = "ID ERROR - Basic Reference with id %s is null or deleted".formatted(shepardId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    if (
      basicReference.getDataObject() != null &&
      !basicReference.getDataObject().getShepardId().equals(dataObjectShepardId)
    ) {
      String errorMsg = "ID ERROR - There is no association between dataObject and reference";
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    return basicReference;
  }

  /**
   * Searches the database for BasicReferences.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId identifies the DataObject
   * @param params              encapsulates possible parameters
   * @return a List of BasicReferences
   * @throws InvalidPathException if dataobject or collection could not be found Id
   * @throws InvalidAuthException if user has no read permission on collection
   */
  public List<BasicReference> getAllBasicReferences(
    long collectionShepardId,
    long dataObjectShepardId,
    QueryParamHelper params
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);

    var references = basicReferenceDAO.findByDataObjectShepardId(dataObjectShepardId, params);
    return references;
  }

  /**
   * Set the deleted flag for the Reference
   *
   * @param basicReferenceShepardId identifies the BasicReference to be deleted
   * @return a boolean to identify if the BasicReference was successfully removed
   */
  public void deleteReference(long collectionShepardId, long dataObjectShepardId, long basicReferenceShepardId) {
    var basicReference = getReference(collectionShepardId, dataObjectShepardId, basicReferenceShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();

    basicReference.setDeleted(true);
    basicReference.setUpdatedAt(dateHelper.getDate());
    basicReference.setUpdatedBy(user);

    basicReferenceDAO.createOrUpdate(basicReference);
  }
}
