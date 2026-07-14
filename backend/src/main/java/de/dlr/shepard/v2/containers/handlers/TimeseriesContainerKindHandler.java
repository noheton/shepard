package de.dlr.shepard.v2.containers.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.io.TimeseriesAnnotationIO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkChannelDataRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.CopyIngestRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.LiveWindowPointIO;
import de.dlr.shepard.v2.timeseriescontainer.io.LiveWindowResponseIO;
import de.dlr.shepard.v2.timeseriescontainer.io.SpatialRolesIO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesChannelV2IO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import de.dlr.shepard.v2.timeseriescontainer.services.TimeseriesContainerChartViewService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * V2CONV-A3 — in-tree {@link ContainerKindHandler} for {@code kind=timeseries}.
 * Delegates create/get/delete/list to the existing
 * {@link TimeseriesContainerService} and resolves/renames via
 * {@link TimeseriesContainerDAO}. The frozen v1
 * {@code /shepard/api/timeseriesContainers} surface is untouched.
 *
 * <p>APISIMP-CONT-NS-COLLAPSE-4 — also overrides the 9 new default methods for
 * live-window, channel annotations, and temporal annotations, replacing the
 * deleted per-kind REST classes {@code TimeseriesLiveWindowRest},
 * {@code TimeseriesChannelAnnotationRest}, and
 * {@code TimeseriesContainerTemporalAnnotationRest}.
 */
@ApplicationScoped
public class TimeseriesContainerKindHandler implements ContainerKindHandler {

  private static final int BYTES_PER_POINT = 28;
  private static final long WINDOW_NS = 10_000_000_000L;
  private static final long NS_PER_MS = 1_000_000L;

  private static final String PT_NOT_FOUND =
      "/problems/timeseries-container-annotations.not-found";

  @Inject
  TimeseriesContainerService service;

  @Inject
  TimeseriesContainerDAO dao;

  @Inject
  TimeseriesContainerChartViewService chartViewService;

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

  // APISIMP-CONT-NS-COLLAPSE-4: additional injections for live-window + annotations
  @Inject
  TsChannelResolver channelResolver;

  @Inject
  TimeseriesDataPointRepository dataPointRepository;

  @Inject
  AnnotatableTimeseriesService annotatableTimeseriesService;

