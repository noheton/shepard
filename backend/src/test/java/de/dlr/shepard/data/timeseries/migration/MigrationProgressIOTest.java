package de.dlr.shepard.data.timeseries.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.dlr.shepard.data.timeseries.migration.io.MigrationProgressIO;
import de.dlr.shepard.data.timeseries.migration.model.MigrationProgress;
import de.dlr.shepard.data.timeseries.migration.model.MigrationProgressStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public class MigrationProgressIOTest {

  @Test
  public void mapsAllFields() {
    var p = new MigrationProgress(7L, 1000);
    p.setStatus(MigrationProgressStatus.RUNNING);
    p.setStartedAt(Instant.now().minusSeconds(60));
    p.setRowsMigrated(500);
    p.setRowsFailed(2);
    p.setLastBatchIndex(25);
    p.setErrors("err");

    var io = new MigrationProgressIO(p);

    assertEquals(7L, io.getContainerId());
    assertEquals(1000, io.getRowsTotal());
    assertEquals(500, io.getRowsMigrated());
    assertEquals(2, io.getRowsFailed());
    assertEquals(25, io.getLastBatchIndex());
    assertEquals(MigrationProgressStatus.RUNNING, io.getStatus());
    assertEquals("err", io.getErrors());
    assertNotNull(io.getEstimatedRemainingSeconds());
    assertTrue(io.getEstimatedRemainingSeconds() >= 0);
  }

  @Test
  public void noEstimateForCompleted() {
    var p = new MigrationProgress(1L, 1);
    p.setStatus(MigrationProgressStatus.COMPLETED);
    var io = new MigrationProgressIO(p);
    assertNull(io.getEstimatedRemainingSeconds());
  }

  @Test
  public void serializesAsExpectedJsonShape() throws Exception {
    var p = new MigrationProgress(7L, 200);
    p.setStatus(MigrationProgressStatus.RUNNING);
    p.setRowsMigrated(50);
    p.setLastBatchIndex(2);
    p.setStartedAt(Instant.parse("2025-01-01T00:00:00Z"));
    p.setLastUpdateAt(Instant.parse("2025-01-01T00:01:00Z"));

    var mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    var io = new MigrationProgressIO(p);
    var json = mapper.writeValueAsString(io);

    assertTrue(json.contains("\"containerId\":7"), json);
    assertTrue(json.contains("\"rowsTotal\":200"), json);
    assertTrue(json.contains("\"rowsMigrated\":50"), json);
    assertTrue(json.contains("\"rowsFailed\":0"), json);
    assertTrue(json.contains("\"lastBatchIndex\":2"), json);
    assertTrue(json.contains("\"status\":\"RUNNING\""), json);
    assertTrue(json.contains("\"startedAt\":\"2025-01-01T00:00:00Z\""), json);
    assertTrue(json.contains("\"lastUpdateAt\":\"2025-01-01T00:01:00Z\""), json);
  }
}
