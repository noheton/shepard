package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileBundleReferenceService;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * APISIMP-KIND-DISCRIMINATOR slice 3 — in-tree {@link ReferenceKindHandler}
 * for {@code kind=bundle} (FR1a multi-file bundles,
 * {@link FileBundleReference}). Exposes FileBundleReference on the unified
 * {@code /v2/references} surface, eliminating the separate
 * {@code /v2/bundles} endpoints for callers.
 *
 * <p>Sets {@code referenceShape="bundle"} and surfaces
 * {@code containerMongoId}, {@code containerAppId}, {@code groupCount},
 * {@code fileCount} in the payload map.
 *
 * <p>Create body: {@code {name, fileContainerAppId, fileOids?}}.
 */
@RequestScoped
public class FileBundleReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Inject
  FileBundleReferenceService fileBundleReferenceService;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  DateHelper dateHelper;

  @Inject
  UserService userService;

  @Override
  public String kind() {
    return "bundle";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof FileBundleReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return fileBundleReferenceDAO.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    FileBundleReference ref = (FileBundleReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    io.setReferenceShape("bundle");
    io.put("containerMongoId", ref.getFileContainer() != null ? ref.getFileContainer().getMongoId() : null);
    io.put("containerAppId", ref.getFileContainer() != null ? ref.getFileContainer().getAppId() : null);
    io.put("groupCount", ref.getGroups() != null ? ref.getGroups().size() : 0);
    io.put("fileCount", ref.getFiles() != null ? ref.getFiles().size() : 0);
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) {
      throw new BadRequestException("create body is required for kind=bundle");
    }
    DataObject parent = resolveParent(dataObjectAppId);

    Object nameVal = body.get("name");
    if (nameVal == null || nameVal.toString().isBlank()) {
      throw new BadRequestException("kind=bundle create body must include a non-blank 'name' field");
    }

    Object containerAppIdVal = body.get("fileContainerAppId");
    if (containerAppIdVal == null || containerAppIdVal.toString().isBlank()) {
      throw new BadRequestException("kind=bundle create body must include 'fileContainerAppId'");
    }

    FileContainer container = fileContainerService.getContainerByAppId(containerAppIdVal.toString().trim());

    FileReferenceIO io = new FileReferenceIO();
    io.setName(nameVal.toString().trim());
    io.setFileContainerId(container.getId());

    Object oidsVal = body.get("fileOids");
    if (oidsVal instanceof List<?> oidsList) {
      io.setFileOids(oidsList.stream().map(Object::toString).toArray(String[]::new));
    } else {
      io.setFileOids(new String[0]);
    }

    FileBundleReference created = fileBundleReferenceService.createReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      io
    );
    return toIO(created);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    FileBundleReference ref = fileBundleReferenceDAO.findByAppId(appId);
    if (ref == null || ref.isDeleted()) {
      throw new NotFoundException("No FileBundleReference with appId " + appId);
    }
    if (patch.containsKey("name")) {
      Object nameVal = patch.get("name");
      if (nameVal == null || nameVal.toString().isBlank()) {
        throw new BadRequestException("name must not be blank");
      }
      ref.setName(nameVal.toString().trim());
      ref.setUpdatedAt(dateHelper.getDate());
      ref.setUpdatedBy(userService.getCurrentUser());
      fileBundleReferenceDAO.createOrUpdate(ref);
    }
    return toIO(ref);
  }

  @Override
  public void delete(String appId) {
    FileBundleReference ref = fileBundleReferenceDAO.findByAppId(appId);
    if (ref == null || ref.isDeleted()) {
      throw new NotFoundException("No FileBundleReference with appId " + appId);
    }
    DataObject parent = ref.getDataObject();
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("FileBundleReference " + appId + " has no resolvable parent DataObject");
    }
    fileBundleReferenceService.deleteReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ref.getShepardId()
    );
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<FileBundleReference> refs = fileBundleReferenceDAO.findByDataObjectAppId(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (FileBundleReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }

  private DataObject resolveParent(String dataObjectAppId) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    DataObject parent = dataObjectDAO.findByAppId(dataObjectAppId);
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    return parent;
  }
}
