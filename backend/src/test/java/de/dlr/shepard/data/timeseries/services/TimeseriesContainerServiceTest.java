package de.dlr.shepard.data.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TimeseriesContainerServiceTest {

  @InjectMock
  UserService userService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  private final String containerName = "AnotherContainer";

  @Test
  @Transactional
  public void createContainer_containerDoesNotExist_containerIsCreated() {
    User user = new User("Alice");

    TimeseriesContainerIO timeseriesContainerIO = new TimeseriesContainerIO();
    timeseriesContainerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);

    var created = timeseriesContainerService.createContainer(timeseriesContainerIO);

    assertEquals(containerName, created.getName());
    assertTrue(created.getId() >= 0);
  }

  @Test
  @Transactional
  public void deleteContainer_containerExists_containerIsDeleted() {
    User user = new User("Alice");

    TimeseriesContainerIO timeseriesContainerIO = new TimeseriesContainerIO();
    timeseriesContainerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var created = timeseriesContainerService.createContainer(timeseriesContainerIO);
    // add the container to the cache
    timeseriesContainerService.getContainer(created.getId());
    timeseriesContainerService.deleteContainer(created.getId());
    assertThrows(InvalidPathException.class, () -> timeseriesContainerService.getContainer(created.getId()));
  }
}
