package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.VersionIO;
import de.dlr.shepard.util.DateHelper;
import java.util.List;

public class VersionService {

  private VersionDAO versionDAO = new VersionDAO();
  private CollectionDAO collectionDAO = new CollectionDAO();
  private UserDAO userDAO = new UserDAO();
  private DateHelper dateHelper = new DateHelper();
  private CollectionService collectionService = new CollectionService();

  public List<Version> getAllVersions(long collectionId) {
    List<Version> versions = versionDAO.findAllVersions(collectionId);
    return versions;
  }

  public Version getVersion(long collectionId, String versionUID) {
    return versionDAO.find(collectionId, versionUID);
  }

  public Version createVersion(long collectionId, VersionIO version, String username) {
    Version HEADVersion = versionDAO.findHEADVersion(collectionId);
    var user = userDAO.find(username);
    Collection collection = collectionService.getCollectionByShepardId(collectionId, null);
    Collection collectionCopy = new Collection(collection);
    collectionCopy.setCreatedAt(DateHelper.getDate());
    collectionCopy.setCreatedBy(user);

    Version newVersion = new Version();
    newVersion.setCreatedAt(DateHelper.getDate());
    newVersion.setCreatedBy(user);
    newVersion.setDescription(version.getDescription());
    newVersion.setName(version.getName());
    newVersion.setPredecessor(HEADVersion);

    Version createdVersion = versionDAO.createOrUpdate(newVersion);

    collectionCopy.setVersion(newVersion);
    collectionDAO.createOrUpdate(collectionCopy);

    return createdVersion;
  }
}
