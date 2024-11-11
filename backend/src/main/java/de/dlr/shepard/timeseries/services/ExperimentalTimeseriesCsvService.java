package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointsQueryParams;
import de.dlr.shepard.timeseries.utilities.CsvConverter;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

@RequestScoped
public class ExperimentalTimeseriesCsvService {

  private ExperimentalTimeseriesService timeseriesService;

  ExperimentalTimeseriesCsvService() {}

  @Inject
  public ExperimentalTimeseriesCsvService(ExperimentalTimeseriesService timeseriesService) {
    this.timeseriesService = timeseriesService;
  }

  /**
   * Export one timeseries as CSV File if found.
   *
   * @param containerId           Id of the container in Neo4j
   * @param timeseriesList        The list of timeseries whose points are queried
   * @param queryParams           The query params to fetch the data points
   * @return InputStream containing the CSV file
   */
  public InputStream exportTimeseriesDataToCsv(
    long containerId,
    ExperimentalTimeseries timeseries,
    ExperimentalTimeseriesDataPointsQueryParams queryParams
  ) {
    var stream = CsvConverter.convertToCsv(
      timeseries,
      this.timeseriesService.getDataPointsByTimeseries(containerId, timeseries, queryParams)
    );
    return stream;
  }

  public void importTimeseriesFromCsv(TimeseriesContainer container, String filePath) throws IOException {
    try (InputStream fileInputStream = new FileInputStream(filePath)) {
      HashMap<ExperimentalTimeseries, List<ExperimentalTimeseriesDataPoint>> timeseriesList =
        CsvConverter.convertToTimeseriesWithData(fileInputStream);
      for (var timeseries : timeseriesList.entrySet()) {
        this.timeseriesService.saveDataPoints(container, timeseries.getKey(), timeseries.getValue());
      }
    }
  }
}
