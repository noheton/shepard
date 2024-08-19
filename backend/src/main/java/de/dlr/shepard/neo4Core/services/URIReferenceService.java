package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.URIReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.URIReference;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.util.DateHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestScoped
public class URIReferenceService implements IReferenceService<URIReference, URIReferenceIO> {

  private URIReferenceDAO uRIReferenceDAO;
  private DataObjectDAO dataObjectDAO;
  private UserDAO userDAO;
  private VersionDAO versionDAO;
  private DateHelper dateHelper;

  URIReferenceService() {}

  @Inject
  public URIReferenceService(
    URIReferenceDAO uRIReferenceDAO,
    DataObjectDAO dataObjectDAO,
    UserDAO userDAO,
    VersionDAO versionDAO,
    DateHelper dateHelper
  ) {
    this.uRIReferenceDAO = uRIReferenceDAO;
    this.dataObjectDAO = dataObjectDAO;
    this.userDAO = userDAO;
    this.versionDAO = versionDAO;
    this.dateHelper = dateHelper;
  }

  @Override
  public List<URIReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId) {
    var references = uRIReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public URIReference getReferenceByShepardId(long uriReferenceShepardId) {
    var reference = uRIReferenceDAO.findByShepardId(uriReferenceShepardId);
    if (reference == null || reference.isDeleted()) {
      log.error("URI Reference with id {} is null or deleted", uriReferenceShepardId);
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
    var dataObject = dataObjectDAO.findLightByShepardId(dataObjectShepardId);

    var toCreate = new URIReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(uriReference.getName());
    toCreate.setUri(uriReference.getUri());

    var created = uRIReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = uRIReferenceDAO.createOrUpdate(created);
    Version version = versionDAO.findVersionByNeo4jId(dataObject.getId());
    versionDAO.createLink(created.getId(), version.getUid().toString());
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
