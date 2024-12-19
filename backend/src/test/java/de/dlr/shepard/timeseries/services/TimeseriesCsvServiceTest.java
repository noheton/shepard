package de.dlr.shepard.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.timeseries.model.Timeseries;
import de.dlr.shepard.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.timeseries.utilities.CsvConverter;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TimeseriesCsvServiceTest {

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesCsvService timeseriesCsvService;

  private final String containerName = "AnotherContainer";
  private final String userName = "Testuser";

  /**********************
   * exportTimeseriesDataToCsv
   ***********************/

  @Test
  @Transactional
  public void exportTimeseriesDataToCsv_oneTimeseriesWithDoubleValues_success() throws IOException, URISyntaxException {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("water_level");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 120.57),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 127.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(3).toNano(), 129.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 134.0)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.toNano(),
      null,
      null,
      null
    );
    var actual = this.timeseriesCsvService.exportTimeseriesDataToCsv(container.getId(), timeseries, queryParams);

    StringBuilder actualCsvContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actual))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualCsvContent.append(line).append("\n");
      }
    }

    var expectedCsvFile = new File(
      getClass().getClassLoader().getResource("timeseries_export_experimental_double.csv").toURI()
    );
    var expectedCsvContent = Files.readString(expectedCsvFile.toPath());

    assertEquals(actualCsvContent.toString().trim(), expectedCsvContent.trim());
  }

  @Test
  @Transactional
  public void exportTimeseriesDataToCsv_oneTimeseriesWithStringValues_success() throws IOException, URISyntaxException {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("status");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointString(instantHelper.toNano(), "running"))
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      instantHelper.toNano(),
      instantHelper.addSeconds(2).toNano(),
      null,
      null,
      null
    );
    var actual = this.timeseriesCsvService.exportTimeseriesDataToCsv(container.getId(), timeseries, queryParams);

    StringBuilder actualCsvContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actual))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualCsvContent.append(line).append("\n");
      }
    }

    var expectedCsvFile = new File(
      getClass().getClassLoader().getResource("timeseries_export_experimental_string.csv").toURI()
    );
    var expectedCsvContent = Files.readString(expectedCsvFile.toPath());

    assertEquals(actualCsvContent.toString().trim(), expectedCsvContent.trim());
  }

  @Test
  @Transactional
  public void exportTimeseriesDataToCsv_oneTimeseriesWithBooleanValues_success()
    throws IOException, URISyntaxException {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("motion");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(2).toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(3).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(4).toNano(), true)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.toNano(),
      null,
      null,
      null
    );
    var actual = this.timeseriesCsvService.exportTimeseriesDataToCsv(container.getId(), timeseries, queryParams);

    StringBuilder actualCsvContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actual))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualCsvContent.append(line).append("\n");
      }
    }

    var expectedCsvFile = new File(
      getClass().getClassLoader().getResource("timeseries_export_experimental_boolean.csv").toURI()
    );
    var expectedCsvContent = Files.readString(expectedCsvFile.toPath());

    assertEquals(actualCsvContent.toString().trim(), expectedCsvContent.trim());
  }

  /**********************
   * importTimeseriesFromCsv
   ***********************/

  @Test
  @Transactional
  public void importTimeseriesFromCsv_multipleTimeseriesWithMultipleValues_success()
    throws IOException, URISyntaxException {
    var container = timeseriesContainerService.createContainer(containerName, userName);

    File importCSVFile = new File(
      getClass().getClassLoader().getResource("timeseries_import_experimental.csv").toURI()
    );

    String csvFileContent = Files.readString(importCSVFile.toPath());

    timeseriesCsvService.importTimeseriesFromCsv(container, importCSVFile.toPath().toString());

    List<TimeseriesEntity> availTimeseriesList = timeseriesService.getTimeseriesAvailable(container.getId());

    List<Timeseries> expTimeseries = new ArrayList<Timeseries>();

    for (var currTimeseries : availTimeseriesList) {
      expTimeseries.add(
        new Timeseries(
          currTimeseries.getMeasurement(),
          currTimeseries.getDevice(),
          currTimeseries.getLocation(),
          currTimeseries.getSymbolicName(),
          currTimeseries.getField()
        )
      );
    }

    var actualTimeseriesDataMap = new ArrayList<TimeseriesWithDataPoints>();
    expTimeseries
      .stream()
      .forEach(timeseries -> {
        actualTimeseriesDataMap.add(
          new TimeseriesWithDataPoints(
            timeseries,
            timeseriesService.getDataPointsByTimeseries(
              container.getId(),
              timeseries,
              new TimeseriesDataPointsQueryParams(
                InstantHelper.fromGermanDate("01.01.2024").addHours(-1).toNano(),
                InstantHelper.fromGermanDate("01.01.2024").addHours(1).toNano(),
                null,
                null,
                null
              )
            )
          )
        );
      });

    var actualTimeSeriesStream = CsvConverter.convertToCsv(actualTimeseriesDataMap);

    int lineCounter = 0;

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actualTimeSeriesStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lineCounter += 1;
        assertTrue(
          csvFileContent.contains(line),
          String.format("Line '%s' is not contained in original CSV file", line)
        );
      }
    }
    // make sure the number of lines in both CSV files are equal
    assertEquals(csvFileContent.split("\n").length, lineCounter);
  }

  @Test
  @Transactional
  public void importTimeseriesFromCsv_emptyTimeseries_noDataCreation() throws IOException, URISyntaxException {
    var container = timeseriesContainerService.createContainer(containerName, userName);

    File importCSVFile = new File(
      getClass().getClassLoader().getResource("timeseries_import_experimental_empty.csv").toURI()
    );

    timeseriesCsvService.importTimeseriesFromCsv(container, importCSVFile.toPath().toString());

    List<TimeseriesEntity> availTimeseriesList = timeseriesService.getTimeseriesAvailable(container.getId());

    assertEquals(
      0,
      availTimeseriesList.size(),
      "Imported empty timeseries payload, but a timeseries was created from this empty payload."
    );
  }
}
