package de.dlr.shepard.data;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public abstract class AbstractContainerService<T extends BasicContainer, S extends BasicContainerIO> {

  @Inject
  PermissionsService permissionsService;

  @Inject
  AuthenticationContext authenticationContext;

  public abstract List<T> getAllContainers(QueryParamHelper params);

  public abstract T getContainer(long id);

  public abstract T createContainer(S containerIO);

  public abstract void deleteContainer(long containerId);

  /**
   * Gets roles for container specified by id
   *
   * @param containerId
   * @return Roles
   * @throws InvalidPathException if container with containerId does not exist
   * @throws InvalidAuthException if user has no read permissions on specified container
   */
  public Roles getContainerRoles(long containerId) {
    getContainer(containerId);

    // We can use the container as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.getUserRolesOnEntity(containerId, authenticationContext.getCurrentUserName());
  }

  /**
   * Gets Permissions for container specified by id
   *
   * @param containerId
   * @return Permissions
   * @throws InvalidPathException if container with containerId does not exist
   * @throws InvalidAuthException if user has no read permissions on specified container, or is not allowed to manage permissions on container
   */
  public Permissions getContainerPermissions(long containerId) {
    getContainer(containerId);
    assertIsAllowedToManageContainer(containerId);

    // We can use the containerId as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.getPermissionsOfEntity(containerId);
  }

  /**
   * Updates Permissions for container specified by id
   *
   * @param containerId
   * @return Permissions
   * @throws InvalidPathException if container with containerId does not exist
   * @throws InvalidAuthException if user has no read permissions on specified container, or is not allowed to manage permissions on container
   */
  public Permissions updateContainerPermissions(PermissionsIO newPermissions, long containerId) {
    getContainer(containerId);
    assertIsAllowedToManageContainer(containerId);

    // We can use the containerId as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.updatePermissionsByNeo4jId(newPermissions, containerId);
  }

  public void assertIsAllowedToReadContainer(long containerId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        containerId,
        AccessType.Read,
        authenticationContext.getCurrentUserName()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  public void assertIsAllowedToEditContainer(long containerId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        containerId,
        AccessType.Write,
        authenticationContext.getCurrentUserName()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  public void assertIsAllowedToManageContainer(long containerId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        containerId,
        AccessType.Manage,
        authenticationContext.getCurrentUserName()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }
}
