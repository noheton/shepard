package de.dlr.shepard.v2.hdf.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.io.HdfContainerIO;
import de.dlr.shepard.data.hdf.services.HdfContainerService;
import de.dlr.shepard.v2.containers.handlers.ContainerPatchSupport;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;

/**
 * PLUGIN-CONTAINER-HANDLER-HDF — {@link ContainerKindHandler} for {@code kind=hdf}.
 *
 * <p>Discovered via CDI {@code @Any Instance<ContainerKindHandler>} by the
 * {@code ContainersV2Service} dispatcher. Delegates CRUD to the existing
 * {@link HdfContainerService} and {@link HdfContainerDAO} (reuse, not
 * duplication). The frozen v1 and per-kind {@code /v2/hdf-containers/...}
 * surfaces are untouched — this handler only feeds the unified
 * {@code /v2/containers} dispatcher.
 *
 * <p>Payload key set: {@code hsdsDomain, description} (read-only fields
 * surfaced from the entity).
 */
@ApplicationScoped
public class HdfContainerKindHandler implements ContainerKindHandler {

  @Inject
  HdfContainerService service;

  @Inject
  HdfContainerDAO dao;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Override
  public String kind() {
    return "hdf";
  }

  @Override
  public boolean owns(BasicContainer container) {
    return container instanceof HdfContainer;
  }

  @Override
  public BasicContainer findByAppId(String appId) {
    HdfContainer c = dao.findByAppId(appId);
    return (c == null || c.isDeleted()) ? null : c;
  }

  @Override
  public ContainerV2IO toIO(BasicContainer container) {
    return toIO((HdfContainer) container);
  }

  private ContainerV2IO toIO(HdfContainer c) {
    ContainerV2IO io = new ContainerV2IO(c, kind());
    io.put("hsdsDomain", c.getHsdsDomain());
    io.put("description", c.getDescription());
    return io;
  }

  @Override
  public ContainerV2IO create(Map<String, Object> body) {
    String name = ContainerPatchSupport.requireName(body);
    HdfContainerIO in = new HdfContainerIO();
    in.setName(name);
    if (body.containsKey("description")) {
      Object v = body.get("description");
      in.setDescription(v == null ? null : v.toString());
    }
    HdfContainer created = service.createContainer(in);
    return toIO(created);
  }

  @Override
  public ContainerV2IO patch(String appId, Map<String, Object> patch) {
    HdfContainer c = dao.findByAppId(appId);
    if (c == null || c.isDeleted()) throw new NotFoundException("No HDF container with appId " + appId);
    boolean changed = ContainerPatchSupport.applyMutableFields(c, patch);
    if (patch != null && patch.containsKey("description")) {
      Object v = patch.get("description");
      String desc = v == null ? null : v.toString();
      if (!java.util.Objects.equals(desc, c.getDescription())) {
        c.setDescription(desc);
        changed = true;
      }
    }
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
    HdfContainer c = dao.findByAppId(appId);
    if (c == null || c.isDeleted()) throw new NotFoundException("No HDF container with appId " + appId);
    service.deleteContainer(c.getId());
  }

  @Override
  public List<ContainerV2IO> list(String nameFilter) {
    QueryParamHelper params = new QueryParamHelper();
    if (nameFilter != null && !nameFilter.isBlank()) params = params.withName(nameFilter);
    return service.getAllContainers(params).stream().map(this::toIO).toList();
  }
}
