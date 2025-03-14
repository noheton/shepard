package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@RequestScoped
public class CompressionRunner implements Callable<Object> {

  private TimeseriesMigrationService migrationService;

  @Inject
  CompressionRunner(TimeseriesMigrationService migrationService) {
    this.migrationService = migrationService;
  }

  @Override
  public Object call() {
    try {
      BlockingQueue<CompressionTask> queue = migrationService.getCompressionTasksQueue();
      ReentrantReadWriteLock lock = migrationService.getReadWriteLock();
      while (true) {
        CompressionTask task = queue.take();
        try {
          lock.writeLock().lock();
          if (task.isLastTask) break;
          migrationService.compressAllDataPoints();
        } finally {
          lock.writeLock().unlock();
        }
      }
    } catch (InterruptedException e) {
      Log.error(e);
      Thread.currentThread().interrupt();
    }
    return "Compression runner Done!";
  }
}
