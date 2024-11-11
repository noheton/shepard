package de.dlr.shepard.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointsQueryParams;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
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
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ExperimentalTimeseriesCsvServiceTest {

  @Inject
  ExperimentalTimeseriesContainerService timeseriesContainerService;

  @Inject
  ExperimentalTimeseriesService timeseriesService;

  @Inject
  ExperimentalTimeseriesCsvService timeseriesCsvService;

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
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 120.57),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 127.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(3).toNano(), 129.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 134.0)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
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
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointString(instantHelper.toNano(), "running"))
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
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
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(2).toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(3).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(4).toNano(), true)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
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

    List<ExperimentalTimeseriesEntity> availTimeseriesList = timeseriesService.getTimeseriesAvailable(
      container.getId()
    );

    List<ExperimentalTimeseries> expTimeseries = new ArrayList<ExperimentalTimeseries>();

    for (var currTimeseries : availTimeseriesList) {
      expTimeseries.add(
        new ExperimentalTimeseries(
          currTimeseries.getMeasurement(),
          currTimeseries.getDevice(),
          currTimeseries.getLocation(),
          currTimeseries.getSymbolicName(),
          currTimeseries.getField()
        )
      );
    }

    var timeseriesDataQueue = new HashMap<ExperimentalTimeseries, List<ExperimentalTimeseriesDataPoint>>();
    expTimeseries
      .stream()
      .forEach(timeseries -> {
        timeseriesDataQueue.put(
          timeseries,
          timeseriesService.getDataPointsByTimeseries(
            container.getId(),
            timeseries,
            new ExperimentalTimeseriesDataPointsQueryParams(
              InstantHelper.fromGermanDate("01.01.2024").addHours(-1).toNano(),
              InstantHelper.fromGermanDate("01.01.2024").addHours(1).toNano(),
              null,
              null,
              null
            )
          )
        );
      });
    var actualTimeseriesDataMap = new HashMap<ExperimentalTimeseries, List<ExperimentalTimeseriesDataPoint>>(
      timeseriesDataQueue
    );

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

    List<ExperimentalTimeseriesEntity> availTimeseriesList = timeseriesService.getTimeseriesAvailable(
      container.getId()
    );

    assertEquals(
      0,
      availTimeseriesList.size(),
      "Imported empty timeseries payload, but a timeseries was created from this empty payload."
    );
  }
}
