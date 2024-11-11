package de.dlr.shepard.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ExperimentalTimeseriesContainerServiceTest {

  @Inject
  ExperimentalTimeseriesContainerService timeseriesContainerService;

  private final String containerName = "AnotherContainer";
  private final String userName = "Testuser";

  @Test
  @Transactional
  public void createContainer_containerDoesNotExist_containerIsCreated() {
    var created = timeseriesContainerService.createContainer(containerName, userName);

    assertEquals(containerName, created.getName());
    assertTrue(created.getId() >= 0);
  }
}
