package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;

@ApplicationScoped
public class MigrationRunner {

  @Inject
  MigrationProgressService progressService;

  @Inject
  TimeseriesDataPointRepository dataPointRepository;

  /**
   * Migrate all rows for one container. Progress is recorded after every batch commit
   * so the run is resumable if the JVM dies mid-way.
   */
  public void migrateContainer(long containerId, List<TimeseriesDataPoint> rows, TimeseriesEntity timeseries)
    throws SQLException {
    if (progressService.shouldSkip(containerId)) {
      Log.infof("Skipping container %d (already COMPLETED)", containerId);
      return;
    }

    int resumeFrom = progressService.resumeBatchIndex(containerId);
    progressService.start(containerId, rows.size());

    try {
      dataPointRepository.insertManyDataPointsWithCopyCommandBatched(
        rows,
        timeseries,
        resumeFrom,
        (batchIndex, rowsInBatch) -> progressService.recordBatch(containerId, batchIndex + 1, rowsInBatch),
        (batchIndex, error) ->
          progressService.recordError(
            containerId,
            0,
            "batch %d failed: %s".formatted(batchIndex, error.getMessage())
          )
      );
      progressService.complete(containerId);
    } catch (SQLException | RuntimeException ex) {
      progressService.fail(containerId, ex.getMessage());
      throw ex;
    }
  }
}