  @Inject
  TimeseriesAnnotationDAO annotationDAO;

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
      recentPoints * BYTES_PER_POINT / 10,
      null,
      null
    ));
  }

  @Override
  public Optional<List<DataObjectIO>> listLinkedDataObjects(String appId) {
    return Optional.of(
      service.findLinkedDataObjectsByAppId(appId).stream().map(DataObjectIO::new).toList()
    );
  }

  @Override
  public Optional<Long> countLinkedDataObjects(String appId) {
    return Optional.of(service.countLinkedDataObjectsByAppId(appId));
  }

  @Override
  public Optional<List<DataObjectIO>> listLinkedDataObjectsPaged(
      String appId, int skip, int limit) {
    return Optional.of(
      service.findLinkedDataObjectsByAppIdPaged(appId, skip, limit)
             .stream().map(DataObjectIO::new).toList()
    );
  }

  // ── APISIMP-CONT-NS-COLLAPSE-2: channel overrides ─────────────────────────

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — list channels of this timeseries container with
   * pagination. Mirrors the logic from the deleted
   * {@code TimeseriesContainerChannelsRest.listChannels}.
   */
  @Override
  public Optional<PagedResponseIO<TimeseriesChannelV2IO>> listChannels(
      String containerAppId, int page, int pageSize) {
    long containerId = service.getContainerByAppId(containerAppId).getId();
    int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    List<TimeseriesEntity> rows = tsChannelResolver.listPaged(containerId, page, safeSize);
    long total = tsChannelResolver.countByContainerId(containerId);
    return Optional.of(new PagedResponseIO<>(
        rows.stream().map(TimeseriesChannelV2IO::from).toList(),
        total, page, safeSize));
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

  // ── APISIMP-CONT-NS-COLLAPSE-3: chart-view overrides ──────────────────────

  @Override
  public Optional<TimeseriesContainerChartViewIO> getChartView(String appId) {
    TimeseriesContainer c = dao.findByAppId(appId)
      .filter(x -> !x.isDeleted())
      .orElseThrow(() -> new NotFoundException("No timeseries container with appId " + appId));
    return Optional.of(TimeseriesContainerChartViewIO.from(chartViewService.find(c.getId())));
  }

  @Override
  public Optional<TimeseriesContainerChartViewIO> patchChartView(
      String appId, TimeseriesContainerChartViewIO patch) {
    TimeseriesContainer c = dao.findByAppId(appId)
      .filter(x -> !x.isDeleted())
      .orElseThrow(() -> new NotFoundException("No timeseries container with appId " + appId));
    return Optional.of(
      TimeseriesContainerChartViewIO.from(chartViewService.patch(c.getId(), patch))
    );
  }

  // ── APISIMP-CONT-NS-COLLAPSE-4: live-window ──────────────────────────────

  @Override
  public Optional<Response> getLiveWindow(
      String appId, UUID shepardId, String measurement, String device,
      String location, String symbolicName, String field,
      int windowSeconds, boolean withBoundaryPoints) {

    // Resolves the container by appId and enforces Read permission.
    var container = service.getContainerByAppId(appId);
    long containerId = container.getId();

    // Build the window boundaries in nanoseconds (storage unit) and milliseconds (response unit).
    long nowNs = System.currentTimeMillis() * NS_PER_MS;
    long windowNs = (long) windowSeconds * 1_000_000_000L;
    long startNs = nowNs - windowNs;
    long windowStartMs = startNs / NS_PER_MS;
    long windowEndMs = nowNs / NS_PER_MS;

    // TS-IDc — shepardId wins when both shapes are supplied.
    TimeseriesEntity entity;
    if (shepardId != null) {
      Optional<TimeseriesEntity> byShepardId =
          channelResolver.findByContainerAndShepardId(containerId, shepardId);
      if (byShepardId.isEmpty()) {
        return Optional.of(problem("timeseries-live-window.not-found", "Not Found",
            Response.Status.NOT_FOUND,
            "No channel with shepardId " + shepardId + " in container " + appId));
      }
      entity = byShepardId.get();
    } else {
      List<TimeseriesEntity> matched = channelResolver.findByContainerAndPartialTuple(
          containerId, measurement, device, location, symbolicName, field);

      if (matched.isEmpty()) {
        return Optional.of(problem("timeseries-live-window.not-found", "Not Found",
            Response.Status.NOT_FOUND,
            "No channel matches the supplied filter fields in container " + appId));
      }
      if (matched.size() > 1) {
        return Optional.of(problem("timeseries-live-window.bad-request", "Bad Request",
            Response.Status.BAD_REQUEST,
            "Channel address is ambiguous — " + matched.size() +
                " channels match. Provide more specific filter fields."));
      }
      entity = matched.get(0);
    }
    int tsId = entity.getId();
    DataPointValueType valueType = entity.getValueType();

    // Fetch the raw data points in the window.
    var queryParams = new TimeseriesDataPointsQueryParams(startNs, nowNs, null, null, null);
    List<TimeseriesDataPoint> raw = dataPointRepository.queryDataPoints(tsId, valueType, queryParams);

    List<LiveWindowPointIO> points = buildPoints(
        raw, tsId, valueType, startNs, nowNs, withBoundaryPoints);

    return Optional.of(
        Response.ok(new LiveWindowResponseIO(windowStartMs, windowEndMs, points)).build());
  }

  /**
   * Assembles the final point list, optionally prepending/appending interpolated boundary points.
   */
  private List<LiveWindowPointIO> buildPoints(
      List<TimeseriesDataPoint> raw,
      int tsId,
      DataPointValueType valueType,
      long startNs,
      long endNs,
      boolean withBoundaryPoints) {

    List<LiveWindowPointIO> result = new ArrayList<>(raw.size() + 2);

    boolean canInterpolate = withBoundaryPoints &&
        (valueType == DataPointValueType.Double || valueType == DataPointValueType.Integer);

    if (canInterpolate && !raw.isEmpty()) {
      TimeseriesDataPoint first = raw.get(0);
      if (first.getTimestamp() > startNs) {
        Optional<TimeseriesDataPoint> before =
            dataPointRepository.findLatestBefore(tsId, valueType, startNs);
        if (before.isPresent()) {
          double interpolated = lerp(before.get(), first, startNs);
          result.add(new LiveWindowPointIO(startNs / 1_000_000L, interpolated, true));
        }
      }
    }

    for (TimeseriesDataPoint dp : raw) {
      result.add(new LiveWindowPointIO(dp.getTimestamp() / 1_000_000L, dp.getValue(), false));
    }

    if (canInterpolate && !raw.isEmpty()) {
      TimeseriesDataPoint last = raw.get(raw.size() - 1);
      if (last.getTimestamp() < endNs) {
        Optional<TimeseriesDataPoint> after =
            dataPointRepository.findEarliestAfter(tsId, valueType, endNs);
        if (after.isPresent()) {
          double interpolated = lerp(last, after.get(), endNs);
          result.add(new LiveWindowPointIO(endNs / 1_000_000L, interpolated, true));
        }
      }
    }

    return result;
  }

  /**
   * Linear interpolation between two data points at a target nanosecond timestamp.
   */
  private double lerp(TimeseriesDataPoint a, TimeseriesDataPoint b, long targetNs) {
    long tA = a.getTimestamp();
    long tB = b.getTimestamp();
    double vA = toDouble(a.getValue());
    double vB = toDouble(b.getValue());
    if (tA == tB) return vA;
    double ratio = (double) (targetNs - tA) / (tB - tA);
    return vA + ratio * (vB - vA);
  }

  private double toDouble(Object v) {
    return ((Number) v).doubleValue();
  }

  // ── APISIMP-CONT-NS-COLLAPSE-4: channel annotations ──────────────────────

  @Override
  public Optional<Response> listChannelAnnotations(
      String appId, String channelShepardId, int page, int pageSize) {
    long containerId = service.getContainerByAppId(appId).getId();
    long skip = (long) page * pageSize;
    long total = annotatableTimeseriesService.countAnnotationsByChannelShepardId(containerId, channelShepardId);
    List<SemanticAnnotationIO> slice = annotatableTimeseriesService
        .getAnnotationsByChannelShepardId(containerId, channelShepardId, skip, pageSize)
        .stream()
        .map(SemanticAnnotationIO::new)
        .collect(Collectors.toList());
    return Optional.of(Response.ok(new de.dlr.shepard.v2.common.io.PagedResponseIO<>(slice, total, page, pageSize))
        .build());
  }

  @Override
  public Optional<Response> createChannelAnnotation(
      String appId, String channelShepardId, SemanticAnnotationIO body) {
    long containerId = service.getContainerByAppId(appId).getId();
    SemanticAnnotation created =
        annotatableTimeseriesService.createAnnotationForChannel(containerId, channelShepardId, body);
    return Optional.of(Response.status(Response.Status.CREATED)
        .entity(new SemanticAnnotationIO(created))
        .build());
  }

  @Override
  public Optional<Response> deleteChannelAnnotation(
      String appId, String channelShepardId, String annotationAppId) {
    long containerId = service.getContainerByAppId(appId).getId();
    annotatableTimeseriesService.deleteAnnotationForChannel(
        containerId, channelShepardId, annotationAppId);
    return Optional.of(Response.noContent().build());
  }

  // ── APISIMP-CONT-NS-COLLAPSE-4: temporal annotations ─────────────────────

  @Override
  public Optional<Response> listTemporalAnnotations(String appId, int page, int pageSize) {
    long containerId = service.getContainerByAppId(appId).getId();
    long skip = (long) page * pageSize;
    long total = annotationDAO.countByContainerId(containerId);
    List<TimeseriesAnnotationIO> slice = annotationDAO
        .findByContainerId(containerId, skip, pageSize)
        .stream()
        .map(TimeseriesAnnotationIO::new)
        .collect(Collectors.toList());
    return Optional.of(Response.ok(new de.dlr.shepard.v2.common.io.PagedResponseIO<>(slice, total, page, pageSize))
        .build());
  }

  @Override
  public Optional<Response> createTemporalAnnotation(String appId, TimeseriesAnnotationIO body) {
    if (body == null || body.getStart() == null) {
      return Optional.of(problem("timeseries-container-annotations.bad-request", "Bad Request",
          Response.Status.BAD_REQUEST, "start is required"));
    }
    if (body.getLabel() == null || body.getLabel().isBlank()) {
      return Optional.of(problem("timeseries-container-annotations.bad-request", "Bad Request",
          Response.Status.BAD_REQUEST, "label is required and must be non-blank"));
    }
    long containerId = service.getContainerByAppId(appId).getId();
    service.assertIsAllowedToEditContainer(containerId);

    TimeseriesAnnotation a = new TimeseriesAnnotation();
    a.setAppId(AppIdGenerator.next());
    a.setStartNs(parseNs(body.getStart()));
    a.setEndNs(body.getEnd() != null ? parseNs(body.getEnd()) : null);
    a.setLabel(body.getLabel().strip());
    a.setDescription(body.getDescription());
    a.setAiGenerated(body.isAiGenerated());
    a.setConfidence(body.getConfidence());

    annotationDAO.createOrUpdate(a);
    annotationDAO.linkToContainer(containerId, a.getAppId());

    return Optional.of(Response.status(Response.Status.CREATED)
        .entity(new TimeseriesAnnotationIO(a)).build());
  }

  @Override
  public Optional<Response> getTemporalAnnotation(String appId, String annotationAppId) {
    service.getContainerByAppId(appId);
    TimeseriesAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) {
      return Optional.of(problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND,
          "Annotation not found: " + annotationAppId));
    }
    return Optional.of(Response.ok(new TimeseriesAnnotationIO(a)).build());
  }

  @Override
  public Optional<Response> updateTemporalAnnotation(
      String appId, String annotationAppId, TimeseriesAnnotationIO body) {
    long containerId = service.getContainerByAppId(appId).getId();
    service.assertIsAllowedToEditContainer(containerId);
    TimeseriesAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) {
      return Optional.of(problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND,
          "Annotation not found: " + annotationAppId));
    }

    if (body.getStart() != null) a.setStartNs(parseNs(body.getStart()));
    if (body.getEnd() != null) a.setEndNs(parseNs(body.getEnd()));
    if (body.getLabel() != null) {
      if (body.getLabel().isBlank()) {
        return Optional.of(problem("timeseries-container-annotations.bad-request", "Bad Request",
            Response.Status.BAD_REQUEST, "label must be non-blank"));
      }
      a.setLabel(body.getLabel().strip());
    }
    if (body.getDescription() != null) a.setDescription(body.getDescription());
    if (body.getConfidence() != null) a.setConfidence(body.getConfidence());

    annotationDAO.createOrUpdate(a);
    return Optional.of(Response.ok(new TimeseriesAnnotationIO(a)).build());
  }

  @Override
  public Optional<Response> deleteTemporalAnnotation(String appId, String annotationAppId) {
    long containerId = service.getContainerByAppId(appId).getId();
    service.assertIsAllowedToEditContainer(containerId);
    TimeseriesAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) {
      return Optional.of(problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND,
          "Annotation not found: " + annotationAppId));
    }
    annotationDAO.unlinkAndDeleteFromContainer(containerId, a);
    return Optional.of(Response.noContent().build());
  }

  @Override
  public Optional<List<String>> findLinkedDataObjectAppIds(String appId) {
    TimeseriesContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) return Optional.empty();
    return Optional.of(
        service.findLinkedDataObjectsById(c.getId()).stream()
            .map(d -> d.getAppId())
            .toList()
    );
  }

  private static long parseNs(String iso) {
    Instant instant = Instant.parse(iso);
    return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
  }
}
