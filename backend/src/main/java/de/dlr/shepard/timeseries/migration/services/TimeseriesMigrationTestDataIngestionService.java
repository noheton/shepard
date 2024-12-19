package de.dlr.shepard.timeseries.migration.services;

import de.dlr.shepard.exceptions.ShepardProcessingException;
import de.dlr.shepard.influxtimeseries.InfluxDBConnector;
import de.dlr.shepard.influxtimeseries.InfluxPoint;
import de.dlr.shepard.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.influxtimeseries.InfluxTimeseriesPayload;
import de.dlr.shepard.timeseries.model.enums.DataPointValueType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

@RequestScoped
@Transactional
public class TimeseriesMigrationTestDataIngestionService {

  private InfluxDBConnector influxConnector;

  private int PAYLOAD_MAX_SIZE = 100;

  @Inject
  TimeseriesMigrationTestDataIngestionService(InfluxDBConnector influxConnector) {
    this.influxConnector = influxConnector;
  }

  public void ingestTestData(
    String databaseName,
    int datasetSize,
    String userName,
    DataPointValueType dataPointValueType
  ) {
    if (influxConnector.databaseExist(databaseName)) {
      throw new ShepardProcessingException("Database already exists: " + databaseName);
    }
    influxConnector.createDatabase(databaseName);

    int remainingDataSize = datasetSize;
    long timeOffset = 0;
    while (remainingDataSize > 0) {
      int payloadSize = Math.min(PAYLOAD_MAX_SIZE, remainingDataSize);
      InfluxTimeseriesPayload payload = getRandomPayload(dataPointValueType, payloadSize, timeOffset);
      influxConnector.saveTimeseriesPayload(databaseName, payload);
      remainingDataSize -= payloadSize;
      timeOffset += payloadSize;
    }
  }

  private InfluxTimeseriesPayload getRandomPayload(
    DataPointValueType dataPointValueType,
    int payloadSize,
    long timeOffset
  ) {
    InfluxTimeseries timeseries = new InfluxTimeseries("measurement", "device", "location", "symbolicName", "field");
    List<InfluxPoint> points = new ArrayList<>();

    IntStream.range(0, payloadSize).forEach(i -> {
      points.add(getRandomInfluxPoint(dataPointValueType, (timeOffset + i) * 1000_000));
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
