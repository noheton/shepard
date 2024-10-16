package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.io.TimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.io.TimeseriesPayloadIO;
import de.dlr.shepard.timeseries.utilities.LocalDateTimeHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
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
  public void addPayload_addDoubleValue_success() {
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
    point1.setValue(123.0);
    dataPoints.add(point1);

    TimeseriesPayloadIO payload = new TimeseriesPayloadIO();
    payload.setTimeseries(timeseries);
    payload.setPoints(dataPoints);

    var created = this.timeseriesService.addPayload(container.getId(), payload);
    Assertions.assertNotNull(created);

    var actual =
      this.timeseriesService.getDataPoints(container.getId(), timeseries, 0L, 1_000_000_000_000_000_000L, 0L);
    Assert.assertNotNull(actual);
    Assert.assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    Assert.assertEquals("DoubleValue must be taken over.", point1.getValue(), actualPoint.getDoubleValue());
    Assert.assertTrue("Id must be set.", actualPoint.getId() > 0);
    Assert.assertEquals("StringValue must be null.", null, actualPoint.getStringValue());
    // Assert.assertEquals("BooleanValue must be null.", null, actualPoint.getBooleanValue());
    // Assert.assertEquals("IntValue must be null.", null, actualPoint.getIntValue());
    Assert.assertEquals(
      "Timestamp must be taken over.",
      LocalDateTimeHelper.fromMilliseconds(point1.getTimestamp()),
      actualPoint.getTime()
    );
    Assert.assertTrue("TimeseriesId must be unequal to 0.", actualPoint.getTimeseriesId() > 0);
  }
}
