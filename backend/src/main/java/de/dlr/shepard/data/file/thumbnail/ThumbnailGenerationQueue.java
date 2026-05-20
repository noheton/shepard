package de.dlr.shepard.data.file.thumbnail;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * TH1a — bounded worker pool for thumbnail generation.
 *
 * <p>Requests submitted via {@link #submit(Callable)} are queued behind a
 * fixed pool of {@code shepard.thumbnail.workers} threads. If the queue
 * is full a {@link RejectedExecutionException} is propagated (→ HTTP 503).
 * If generation takes longer than {@code shepard.thumbnail.timeout-ms} the
 * future is cancelled and a {@link TimeoutException} is propagated (→ HTTP 503).
 *
 * <p>Pool size defaults to 4 — enough for interactive page-loads without
 * saturating the CPU at MFFD dataset scale (thousands of files per page).
 */
@ApplicationScoped
public class ThumbnailGenerationQueue {

  @ConfigProperty(name = "shepard.thumbnail.workers", defaultValue = "4")
  int workers;

  @ConfigProperty(name = "shepard.thumbnail.timeout-ms", defaultValue = "5000")
  long timeoutMs;

  private volatile ThreadPoolExecutor executor;

  ThreadPoolExecutor executor() {
    if (executor == null) {
      synchronized (this) {
        if (executor == null) {
          int queueCapacity = workers * 8;
          executor = new ThreadPoolExecutor(
            workers, workers,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            r -> {
              Thread t = new Thread(r, "thumbnail-worker");
              t.setDaemon(true);
              return t;
            }
          );
        }
      }
    }
    return executor;
  }

  /**
   * Submit a generation task and block until it completes or times out.
   *
   * @return generated PNG bytes
   * @throws RejectedExecutionException if the internal queue is full
   * @throws TimeoutException if generation exceeds {@code shepard.thumbnail.timeout-ms}
   * @throws Exception if the provider itself threw
   */
  public byte[] submit(Callable<byte[]> task) throws Exception {
    Future<byte[]> future = executor().submit(task);
    try {
      return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException te) {
      future.cancel(true);
      Log.warnf("ThumbnailGenerationQueue: generation timed out after %d ms", timeoutMs);
      throw te;
    } catch (ExecutionException ee) {
      Throwable cause = ee.getCause();
      if (cause instanceof Exception ex) throw ex;
      throw new RuntimeException(cause);
    }
  }

  void onShutdown(@Observes ShutdownEvent ev) {
    if (executor != null) {
      executor.shutdownNow();
    }
  }
}
