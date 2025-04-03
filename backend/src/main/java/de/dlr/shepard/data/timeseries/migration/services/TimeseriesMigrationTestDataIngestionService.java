package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.common.exceptions.ShepardProcessingException;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxDBConnector;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxPoint;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesPayload;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

@RequestScoped
public class TimeseriesMigrationTestDataIngestionService {

  private InfluxDBConnector influxConnector;

  TimeseriesContainerDAO timeseriesContainerDao;

  private int PAYLOAD_MAX_SIZE = 200000;

  @Inject
  TimeseriesMigrationTestDataIngestionService(
    InfluxDBConnector influxConnector,
    TimeseriesContainerDAO timeseriesContainerDao
  ) {
    this.influxConnector = influxConnector;
    this.timeseriesContainerDao = timeseriesContainerDao;
  }

  /**
   * @return Newly created Influx Timeseries Container
   */
  public TimeseriesContainer ingestTestData(
    String databaseName,
    int datasetSize,
    String userName,
    DataPointValueType dataPointValueType
  ) {
    if (influxConnector.databaseExist(databaseName)) {
      throw new ShepardProcessingException("Database already exists: " + databaseName);
    }
    Log.infof("ingestTestData started, size: %s, databaseName: %s", datasetSize, databaseName);
    var containerName = String.format("Container-%d", System.currentTimeMillis());
    TimeseriesContainer entity = new TimeseriesContainer();
    entity.setDatabase(databaseName);
    entity.setName(containerName);

    timeseriesContainerDao.createOrUpdate(entity);
    influxConnector.createDatabase(databaseName);

    int remainingDataSize = datasetSize;
    long timeOffset = 0;
    while (remainingDataSize > 0) {
      Log.infof("ingestTestData, remaining: %s", remainingDataSize);
      int payloadSize = Math.min(PAYLOAD_MAX_SIZE, remainingDataSize);
      InfluxTimeseriesPayload payload = getRandomPayload(dataPointValueType, payloadSize, timeOffset);
      influxConnector.saveTimeseriesPayload(databaseName, payload);
      remainingDataSize -= payloadSize;
      timeOffset += payloadSize;
    }
    Log.infof("ingestTestData finished, databaseName: %s", databaseName);
    return entity;
  }

  private InfluxTimeseriesPayload getRandomPayload(
    DataPointValueType dataPointValueType,
    int payloadSize,
    long timeOffset
  ) {
    InfluxTimeseries timeseries = new InfluxTimeseries("measurement", "device", "location", "symbolicName", "field");
    List<InfluxPoint> points = new ArrayList<>();

    IntStream.range(0, payloadSize).forEach(i -> {
      points.add(getRandomInfluxPoint(dataPointValueType, (timeOffset + i) * 100_000_000)); // one data point per 100 milliseconds
    });
    InfluxTimeseriesPayload payload = new InfluxTimeseriesPayload(timeseries, points);
    return payload;
  }

  private InfluxPoint getRandomInfluxPoint(DataPointValueType dataPointValueType, long timestamp) {
    switch (dataPointValueType) {
      case Boolean:
        return new InfluxPoint(timestamp, new Random().nextBoolean());
      case String:
        return new InfluxPoint(timestamp, String.format("String-%d", System.currentTimeMillis() * 1_000_000));
      case Integer:
        return new InfluxPoint(timestamp, new Random().nextInt());
      case Double:
        return new InfluxPoint(timestamp, new Random().nextDouble());
    }
    return null;
  }
}
