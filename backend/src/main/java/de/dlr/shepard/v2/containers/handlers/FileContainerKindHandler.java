package de.dlr.shepard.v2.containers.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.daos.PayloadVersionDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.file.services.FileContainerService;
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
 * V2CONV-A3 — in-tree {@link ContainerKindHandler} for {@code kind=file}.
 * Delegates create/get/delete/list to the existing {@link FileContainerService}
 * (reuse, not duplication) and resolves/renames via {@link FileContainerDAO}.
 * The frozen v1 {@code /shepard/api/fileContainers} surface is untouched — this
 * handler only feeds the unified {@code /v2/containers} dispatcher.
 */
@ApplicationScoped
public class FileContainerKindHandler implements ContainerKindHandler {

  @Inject
  FileContainerService service;

  @Inject
  FileContainerDAO dao;

  @Inject
  PayloadVersionDAO payloadVersionDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Override
  public String kind() {
    return "file";
  }

  @Override
  public boolean owns(BasicContainer container) {
    return container instanceof FileContainer;
  }

  @Override
  public BasicContainer findByAppId(String appId) {
    return dao.findByAppId(appId).filter(c -> !c.isDeleted()).orElse(null);
  }

  @Override
  public ContainerV2IO toIO(BasicContainer container) {
    return toIO((FileContainer) container);
  }

  private ContainerV2IO toIO(FileContainer c) {
    var io = new ContainerV2IO(c, kind());
    io.put("oid", c.getMongoId());
    if (c.getCollectionList() != null) {
      io.put(
        "defaultCollectionAppIds",
        c.getCollectionList().stream().filter(d -> !d.isDeleted()).map(Collection::getAppId).toList()
      );
    }
    return io;
  }

  @Override
  public ContainerV2IO create(Map<String, Object> body) {
    String name = ContainerPatchSupport.requireName(body);
    var in = new FileContainerIO();
    in.setName(name);
    FileContainer created = service.createContainer(in);
    return toIO(created);
  }

  @Override
  public ContainerV2IO patch(String appId, Map<String, Object> patch) {
    FileContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) throw new NotFoundException("No file container with appId " + appId);
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
    FileContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) throw new NotFoundException("No file container with appId " + appId);
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
}
