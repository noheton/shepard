package de.dlr.shepard.v2.containers.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
}
