package de.dlr.shepard.v2.containers.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.data.file.daos.PayloadVersionDAO;
import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import de.dlr.shepard.v2.file.io.PayloadVersionIO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V2CONV-A3 — in-tree {@link ContainerKindHandler} for
 * {@code kind=structured-data}. Delegates create/get/delete/list to the
 * existing {@link StructuredDataContainerService} and resolves/renames via
 * {@link StructuredDataContainerDAO}. The frozen v1
 * {@code /shepard/api/structuredDataContainers} surface is untouched.
 */
@ApplicationScoped
public class StructuredDataContainerKindHandler implements ContainerKindHandler {

  @Inject
  StructuredDataContainerService service;

  @Inject
  StructuredDataContainerDAO dao;

  @Inject
  PayloadVersionDAO payloadVersionDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Override
  public String kind() {
    return "structured-data";
  }

  @Override
  public boolean owns(BasicContainer container) {
    return container instanceof StructuredDataContainer;
  }

  @Override
  public BasicContainer findByAppId(String appId) {
    return dao.findByAppId(appId).filter(c -> !c.isDeleted()).orElse(null);
  }

  @Override
  public ContainerV2IO toIO(BasicContainer container) {
    return toIO((StructuredDataContainer) container);
  }

  private ContainerV2IO toIO(StructuredDataContainer c) {
    var io = new ContainerV2IO(c, kind());
    io.put("oid", c.getMongoId());
    return io;
  }

  @Override
  public ContainerV2IO create(Map<String, Object> body) {
    String name = ContainerPatchSupport.requireName(body);
    var in = new StructuredDataContainerIO();
    in.setName(name);
    StructuredDataContainer created = service.createContainer(in);
    return toIO(created);
  }

  @Override
  public ContainerV2IO patch(String appId, Map<String, Object> patch) {
    StructuredDataContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) throw new NotFoundException("No structured-data container with appId " + appId);
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
    StructuredDataContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) throw new NotFoundException("No structured-data container with appId " + appId);
    service.deleteContainer(c.getId());
  }

  @Override
  public List<ContainerV2IO> list(String nameFilter) {
    var params = new QueryParamHelper();
    if (nameFilter != null && !nameFilter.isBlank()) params = params.withName(nameFilter);
    return service.getAllContainers(params).stream().map(this::toIO).toList();
  }

  @Override
  public Optional<List<PayloadVersionIO>> listVersions(String appId, String fileName) {
    return Optional.of(
      payloadVersionDAO.findByContainerAndName(appId, fileName).stream()
        .map(v -> new PayloadVersionIO(
          v.getAppId(), v.getVersionNumber(), v.getFileOid(),
          v.getSha256(), v.getSizeBytes(), v.getUploadedBy(), v.getUploadedAt()
        ))
        .toList()
    );
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

  @Override
  public Optional<ContainerStatsIO> getStats(String appId) {
    StructuredDataContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) return Optional.empty();
    long count = c.getStructuredDatas() != null ? c.getStructuredDatas().size() : 0L;
    return Optional.of(new ContainerStatsIO(null, null, null, null, null, null, count));
  }

  @Override
  public Optional<List<String>> findLinkedDataObjectAppIds(String appId) {
    StructuredDataContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) return Optional.empty();
    return Optional.of(
        service.findLinkedDataObjectsById(c.getId()).stream()
            .map(d -> d.getAppId())
            .toList()
    );
  }
}
