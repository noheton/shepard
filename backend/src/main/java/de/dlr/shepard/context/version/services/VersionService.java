package de.dlr.shepard.context.version.services;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.context.version.io.VersionIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class VersionService {

  @Inject
  VersionDAO versionDAO;

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  UserDAO userDAO;

  @Inject
  DateHelper dateHelper;

  @Inject
  CollectionService collectionService;

  public List<Version> getAllVersions(long collectionId) {
    List<Version> versions = versionDAO.findAllVersions(collectionId);
    return versions;
  }

  public Version getVersion(UUID versionUID) {
    return versionDAO.find(versionUID);
  }

  public Version attachToVersionOfVersionableEntityAndReturnVersion(long existingEntityId, long entityToAttachId) {
    Version version = versionDAO.findVersionLightByNeo4jId(existingEntityId);
    versionDAO.createLink(entityToAttachId, version.getUid());
    return version;
  }

  private Collection copyCollectionForVersioning(Collection collection, Date createdAt, User createdBy) {
    Collection collectionCopy = new Collection();
    collectionCopy.setAnnotations(collection.getAnnotations());
    collectionCopy.setAttributes(collection.getAttributes());
    collectionCopy.setDescription(collection.getDescription());
    collectionCopy.setName(collection.getName());
    collectionCopy.setPermissions(collection.getPermissions());
    collectionCopy.setShepardId(collection.getShepardId());
    collectionCopy.setCreatedAt(createdAt);
    collectionCopy.setCreatedBy(createdBy);
    return collectionCopy;
  }

  public Version createVersion(long collectionId, VersionIO version, String username) {
    Version HEADVersion = versionDAO.findHEADVersion(collectionId);
    var user = userDAO.find(username);
    var date = dateHelper.getDate();
    Collection collection = collectionService.getCollectionWithDataObjectsAndIncomingReferences(collectionId, null);
    Collection collectionCopy = copyCollectionForVersioning(collection, date, user);

    Version newVersion = new Version();
    newVersion.setCreatedAt(date);
    newVersion.setCreatedBy(user);
    newVersion.setDescription(version.getDescription());
    newVersion.setName(version.getName());
    newVersion.setHEADVersion(false);
    if (HEADVersion.getPredecessor() != null) {
      newVersion.setPredecessor(HEADVersion.getPredecessor());
    }

    if (HEADVersion.getPredecessor() != null) versionDAO.removeHasPredecessor(
      HEADVersion.getUid(),
      HEADVersion.getPredecessor().getUid()
    );
    Version createdVersion = versionDAO.createOrUpdate(newVersion);
    HEADVersion.setPredecessor(createdVersion);
    versionDAO.createOrUpdate(HEADVersion);

    collectionCopy.setVersion(newVersion);
    collectionDAO.createOrUpdate(collectionCopy);

    UUID HEADVersionUID = HEADVersion.getUid();
    UUID createdVersionUID = createdVersion.getUid();
    versionDAO.copyDataObjectsWithParentsAndPredecessors(HEADVersionUID, createdVersionUID);
    versionDAO.copyDataObjectReferences(HEADVersionUID, createdVersionUID);
    versionDAO.copyCollectionReferences(HEADVersionUID, createdVersionUID);
    versionDAO.copyFileReferences(HEADVersionUID, createdVersionUID);
    versionDAO.copyStructuredDataReferences(HEADVersionUID, createdVersionUID);
    versionDAO.copyTimeseriesReferences(HEADVersionUID, createdVersionUID);
    versionDAO.copyURIReferences(HEADVersionUID, createdVersionUID);
    return createdVersion;
  }
}
