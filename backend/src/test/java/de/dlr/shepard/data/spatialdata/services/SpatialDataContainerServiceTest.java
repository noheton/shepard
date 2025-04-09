package de.dlr.shepard.data.spatialdata.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataContainerIO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class SpatialDataContainerServiceTest {

  @InjectMock
  SpatialDataContainerDAO spatialDataContainerDAO;

  @InjectMock
  SpatialDataPointService spatialDataPointService;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @InjectMock
  UserService userService;

  @Inject
  SpatialDataContainerService spatialDataContainerService;

  private final User user = new User("123");

  @Test
  public void createContainer_containerAndPermissions_created() {
    when(userService.getCurrentUser()).thenReturn(user);
    when(spatialDataContainerDAO.createOrUpdate(any())).thenReturn(new SpatialDataContainer());

    SpatialDataContainerIO containerIO = new SpatialDataContainerIO();
    containerIO.setName("testContainer");

    spatialDataContainerService.createContainer(containerIO);

    verify(spatialDataContainerDAO, times(1)).createOrUpdate(any());
    verify(permissionsService, times(1)).createPermissions(any(), any(), any());
  }

  @Test
  public void getContainer_containerDoesExist_returnContainer() {
    SpatialDataContainer container = new SpatialDataContainer(1);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Read, user.getUsername())).thenReturn(true);
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(container);

    SpatialDataContainer result = spatialDataContainerService.getContainer(1);

    assertNotNull(result);
  }

  @Test
  public void getContainer_containerDoesNotExist_throwException() {
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(null);

    var ex = assertThrows(InvalidPathException.class, () -> spatialDataContainerService.getContainer(1));

    assertEquals("ID ERROR - Spatial data container with id 1 is null or deleted", ex.getMessage());
  }

  @Test
  public void getContainers_userQueryParams_callRepository() {
    when(userService.getCurrentUser()).thenReturn(user);
    when(spatialDataContainerDAO.findAllSpatialContainers(any(), anyString())).thenReturn(
      List.of(new SpatialDataContainer(1))
    );

    spatialDataContainerService.getAllContainers(new QueryParamHelper());

    verify(spatialDataContainerDAO, times(1)).findAllSpatialContainers(any(), anyString());
  }

  @Test
  public void deleteContainer_deleteDataPointsAndSetDeletedFlagOffContainer() {
    SpatialDataContainer container = new SpatialDataContainer();
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(container);
    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isCurrentUserOwner(1)).thenReturn(true);

    spatialDataContainerService.deleteContainer(1);

    verify(spatialDataPointService, times(1)).deleteByContainerId(1);
    verify(spatialDataContainerDAO, times(1)).createOrUpdate(any());
  }

  @Test
  public void getContainer_wrongPermissions_throwException() {
    SpatialDataContainer container = new SpatialDataContainer(1);
    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Read, user.getUsername())).thenReturn(false);
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(container);

    var ex = assertThrows(InvalidAuthException.class, () -> spatialDataContainerService.getContainer(1));
    assertEquals(
      "The requested action is forbidden by the permission policies. User has no READ permissions.",
      ex.getMessage()
    );
  }

  @Test
  public void deleteContainer_UserIsNotOwner_throwException() {
    SpatialDataContainer container = new SpatialDataContainer();
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(container);
    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Write, user.getUsername())).thenReturn(true);

    var ex = assertThrows(InvalidAuthException.class, () -> spatialDataContainerService.deleteContainer(1));
    assertEquals("The requested action is forbidden by the permission policies. User is not owner.", ex.getMessage());
  }

  @Test
  public void updatePermissions_addNewUser_success() {
    // setup
    User secondUser = new User("testuser2");
    SpatialDataContainer container = new SpatialDataContainer(1L);
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(container);
    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Write, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Manage, user.getUsername())).thenReturn(true);

    // act
    var newUserList = new String[] { secondUser.getUsername(), user.getUsername() };
    PermissionsIO updatedPermissions = new PermissionsIO();
    updatedPermissions.setEntityId(container.getId());
    updatedPermissions.setOwner(user.getUsername());
    updatedPermissions.setWriter(newUserList);
    updatedPermissions.setReader(newUserList);
    spatialDataContainerService.updateContainerPermissions(updatedPermissions, container.getId());

    //assert
    verify(permissionsService, times(1)).isAccessTypeAllowedForUser(1L, AccessType.Manage, user.getUsername());
    verify(permissionsService, times(1)).updatePermissionsByNeo4jId(updatedPermissions, 1L);
  }

  @Test
  public void updatePermissions_wrongPermissions_throwException() {
    // setup
    User secondUser = new User("testuser2");
    SpatialDataContainer container = new SpatialDataContainer(1L);
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(container);
    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1, AccessType.Manage, user.getUsername())).thenReturn(false);

    var newUserList = new String[] { secondUser.getUsername(), user.getUsername() };
    PermissionsIO updatedPermissions = new PermissionsIO();
    updatedPermissions.setEntityId(container.getId());
    updatedPermissions.setOwner(user.getUsername());
    updatedPermissions.setWriter(newUserList);
    updatedPermissions.setReader(newUserList);

    // act + assert
    var ex = assertThrows(InvalidAuthException.class, () ->
      spatialDataContainerService.updateContainerPermissions(updatedPermissions, container.getId())
    );
    assertEquals(
      "The requested action is forbidden by the permission policies. User has no MANAGE permissions.",
      ex.getMessage()
    );
  }
}
