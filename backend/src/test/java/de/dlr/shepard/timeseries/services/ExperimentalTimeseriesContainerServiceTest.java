package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.io.TimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.io.TimeseriesPayloadIO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ExperimentalTimeseriesContainerServiceTest {

  @Inject
  ExperimentalTimeseriesContainerService timeseriesService;

  @Test
  public void createContainer_containerDoesNotExist_containerIsCreated() {
    var containerName = "Testcontainer";
    var userName = "Testuser";

    var created = timeseriesService.createContainer(containerName, userName);

    Assertions.assertEquals(created.getName(), containerName);
    Assertions.assertTrue(created.getId() > 0);
  }

  @Test
  public void addPayload_firstPayloadOfTimeseries_payloadIsAddedToDb() {
    var containerName = "AnotherContainer";
    var userName = "Testuser";

    var container = timeseriesService.createContainer(containerName, userName);

    ExperimentalTimeseries timeseries = new ExperimentalTimeseries();
    timeseries.setContainerId(container.getId());
    timeseries.setDevice("device");
    timeseries.setField("field");
    timeseries.setLocation("location");
    timeseries.setMeasurement("measurement");
    timeseries.setSymbolicName("symbolicName");

    List<TimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    TimeseriesPayloadDataPointIO point1 = new TimeseriesPayloadDataPointIO();

    point1.setTimestamp(Instant.now().toEpochMilli());
    point1.setValue(123);
    dataPoints.add(point1);

    TimeseriesPayloadIO payload = new TimeseriesPayloadIO();
    payload.setTimeseries(timeseries);
    payload.setPoints(dataPoints);

    var created = this.timeseriesService.addPayload(container.getId(), payload);
    Assertions.assertNotNull(created);
  }
}
