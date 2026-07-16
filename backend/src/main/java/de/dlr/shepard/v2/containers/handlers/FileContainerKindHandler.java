package de.dlr.shepard.v2.containers.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.v2.common.ProblemResponse;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.daos.PayloadVersionDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.file.thumbnail.ThumbnailService;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.PresignTtlValidator;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import de.dlr.shepard.v2.file.io.PayloadVersionIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedDownloadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadRequestIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.UploadCommitIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;
import java.net.URI;
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

  @Inject
  ThumbnailService thumbnailService;

  @Inject
  PresignTtlValidator ttlValidator;

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

  // ── APISIMP-CONT-LIST-INMEM-PAGING ──────────────────────────────────────
  // Push COUNT + SKIP/LIMIT to Cypher; the SPI default loaded ALL rows twice
  // (once for count, once for the subList slice).

  @Override
  public int count(String nameFilter) {
    User user = userService.getCurrentUser();
    var params = new QueryParamHelper();
    if (nameFilter != null && !nameFilter.isBlank()) params = params.withName(nameFilter);
    return dao.countFileContainers(params, user.getUsername());
  }

  @Override
  public List<ContainerV2IO> list(String nameFilter, int skip, int limit) {
    var params = new QueryParamHelper();
    if (nameFilter != null && !nameFilter.isBlank()) params = params.withName(nameFilter);
    // skip is always page*limit from ContainersV2Rest; integer division recovers page exactly.
    params = params.withPageAndSize(limit > 0 ? skip / limit : 0, limit);
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
  // ─── APISIMP-CONT-NS-COLLAPSE-5 ──────────────────────────────────────────

  private static final int DEFAULT_SIZE = 400;
  private static final int[] VALID_SIZES = {64, 200, 400};

  private static int normaliseSize(Integer sizeParam) {
    if (sizeParam == null) return DEFAULT_SIZE;
    for (int v : VALID_SIZES) {
      if (sizeParam == v) return v;
    }
    return DEFAULT_SIZE;
  }

  @Override
  public Optional<Response> getThumbnail(String appId, String oid, Integer sizeParam) {
    int sizePx = normaliseSize(sizeParam);
    byte[] pngBytes;
    try {
      pngBytes = thumbnailService.getThumbnail(appId, oid, sizePx);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    } catch (ServiceUnavailableException sue) {
      return Optional.of(ProblemResponse.problemBuilder(
          "/problems/files.thumbnail-unavailable", "Thumbnail Service Unavailable",
          Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), sue.getMessage())
        .header("Retry-After", "5")
        .build());
    }
    if (pngBytes == null) {
      throw new NotFoundException("No thumbnail available for this file type.");
    }
    CacheControl cc = new CacheControl();
    cc.setMaxAge(3600);
    return Optional.of(Response.ok(pngBytes, "image/png").cacheControl(cc).build());
  }

  @Override
  public Optional<Response> getUploadUrl(String appId, PresignedUploadRequestIO request) {
    if (request == null || request.getFileName() == null || request.getFileName().isBlank()) {
      throw new BadRequestException("fileName is required");
    }
    FileContainer container = dao.findByAppId(appId)
      .filter(c -> !c.isDeleted())
      .orElseThrow(() -> new NotFoundException("No file container with appId " + appId));
    FileStorage.PresignedPut result;
    try {
      result = service.presignedUploadUrl(container.getId(), request.getFileName(), ttlValidator.effectiveUploadTtl());
    } catch (StorageException se) {
      Log.errorf("presignedUploadUrl failed for container %s: %s", appId, se.getMessage());
      throw new InternalServerErrorException("Storage error: " + se.getMessage());
    }
    return Optional.of(Response.ok(
      new PresignedUploadUrlIO(result.uploadUrl().toString(), result.assignedOid(), result.expiresAt())
    ).build());
  }

  @Override
  public Optional<Response> commitUpload(String appId, UploadCommitIO commit) {
    if (commit == null || commit.getFileId() == null || commit.getFileId().isBlank()) {
      throw new BadRequestException("fileId is required");
    }
    if (commit.getFileName() == null || commit.getFileName().isBlank()) {
      throw new BadRequestException("fileName is required");
    }
    FileContainer container = dao.findByAppId(appId)
      .filter(c -> !c.isDeleted())
      .orElseThrow(() -> new NotFoundException("No file container with appId " + appId));
    ShepardFile file;
    try {
      file = service.commitUpload(
        container.getId(), commit.getFileId(), commit.getFileName(), commit.getFileSize());
    } catch (StorageException se) {
      Log.errorf("commitUpload failed for container %s fileId %s: %s", appId, commit.getFileId(), se.getMessage());
      throw new InternalServerErrorException("Storage error: " + se.getMessage());
    }
    return Optional.of(Response.status(Response.Status.CREATED).entity(file).build());
  }

  @Override
  public Optional<Response> getDownloadUrl(String appId, String oid) {
    FileContainer container = dao.findByAppId(appId)
      .filter(c -> !c.isDeleted())
      .orElseThrow(() -> new NotFoundException("No file container with appId " + appId));
    URI downloadUrl;
    try {
      downloadUrl = service.presignedDownloadUrl(container.getId(), oid, ttlValidator.effectiveDownloadTtl());
    } catch (StorageException se) {
      Log.errorf("presignedDownloadUrl failed for container %s oid %s: %s", appId, oid, se.getMessage());
      throw new InternalServerErrorException("Storage error: " + se.getMessage());
    }
    return Optional.of(Response.ok(
      new PresignedDownloadUrlIO(downloadUrl.toString(), java.time.Instant.now().plus(ttlValidator.effectiveDownloadTtl()))
    ).build());
  }

  // ── APISIMP-OID-PATHPARAM-REPLACE slice 2: fileAppId variants ───────────

  @Override
  public Optional<Response> getThumbnailByFileAppId(String appId, String fileAppId, Integer sizeParam) {
    FileContainer container = dao.findByAppId(appId)
      .filter(c -> !c.isDeleted())
      .orElseThrow(() -> new NotFoundException("No file container with appId " + appId));
    String oid = resolveOidByFileAppId(container, fileAppId);
    return getThumbnail(appId, oid, sizeParam);
  }

  @Override
  public Optional<Response> getDownloadUrlByFileAppId(String appId, String fileAppId) {
    FileContainer container = dao.findByAppId(appId)
      .filter(c -> !c.isDeleted())
      .orElseThrow(() -> new NotFoundException("No file container with appId " + appId));
    String oid = resolveOidByFileAppId(container, fileAppId);
    return getDownloadUrl(appId, oid);
  }

  private String resolveOidByFileAppId(FileContainer container, String fileAppId) {
    if (container.getFiles() != null) {
      return container.getFiles().stream()
        .filter(f -> fileAppId.equals(f.getAppId()))
        .map(ShepardFile::getOid)
        .findFirst()
        .orElseThrow(() -> new NotFoundException("No file with fileAppId " + fileAppId));
    }
    throw new NotFoundException("No file with fileAppId " + fileAppId);
  }

  @Override
  public Optional<ContainerStatsIO> getStats(String appId) {
    FileContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) return Optional.empty();
    long count = c.getFiles() != null ? c.getFiles().size() : 0L;
    return Optional.of(new ContainerStatsIO(null, null, null, null, null, count, null));
  }

  @Override
  public Optional<List<String>> findLinkedDataObjectAppIds(String appId) {
    FileContainer c = dao.findByAppId(appId).filter(x -> !x.isDeleted()).orElse(null);
    if (c == null) return Optional.empty();
    return Optional.of(
        service.findLinkedDataObjectsById(c.getId()).stream()
            .map(d -> d.getAppId())
            .toList()
    );
  }
}
