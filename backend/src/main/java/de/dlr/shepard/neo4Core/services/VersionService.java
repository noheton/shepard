package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.VersionIO;
import de.dlr.shepard.util.DateHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class VersionService {

  private VersionDAO versionDAO;
  private CollectionDAO collectionDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;
  private CollectionService collectionService;

  VersionService() {}

  @Inject
  public VersionService(
    VersionDAO versionDAO,
    CollectionDAO collectionDAO,
    UserDAO userDAO,
    DateHelper dateHelper,
    CollectionService collectionService
  ) {
    this.versionDAO = versionDAO;
    this.collectionDAO = collectionDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.collectionService = collectionService;
  }

  public List<Version> getAllVersions(long collectionId) {
    List<Version> versions = versionDAO.findAllVersions(collectionId);
    return versions;
  }

  public Version getVersion(UUID versionUID) {
    return versionDAO.find(versionUID);
  }

  public Version createVersion(long collectionId, VersionIO version, String username) {
    Version HEADVersion = versionDAO.findHEADVersion(collectionId);
    var user = userDAO.find(username);
    Collection collection = collectionService.getCollectionByShepardId(collectionId, null);
    Collection collectionCopy = new Collection(collection);
    collectionCopy.setCreatedAt(dateHelper.getDate());
    collectionCopy.setCreatedBy(user);

    Version newVersion = new Version();
    newVersion.setCreatedAt(dateHelper.getDate());
    newVersion.setCreatedBy(user);
    newVersion.setDescription(version.getDescription());
    newVersion.setName(version.getName());
    if (HEADVersion.getPredecessor() != null) {
      newVersion.setPredecessor(HEADVersion.getPredecessor());
    }

    Version createdVersion = versionDAO.createOrUpdate(newVersion);
    HEADVersion.setPredecessor(createdVersion);
    versionDAO.createOrUpdate(HEADVersion);

    collectionCopy.setVersion(newVersion);
    collectionDAO.createOrUpdate(collectionCopy);

    return createdVersion;
  }
}
