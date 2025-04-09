package de.dlr.shepard.data.file.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.AbstractContainerService;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.io.FileContainerIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.List;

@RequestScoped
public class FileContainerService extends AbstractContainerService<FileContainer, FileContainerIO> {

  @Inject
  FileContainerDAO fileContainerDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  FileService fileService;

  @Inject
  PermissionsService permissionsService;

  /**
   * Creates a FileContainer and stores it in Neo4J
   *
   * @param fileContainerIO to be stored
   * @param username        of the related user
   * @return the created FileContainer
   */
  @Override
  public FileContainer createContainer(FileContainerIO fileContainerIO) {
    User user = userService.getCurrentUser();
    FileContainer toCreate = new FileContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setMongoId(fileService.createFileContainer());
    toCreate.setName(fileContainerIO.getName());

    var created = fileContainerDAO.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  /**
   * Gets the FileContainer
   *
   * @param id identifies the searched FileContainer
   * @return the FileContainer with matching id or null
   * @throws InvalidPathException if the file container cannot be found
   * @throws InvalidAuthException if user has no read permission on container
   */
  @Override
  public FileContainer getContainer(long id) {
    FileContainer fileContainer = fileContainerDAO.findByNeo4jId(id);

    if (fileContainer == null || fileContainer.isDeleted()) {
      String errorMsg = String.format("ID ERROR - File Container with id %s is null or deleted", id);
      Log.errorf(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
    assertIsAllowedToReadContainer(id);
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
  public List<FileContainer> getAllContainers(QueryParamHelper params) {
    User user = userService.getCurrentUser();
    List<FileContainer> containers = fileContainerDAO.findAllFileContainers(params, user.getUsername());
    return containers;
  }

  /**
   * Deletes a FileContainer in Neo4j
   *
   * @param fileContainerId identifies the FileContainer
   * @param username        the deleting user
   * @throws InvalidPathException if the file container cannot be found
   * @throws InvalidAuthException if user has no write permission on container
   */
  @Override
  public void deleteContainer(long fileContainerId) {
    User user = userService.getCurrentUser();
    FileContainer fileContainer = getContainer(fileContainerId);
    assertIsAllowedToDeleteContainer(fileContainerId);

    String mongoId = fileContainer.getMongoId();
    fileContainer.setDeleted(true);
    fileContainer.setUpdatedAt(dateHelper.getDate());
    fileContainer.setUpdatedBy(user);
    fileContainerDAO.createOrUpdate(fileContainer);
    fileService.deleteFileContainer(mongoId);
  }

  /**
   * Get file payload
   *
   * @param fileContainerId The container to get the payload from
   * @param oid             The specific file
   * @return a NamedInputStream
   * @throws InvalidPathException if the file container cannot be found
   * @throws InvalidAuthException if user has no read permission on container
   */
  public NamedInputStream getFile(long fileContainerId, String oid) {
    FileContainer container = getContainer(fileContainerId);

    return fileService.getPayload(container.getMongoId(), oid);
  }

  /**
   * Create a new file
   *
   * @param fileContainerId identifies the file container
   * @param fileName        the name of the new file
   * @param inputStream     the file itself
   * @return The newly created file
   * @throws InternalServerErrorException if file creation fails
   * @throws InvalidPathException if the file container cannot be found
   * @throws InvalidAuthException if user has no read or write permission on container
   */
  public ShepardFile createFile(long fileContainerId, String fileName, InputStream inputStream) {
    FileContainer fileContainer = getContainer(fileContainerId);
    assertIsAllowedToEditContainer(fileContainerId);

    if (fileName == null || fileName.isBlank()) {
      var sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
      var dateStr = sdf.format(dateHelper.getDate());
      fileName = "shepard-file-" + dateStr;
    }

    ShepardFile result = fileService.createFile(fileContainer.getMongoId(), fileName, inputStream);

    fileContainer.addFile(result);
    fileContainerDAO.createOrUpdate(fileContainer);
    return result;
  }

  /**
   * Delete one file
   *
   * @param fileContainerId The container to get the payload from
   * @param oid             The specific file
   
   */
  public void deleteFile(long fileContainerId, String oid) {
    FileContainer container = getContainer(fileContainerId);
    assertIsAllowedToEditContainer(fileContainerId);

    fileService.deleteFile(container.getMongoId(), oid);

    List<ShepardFile> newFiles = container.getFiles().stream().filter(f -> !f.getOid().equals(oid)).toList();
    container.setFiles(newFiles);
    fileContainerDAO.createOrUpdate(container);
  }
}
