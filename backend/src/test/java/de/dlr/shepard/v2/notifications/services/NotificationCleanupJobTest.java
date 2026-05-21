package de.dlr.shepard.v2.notifications.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.notifications.daos.NotificationDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class NotificationCleanupJobTest {

  @Mock
  NotificationDAO dao;

  NotificationCleanupJob job;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    job = new NotificationCleanupJob();
    job.dao = dao;
  }

  @Test
  void runNightly_deletesExpiredBeforeNow() {
    when(dao.deleteExpiredBefore(longThat(v -> v > 0))).thenReturn(5L);
    long before = System.currentTimeMillis();

    job.runNightly();

    long after = System.currentTimeMillis();
    verify(dao).deleteExpiredBefore(longThat(v -> v >= before && v <= after));
  }

  @Test
  void runNightly_handlesZeroDeleted() {
    when(dao.deleteExpiredBefore(longThat(v -> v > 0))).thenReturn(0L);

    // Should not throw.
    job.runNightly();

    verify(dao).deleteExpiredBefore(longThat(v -> v > 0));
  }

  @Test
  void runNightly_handlesDAOException() {
    when(dao.deleteExpiredBefore(longThat(v -> v > 0))).thenThrow(new RuntimeException("Neo4j timeout"));

    // Must swallow the exception and not propagate.
    job.runNightly();
  }
}
