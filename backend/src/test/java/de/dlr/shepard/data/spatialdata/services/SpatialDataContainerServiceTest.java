package de.dlr.shepard.data.spatialdata.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class SpatialDataContainerServiceTest {

  @Inject
  SpatialDataContainerService spatialDataContainerService;

  @InjectMock
  SpatialDataContainerDAO spatialDataContainerDAO;

  @InjectMock
  SpatialDataPointService spatialDataPointService;

  @InjectMock
  PermissionsDAO permissionsDao;

  @InjectMock
  UserDAO userDao;

  @Test
  public void createContainer_containerAndPermissions_created() {
    String userName = "testUser";
    when(userDao.find(userName)).thenReturn(new User(userName));
    when(spatialDataContainerDAO.createOrUpdate(any())).thenReturn(new SpatialDataContainer());

    spatialDataContainerService.createContainer("testContainer", userName);

    verify(spatialDataContainerDAO, times(1)).createOrUpdate(any());
    verify(permissionsDao, times(1)).createOrUpdate(any());
  }

  @Test
  public void getContainer_containerDoesExist_returnContainer() {
    SpatialDataContainer container = new SpatialDataContainer(1);
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(container);

    SpatialDataContainer result = spatialDataContainerService.getContainer(1);

    assertNotNull(result);
  }

  @Test
  public void getContainer_containerDoesNotExist_throwException() {
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(null);

    try {
      spatialDataContainerService.getContainer(1);
    } catch (Exception e) {
      assertEquals("Spatial data container with id 1 not found.", e.getMessage());
    }
  }

  @Test
  public void getContainers_userQueryParams_callRepository() {
    when(spatialDataContainerDAO.findAllSpatialContainers(any(), anyString())).thenReturn(
      List.of(new SpatialDataContainer(1))
    );

    spatialDataContainerService.getContainers(new QueryParamHelper(), "testUser");

    verify(spatialDataContainerDAO, times(1)).findAllSpatialContainers(any(), anyString());
  }

  @Test
  public void deleteContainer_deleteDataPointsAndSetDeletedFlagOffContainer() {
    SpatialDataContainer container = new SpatialDataContainer();
    when(spatialDataContainerDAO.findByNeo4jId(anyLong())).thenReturn(container);

    spatialDataContainerService.deleteContainer(1, "testUser");

    verify(spatialDataPointService, times(1)).deleteByContainerId(1);
    verify(spatialDataContainerDAO, times(1)).createOrUpdate(any());
  }
}
