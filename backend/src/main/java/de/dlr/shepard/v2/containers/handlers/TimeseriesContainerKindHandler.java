package de.dlr.shepard.v2.containers.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkChannelDataRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.CopyIngestRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.SpatialRolesIO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesChannelV2IO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * V2CONV-A3 — in-tree {@link ContainerKindHandler} for {@code kind=timeseries}.
 * Delegates create/get/delete/list to the existing
 * {@link TimeseriesContainerService} and resolves/renames via
 * {@link TimeseriesContainerDAO}. The frozen v1
 * {@code /shepard/api/timeseriesContainers} surface is untouched.
 */
@ApplicationScoped
public class TimeseriesContainerKindHandler implements ContainerKindHandler {

  private static final int BYTES_PER_POINT = 28;
  private static final long WINDOW_NS = 10_000_000_000L;

  @Inject
  TimeseriesContainerService service;

  @Inject
  TimeseriesContainerDAO dao;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @PersistenceContext
  EntityManager entityManager;

  // ── APISIMP-CONT-NS-COLLAPSE-2: channel-endpoint support ──────────────────

  @Inject
  TsChannelResolver tsChannelResolver;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  AnnotatableTimeseriesDAO annotatableTimeseriesDAO;

  private static final int MAX_PAGE_SIZE    = 1000;
  private static final int DEFAULT_MAX_POINTS = 2000;
  private static final int HARD_MAX_POINTS    = 5000;

  @Override
  public String kind() {
    return "timeseries";
  }

  @Override
  public boolean owns(BasicContainer container) {
    return container instanceof TimeseriesContainer;
  }

  @Override
  public BasicContainer findByAppId(String appId) {
    return dao.findByAppId(appId).filter(c -> !c.isDeleted()).orElse(null);
  }

  @Override
  public ContainerV2IO toIO(BasicContainer container) {
    return new ContainerV2IO(container, kind());
  }

  @Override
  public ContainerV2IO create(Map<String, Object> body) {
    String name = ContainerPatchSupport.requireName(body);
    var in = new TimeseriesContainerIO();
    in.setName(name);
    TimeseriesContainer created = service.createContainer(in);
    return toIO(created);
  }

