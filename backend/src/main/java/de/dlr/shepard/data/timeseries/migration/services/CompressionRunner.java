package de.dlr.shepard.data.timeseries.migration.services;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@RequestScoped
public class CompressionRunner implements Callable<Object> {

  @Inject
  TimeseriesMigrationService migrationService;

  @Override
  public Object call() {
    try {
      BlockingQueue<CompressionTask> queue = migrationService.getCompressionTasksQueue();
      ReentrantReadWriteLock lock = migrationService.getReadWriteLock();
      while (true) {
        CompressionTask task = queue.take();
        try {
          Log.info("Compression task waiting for write lock");
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
