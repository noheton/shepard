package de.dlr.shepard.data.spatialdata.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataPoint;
import de.dlr.shepard.data.spatialdata.repositories.SpatialDataPointRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Permission-gate regression for the brush-trace promotion endpoint
 * (MFFD-SPATIAL-LINESCAN-IMPORTER-1, 2026-06-02).
 *
 * <p>The line-scan importer uploads its rows via
 * {@code POST /shepard/api/spatialDataContainers/{id}/payload}, which hits
 * {@link SpatialDataPointService#createSpatialDataPoints}. That service must
 * gate every write on
 * {@link SpatialDataContainerService#assertIsAllowedToEditContainer} — a
 * caller without Write must never persist points. This test pins that wiring
 * so a refactor cannot silently drop the gate (the gate is the only thing
 * between an unauth'd caller and 313 M JSONB rows on the postgres side).
 *
 * <p>Sibling of {@link SpatialDataPointServiceTest} — that suite covers the
 * happy path; this file covers the forbidden path.
 */
@QuarkusComponentTest
public class BrushTracePermissionGateTest {

  @Inject
  SpatialDataPointService spatialDataPointService;

  @InjectMock
  SpatialDataPointRepository spatialDataPointRepository;

  @InjectMock
  SpatialDataContainerService spatialDataContainerService;

  /**
   * Caller without Write permission must trip the gate before any persistence
   * call lands on the repository.
   */
  @Test
  public void createSpatialDataPoints_forbidden_doesNotPersist() {
    Long containerId = 42L;
    var points = List.of(
      new SpatialDataPointIO(
        1L,
        0.0,
        0.0,
        0.0,
        Map.of(),
        Map.of("kind", "brush-trace", "intensities", List.of(1, 2, 3))
      )
    );

    doThrow(new ForbiddenException("forbidden"))
      .when(spatialDataContainerService)
      .assertIsAllowedToEditContainer(containerId);

    assertThrows(
      ForbiddenException.class,
      () -> spatialDataPointService.createSpatialDataPoints(containerId, points)
    );

    // The repository.insert() call MUST NOT have happened — proving the gate
    // sits in front of the write, not after it.
    verify(spatialDataPointRepository, never()).insert(
      Mockito.anyLong(),
      Mockito.any(SpatialDataPoint[].class)
    );
  }

  /**
   * The gate is checked exactly once per upload batch — multiple brush-trace
   * rows in one POST share a single permission decision. This pins the
   * single-shot semantics so a refactor doesn't accidentally re-check per row
   * (which would amplify auth-service load by 964× per chunk).
   */
  @Test
  public void createSpatialDataPoints_authChecksOnce_perBatch() {
    Long containerId = 99L;
    var points = List.of(
      new SpatialDataPointIO(1L, 0.0, 0.0, 0.0, Map.of(), Map.of("intensities", List.of(1))),
      new SpatialDataPointIO(2L, 0.0, 1.0, 0.0, Map.of(), Map.of("intensities", List.of(2))),
      new SpatialDataPointIO(3L, 0.0, 2.0, 0.0, Map.of(), Map.of("intensities", List.of(3)))
    );

    spatialDataPointService.createSpatialDataPoints(containerId, points);

    verify(spatialDataContainerService, times(1)).assertIsAllowedToEditContainer(containerId);
  }
}
