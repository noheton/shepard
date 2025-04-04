package de.dlr.shepard.data.timeseries.services;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.utilities.CsvConverter;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RequestScoped
public class TimeseriesCsvService {

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

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
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    var stream = CsvConverter.convertToCsv(
      timeseries,
      this.timeseriesService.getDataPointsByTimeseries(containerId, timeseries, queryParams)
    );
    return stream;
  }

  /**
   * Export a list of timeseries with data points as CSV File.
   *
   * @param containerId           The timeseries container Id
   * @param timeseriesList        The list of timeseries whose points are queried
   * @param queryParams           Query params containing Aggregate functions, fill option, ...
   * @return InputStream containing the CSV file
   * @throws IOException When the CSV file could not be written
   */
  public InputStream exportManyTimeseriesWithDataPointsToCsv(
    Long containerId,
    List<Timeseries> timeseriesList,
    TimeseriesDataPointsQueryParams queryParams
  ) throws IOException {
    timeseriesContainerService.getContainer(containerId);

    var timeseriesWithDataPointsList = timeseriesService.getManyTimeseriesWithDataPoints(
      containerId,
      timeseriesList,
      queryParams
    );
    var stream = CsvConverter.convertToCsv(timeseriesWithDataPointsList);
    return stream;
  }

  public void importTimeseriesFromCsv(long containerId, String filePath) throws IOException {
    timeseriesContainerService.getContainer(containerId);
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);

    try (InputStream fileInputStream = new FileInputStream(filePath)) {
      List<TimeseriesWithDataPoints> timeseriesWithDataPointsList = CsvConverter.convertToTimeseriesWithData(
        fileInputStream
      );
      for (var timeseriesWithDataPoints : timeseriesWithDataPointsList) {
        this.timeseriesService.saveDataPoints(
            containerId,
            timeseriesWithDataPoints.getTimeseries(),
            timeseriesWithDataPoints.getPoints()
          );
      }
    }
  }
}