  @Override
  public ContainerV2IO patch(String appId, Map<String, Object> patch) {
    TimeseriesContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) throw new NotFoundException("No timeseries container with appId " + appId);
    boolean changed = ContainerPatchSupport.applyMutableFields(c, patch);
    if (changed) {
      User user = userService.getCurrentUser();
      c.setUpdatedAt(dateHelper.getDate());
      c.setUpdatedBy(user);
      c = dao.createOrUpdate(c);
    }
    return toIO(c);
  }

  @Override
  public void delete(String appId) {
    TimeseriesContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) throw new NotFoundException("No timeseries container with appId " + appId);
    service.deleteContainer(c.getId());
  }

  @Override
  public List<ContainerV2IO> list(String nameFilter) {
    var params = new QueryParamHelper();
    if (nameFilter != null && !nameFilter.isBlank()) params = params.withName(nameFilter);
    return service.getAllContainers(params).stream().map(c -> (ContainerV2IO) toIO(c)).toList();
  }

  @Override
  public Optional<ContainerStatsIO> getStats(String appId) {
    var container = service.getContainerByAppId(appId);
    long containerId = container.getId();

    Object[] totals = (Object[]) entityManager.createNativeQuery(
      "SELECT COUNT(dp.timeseries_id) AS point_count, COUNT(DISTINCT dp.timeseries_id) AS channel_count " +
      "FROM timeseries_data_points dp " +
      "JOIN timeseries t ON dp.timeseries_id = t.id " +
      "WHERE t.container_id = :cid"
    ).setParameter("cid", containerId).getSingleResult();

    long pointCount = ((Number) totals[0]).longValue();
    long channelCount = ((Number) totals[1]).longValue();

    long nowNs = System.currentTimeMillis() * 1_000_000L;
    Number recent = (Number) entityManager.createNativeQuery(
      "SELECT COUNT(*) " +
      "FROM timeseries_data_points dp " +
      "JOIN timeseries t ON dp.timeseries_id = t.id " +
      "WHERE t.container_id = :cid AND dp.time > :windowStart"
    )
      .setParameter("cid", containerId)
      .setParameter("windowStart", nowNs - WINDOW_NS)
      .getSingleResult();

    long recentPoints = recent.longValue();
    return Optional.of(new ContainerStatsIO(
      pointCount,
      channelCount,
      pointCount * BYTES_PER_POINT,
      recentPoints,
      recentPoints * BYTES_PER_POINT / 10
    ));
  }

  @Override
  public Optional<List<DataObjectIO>> listLinkedDataObjects(String appId) {
    return Optional.of(
      service.findLinkedDataObjectsByAppId(appId).stream().map(DataObjectIO::new).toList()
    );
  }

  // ── APISIMP-CONT-NS-COLLAPSE-2: channel overrides ─────────────────────────

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — list channels of this timeseries container with
   * pagination. Mirrors the logic from the deleted
   * {@code TimeseriesContainerChannelsRest.listChannels}.
   */
  @Override
  public Optional<List<TimeseriesChannelV2IO>> listChannels(
      String containerAppId, int page, int pageSize) {
    long containerId = service.getContainerByAppId(containerAppId).getId();
    int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    List<TimeseriesEntity> rows = tsChannelResolver.listPaged(containerId, page, safeSize);
    return Optional.of(rows.stream().map(TimeseriesChannelV2IO::from).toList());
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — return spatial role assignments for the Trace3D
   * recipe builder. Mirrors the logic from the deleted
   * {@code TimeseriesContainerChannelsRest.getSpatialRoles}.
   */
  @Override
  public Optional<SpatialRolesIO> getChannelSpatialRoles(String containerAppId) {
    long containerId = service.getContainerByAppId(containerAppId).getId();

    Map<String, UUID> roleMap = new HashMap<>();
    int page = 0;
    final int PAGE_SIZE = 500;

    while (true) {
      List<TimeseriesEntity> batch = tsChannelResolver.listPaged(containerId, page, PAGE_SIZE);
      if (batch.isEmpty()) break;

      for (TimeseriesEntity row : batch) {
        if (row.getShepardId() == null) continue;
        Optional<AnnotatableTimeseries> node =
            annotatableTimeseriesDAO.findByAppId(row.getShepardId().toString());
        if (node.isEmpty()) continue;

        for (SemanticAnnotation ann : node.get().getAnnotations()) {
          if (!Constants.TS_AXIS_PREDICATE.equals(ann.getPropertyIRI())) continue;
          String role = ann.getValueIRI();
          roleMap.putIfAbsent(role, row.getShepardId());
        }
      }

      if (batch.size() < PAGE_SIZE) break;
      page++;
    }

    return Optional.of(new SpatialRolesIO(
        roleMap.get("x"),
        roleMap.get("y"),
        roleMap.get("z"),
        roleMap.get("rot_a"),
        roleMap.get("rot_b"),
        roleMap.get("rot_c")
    ));
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — fetch data points for a single channel by
   * {@code shepardId}. Mirrors the logic from the deleted
   * {@code TimeseriesContainerChannelsRest.getChannelData}.
   *
   * <p>Throws {@link NotFoundException} (→ 404) when the channel is not found in
   * this container; the JAX-RS exception mapper converts it to the standard
   * problem-json 404 response.
   */
  @Override
  public Optional<TimeseriesWithDataPoints> getChannelData(
      String containerAppId, UUID shepardId,
      Long start, Long end, String downsample, Integer maxPoints) {
    long containerId = service.getContainerByAppId(containerAppId).getId();

    Timeseries tuple = tsChannelResolver.resolveTuple(shepardId).orElse(null);
    if (tuple == null) {
      throw new NotFoundException(
          "No channel with shepardId " + shepardId + " in container " + containerAppId);
    }

    var points = downsample != null && "lttb".equalsIgnoreCase(downsample.trim())
        ? timeseriesService.getDataPointsLttbOptimised(
            containerId, tuple, start, end,
            maxPoints == null ? DEFAULT_MAX_POINTS : Math.min(Math.max(maxPoints, 1), HARD_MAX_POINTS))
        : timeseriesService.getDataPointsByTimeseries(
            containerId, tuple, new TimeseriesDataPointsQueryParams(start, end, null, null, null));

    return Optional.of(new TimeseriesWithDataPoints(tuple, points));
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — bulk channel data fetch.
   * Mirrors the logic from the deleted
   * {@code TimeseriesContainerChannelsRest.getBulkChannelData}.
   */
  @Override
  public Optional<List<TimeseriesWithDataPoints>> getBulkChannelData(
      String containerAppId, BulkChannelDataRequestIO body) {
    long containerId = service.getContainerByAppId(containerAppId).getId();

    var entities = tsChannelResolver.bulkFindByShepardIds(body.shepardIds());
    List<TimeseriesWithDataPoints> results = timeseriesService.getManyDataPointsByEntities(
        containerId, entities,
        new TimeseriesDataPointsQueryParams(body.start(), body.end(), null, null, null));

    return Optional.of(results);
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — COPY-protocol ingest for a single channel.
   * Mirrors the logic from the deleted
   * {@code TimeseriesContainerChannelsRest.ingestChannelData}.
   *
   * <p>Throws {@link NotFoundException} (→ 404) when the channel is not found in
   * this container.
   */
  @Override
  public boolean ingestChannelData(
      String containerAppId, UUID shepardId, CopyIngestRequestIO body) {
    long containerId = service.getContainerByAppId(containerAppId).getId();

    var entity = tsChannelResolver.findByShepardId(shepardId)
        .filter(e -> e.getContainerId() == containerId)
        .orElse(null);
    if (entity == null) {
      throw new NotFoundException(
          "No channel with shepardId " + shepardId + " in container " + containerAppId);
    }

    timeseriesService.ingestDataPointsCopy(containerId, entity, body.dataPoints());
    return true;
  }
}
