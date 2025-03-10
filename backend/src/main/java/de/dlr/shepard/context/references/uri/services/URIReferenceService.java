package de.dlr.shepard.context.references.uri.services;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.uri.daos.URIReferenceDAO;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class URIReferenceService implements IReferenceService<URIReference, URIReferenceIO> {

  @Inject
  URIReferenceDAO uRIReferenceDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  UserDAO userDAO;

  @Inject
  VersionService versionService;

  @Inject
  DateHelper dateHelper;

  @Override
  public List<URIReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId, UUID versionUID) {
    var references = uRIReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public URIReference getReferenceByShepardId(long uriReferenceShepardId, UUID versionUID) {
    var reference = uRIReferenceDAO.findByShepardId(uriReferenceShepardId, versionUID);
    if (reference == null || reference.isDeleted()) {
      Log.errorf("URI Reference with id %s is null or deleted", uriReferenceShepardId);
      return null;
    }
    return reference;
  }

  @Override
  public URIReference createReferenceByShepardId(
    long dataObjectShepardId,
    URIReferenceIO uriReference,
    String username
  ) {
    var user = userDAO.find(username);
    var dataObject = dataObjectService.getDataObject(dataObjectShepardId);

    var toCreate = new URIReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(uriReference.getName());
    toCreate.setUri(uriReference.getUri());

    var created = uRIReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = uRIReferenceDAO.createOrUpdate(created);
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
    return created;
  }

  @Override
  public boolean deleteReferenceByShepardId(long uriReferenceShepardId, String username) {
    var user = userDAO.find(username);

    var old = uRIReferenceDAO.findByShepardId(uriReferenceShepardId);
    old.setDeleted(true);
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);

    uRIReferenceDAO.createOrUpdate(old);
    return true;
  }
}
