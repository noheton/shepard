package de.dlr.shepard.provenance.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProvenanceRetentionJobTest {

  @Mock
  ActivityDAO activityDAO;

  ProvenanceRetentionJob job;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    job = new ProvenanceRetentionJob();
    job.activityDAO = activityDAO;
    job.provenanceEnabled = true;
    job.retentionDays = 730L;
  }

  @Test
  void runDeletesRowsOlderThanCutoff() {
    when(activityDAO.deleteOlderThan(anyLong())).thenReturn(42L);

    long beforeRun = System.currentTimeMillis();
    job.runNightly();
    long afterRun = System.currentTimeMillis();

    ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
    verify(activityDAO).deleteOlderThan(cutoff.capture());
    long expectedLow = beforeRun - 730L * ProvenanceRetentionJob.MILLIS_PER_DAY;
    long expectedHigh = afterRun - 730L * ProvenanceRetentionJob.MILLIS_PER_DAY;
    assertTrue(cutoff.getValue() >= expectedLow);
    assertTrue(cutoff.getValue() <= expectedHigh);
  }

  @Test
  void runSkippedWhenProvenanceDisabled() {
    job.provenanceEnabled = false;
    job.runNightly();
    verify(activityDAO, never()).deleteOlderThan(anyLong());
  }

  @Test
  void runSkippedWhenRetentionNegative() {
    job.retentionDays = -1L;
    job.runNightly();
    verify(activityDAO, never()).deleteOlderThan(anyLong());
  }

  @Test
  void runSwallowsDAOFailure() {
    when(activityDAO.deleteOlderThan(anyLong())).thenThrow(new RuntimeException("Neo4j burped"));
    // Must not throw — retention failures are not request-blocking.
    job.runNightly();
    verify(activityDAO, times(1)).deleteOlderThan(anyLong());
  }

  @Test
  void currentCutoffMillisRespectsRetentionDays() {
    job.retentionDays = 30L;
    long now = System.currentTimeMillis();
    long cutoff = job.currentCutoffMillis();
    long expected = now - 30L * ProvenanceRetentionJob.MILLIS_PER_DAY;
    // Within 100ms of expected (clock advanced between two calls).
    assertTrue(Math.abs(cutoff - expected) < 100, "cutoff was " + cutoff + ", expected near " + expected);
  }

  @Test
  void millisPerDayConstantSane() {
    assertEquals(86_400_000L, ProvenanceRetentionJob.MILLIS_PER_DAY);
  }
}
