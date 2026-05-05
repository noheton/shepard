package de.dlr.shepard.common.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.simons.neo4j.migrations.core.MigrationsException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

public class MigrationsRunnerTest {

  @Test
  public void awaitConnectivity_returnsImmediatelyWhenCheckSucceeds() {
    AtomicInteger calls = new AtomicInteger();
    List<Long> sleeps = new ArrayList<>();

    MigrationsRunner.awaitConnectivity(
      calls::incrementAndGet,
      Duration.ofSeconds(60),
      sleeps::add,
      virtualClock(0L)
    );

    assertEquals(1, calls.get());
    assertTrue(sleeps.isEmpty(), "no sleep expected on first-attempt success");
  }

  @Test
  public void awaitConnectivity_throwsConnectionWaitTimeoutWhenDeadlineExceeded() {
    RuntimeException failure = new IllegalStateException("db not ready");
    Runnable alwaysFails = () -> {
      throw failure;
    };
    List<Long> sleeps = new ArrayList<>();
    long[] now = { 0L };
    MigrationsRunner.NanoClock clock = () -> now[0];
    MigrationsRunner.Sleeper sleeper = millis -> {
      sleeps.add(millis);
      now[0] += millis * 1_000_000L;
    };

    Duration timeout = Duration.ofMillis(100);
    ConnectionWaitTimeoutException ex = assertThrows(
      ConnectionWaitTimeoutException.class,
      () -> MigrationsRunner.awaitConnectivity(alwaysFails, timeout, sleeper, clock)
    );

    assertSame(failure, ex.getCause(), "the most recent failure should be chained as cause");
    assertNotNull(ex.getMessage());
    assertTrue(ex.getMessage().contains(timeout.toString()), "message should mention configured timeout");
    long totalSleptNanos = sleeps.stream().mapToLong(Long::longValue).sum() * 1_000_000L;
    assertTrue(
      totalSleptNanos <= timeout.toNanos(),
      "total slept time " + totalSleptNanos + "ns must not exceed configured timeout " + timeout.toNanos() + "ns"
    );
  }

  @Test
  public void awaitConnectivity_usesExponentialBackoffCappedAtMax() {
    Runnable alwaysFails = () -> {
      throw new IllegalStateException("nope");
    };
    List<Long> sleeps = new ArrayList<>();
    long[] now = { 0L };
    MigrationsRunner.NanoClock clock = () -> now[0];
    MigrationsRunner.Sleeper sleeper = millis -> {
      sleeps.add(millis);
      now[0] += millis * 1_000_000L;
    };

    Duration timeout = Duration.ofSeconds(30);
    assertThrows(
      ConnectionWaitTimeoutException.class,
      () -> MigrationsRunner.awaitConnectivity(alwaysFails, timeout, sleeper, clock)
    );

    assertEquals(MigrationsRunner.INITIAL_BACKOFF.toMillis(), (long) sleeps.get(0));
    assertEquals(MigrationsRunner.INITIAL_BACKOFF.toMillis() * 2, (long) sleeps.get(1));
    assertEquals(MigrationsRunner.INITIAL_BACKOFF.toMillis() * 4, (long) sleeps.get(2));
    long maxMillis = MigrationsRunner.MAX_BACKOFF.toMillis();
    assertTrue(
      sleeps.stream().allMatch(s -> s <= maxMillis),
      "no individual sleep should exceed MAX_BACKOFF (" + maxMillis + " ms): " + sleeps
    );
    assertTrue(sleeps.stream().anyMatch(s -> s == maxMillis), "backoff should reach MAX_BACKOFF: " + sleeps);
  }

  @Test
  public void awaitConnectivity_succeedsAfterTransientFailures() {
    AtomicInteger attempts = new AtomicInteger();
    Runnable flaky = () -> {
      if (attempts.incrementAndGet() < 3) {
        throw new IllegalStateException("warming up");
      }
    };
    List<Long> sleeps = new ArrayList<>();
    long[] now = { 0L };
    MigrationsRunner.NanoClock clock = () -> now[0];
    MigrationsRunner.Sleeper sleeper = millis -> {
      sleeps.add(millis);
      now[0] += millis * 1_000_000L;
    };

    MigrationsRunner.awaitConnectivity(flaky, Duration.ofSeconds(10), sleeper, clock);

    assertEquals(3, attempts.get());
    assertEquals(2, sleeps.size(), "sleeps only happen between failed attempts");
  }

  @Test
  public void awaitConnectivity_propagatesInterruptionAsTimeoutException() {
    Runnable alwaysFails = () -> {
      throw new IllegalStateException("nope");
    };
    MigrationsRunner.Sleeper interrupter = millis -> {
      throw new InterruptedException("test");
    };

    try {
      ConnectionWaitTimeoutException ex = assertThrows(
        ConnectionWaitTimeoutException.class,
        () -> MigrationsRunner.awaitConnectivity(alwaysFails, Duration.ofSeconds(10), interrupter, virtualClock(0L))
      );
      assertTrue(ex.getMessage().toLowerCase().contains("interrupt"));
      assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag should be re-set");
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void runMigrations_propagatesMigrationsExceptionAsRuntimeException() {
    MigrationsException failure = new MigrationsException("boom");
    Runnable failing = () -> {
      throw failure;
    };

    RuntimeException ex = assertThrows(
      RuntimeException.class,
      () -> MigrationsRunner.runMigrations(failing)
    );

    assertSame(failure, ex.getCause(), "underlying MigrationsException should be chained as cause");
  }

  @Test
  public void runMigrations_propagatesServiceUnavailableAsRuntimeException() {
    ServiceUnavailableException failure = new ServiceUnavailableException("db gone");
    Runnable failing = () -> {
      throw failure;
    };

    RuntimeException ex = assertThrows(
      RuntimeException.class,
      () -> MigrationsRunner.runMigrations(failing)
    );

    assertSame(failure, ex.getCause(), "underlying ServiceUnavailableException should be chained as cause");
  }

  @Test
  public void runMigrations_returnsNormallyOnSuccess() {
    MigrationsRunner.runMigrations(() -> {});
  }

  private static MigrationsRunner.NanoClock virtualClock(long startNanos) {
    long[] now = { startNanos };
    return () -> now[0];
  }
}
