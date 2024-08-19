package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.mongoDB.NamedInputStream;
import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.List;

@RequestScoped
public class FileContainerService implements IContainerService<FileContainer, FileContainerIO> {

  private FileContainerDAO fileContainerDAO;
  private PermissionsDAO permissionsDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;
  private FileService fileService;

  FileContainerService() {}

  @Inject
  public FileContainerService(
    FileContainerDAO fileContainerDAO,
    PermissionsDAO permissionsDAO,
    UserDAO userDAO,
    DateHelper dateHelper,
    FileService fileService
  ) {
    this.fileContainerDAO = fileContainerDAO;
    this.permissionsDAO = permissionsDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.fileService = fileService;
  }

  /**
   * Creates a FileContainer and stores it in Neo4J
   *
   * @param fileContainerIO to be stored
   * @param username        of the related user
   * @return the created FileContainer
   */
  @Override
  public FileContainer createContainer(FileContainerIO fileContainerIO, String username) {
    var user = userDAO.find(username);
    var toCreate = new FileContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setMongoId(fileService.createFileContainer());
    toCreate.setName(fileContainerIO.getName());

    var created = fileContainerDAO.createOrUpdate(toCreate);
    permissionsDAO.createOrUpdate(new Permissions(created, user, PermissionType.Private));
    return created;
  }

  /**
   * Searches the FileContainer in Neo4j
   *
   * @param id identifies the searched FileContainer
   * @return the FileContainer with matching id or null
   */
  @Override
  public FileContainer getContainer(long id) {
    FileContainer fileContainer = fileContainerDAO.findByNeo4jId(id);
    if (fileContainer == null || fileContainer.isDeleted()) {
      Log.errorf("File Container with id %s is null or deleted", id);
      return null;
    }
    return fileContainer;
  }

  /**
   * Searches the database for all FileContainers
   *
   * @param params   QueryParamsHelper
   * @param username the name of the user
   * @return a list of FileContainers
   */
  @Override
  public List<FileContainer> getAllContainers(QueryParamHelper params, String username) {
    var containers = fileContainerDAO.findAllFileContainers(params, username);
    return containers;
  }

  /**
   * Deletes a FileContainer in Neo4j
   *
   * @param fileContainerId identifies the FileContainer
   * @param username        the deleting user
   * @return a boolean to determine if FileContainer was successfully deleted
   */
  @Override
  public boolean deleteContainer(long fileContainerId, String username) {
    var user = userDAO.find(username);
    FileContainer fileContainer = fileContainerDAO.findByNeo4jId(fileContainerId);
    if (fileContainer == null) {
      return false;
    }
    String mongoid = fileContainer.getMongoId();
    fileContainer.setDeleted(true);
    fileContainer.setUpdatedAt(dateHelper.getDate());
    fileContainer.setUpdatedBy(user);
    fileContainerDAO.createOrUpdate(fileContainer);
    return fileService.deleteFileContainer(mongoid);
  }

  /**
   * Get file payload
   *
   * @param fileContainerId The container to get the payload from
   * @param oid             The specific file
   * @return a NamedInputStream
   */
  public NamedInputStream getFile(long fileContainerId, String oid) {
    var container = fileContainerDAO.findLightByNeo4jId(fileContainerId);
    if (container == null || container.isDeleted()) {
      Log.errorf("File Container with id %s is null or deleted", fileContainerId);
      return null;
    }
    var result = fileService.getPayload(container.getMongoId(), oid);
    return result;
  }

  /**
   * Create a new file
   *
   * @param fileContainerId identifies the file container
   * @param fileName        the name of the new file
   * @param inputStream     the file itself
   * @return The newly created file
   */
  public ShepardFile createFile(long fileContainerId, String fileName, InputStream inputStream) {
    var fileContainer = fileContainerDAO.findByNeo4jId(fileContainerId);
    if (fileContainer == null || fileContainer.isDeleted()) {
      Log.errorf("File Container with id %s is null or deleted", fileContainerId);
      return null;
    }
    if (fileName == null || fileName.isBlank()) {
      var sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
      var dateStr = sdf.format(dateHelper.getDate());
      fileName = "shepard-file-" + dateStr;
    }
    var result = fileService.createFile(fileContainer.getMongoId(), fileName, inputStream);
    if (result == null) {
      Log.error("Failed to create shepard file");
      return null;
    }
    fileContainer.addFile(result);
    fileContainerDAO.createOrUpdate(fileContainer);
    return result;
  }

  /**
   * Delete one file
   *
   * @param fileContainerId The container to get the payload from
   * @param oid             The specific file
   * @return Whether the deletion was successful or not
   */
  public boolean deleteFile(long fileContainerId, String oid) {
    var container = fileContainerDAO.findByNeo4jId(fileContainerId);
    if (container == null || container.isDeleted()) return false;
    var result = fileService.deleteFile(container.getMongoId(), oid);
    if (result) {
      var newFiles = container.getFiles().stream().filter(f -> !f.getOid().equals(oid)).toList();
      container.setFiles(newFiles);
      fileContainerDAO.createOrUpdate(container);
    }
    return result;
  }
}
