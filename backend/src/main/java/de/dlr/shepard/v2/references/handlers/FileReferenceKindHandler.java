package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
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
 * V2CONV-A2 — in-tree {@link ReferenceKindHandler} for {@code kind=file}
 * (FR1b singletons). Delegates to {@link SingletonFileReferenceService}.
 *
 * <p>Sets {@code referenceShape="singleton"} and surfaces {@code fileKind}.
 * Binary creation does NOT route through the unified
 * {@code POST /v2/references} — it keeps the multipart
 * {@code POST /v2/files} entry point, so {@link #create} here rejects with
 * 400 directing the caller to the upload endpoint. Payload key set:
 * {@code {file}}.
 */
@RequestScoped
public class FileReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  SingletonFileReferenceService singletonService;

  @Override
  public String kind() {
    return "file";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof FileReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return singletonService.getByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    FileReference ref = (FileReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    io.setReferenceShape("singleton");
    io.setFileKind(ref.getFileKind());
    io.put("file", ref.getFile());
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    // FR1b singletons carry binary bytes — they are created via the
    // multipart POST /v2/files upload entry, not the JSON unified create.
    throw new BadRequestException(
      "kind=file is a binary upload — use multipart POST /v2/files?parentDataObjectAppId=… instead of POST /v2/references"
    );
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    FileReference updated = singletonService.patchSingleton(appId, patch);
    return toIO(updated);
  }

  @Override
  public void delete(String appId) {
    FileReference ref = singletonService.getByAppId(appId);
    if (ref == null) throw new NotFoundException("No singleton FileReference with appId " + appId);
    singletonService.deleteSingleton(appId);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<FileReference> refs = singletonService.listByDataObject(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (FileReference ref : refs) {
      if (ref == null || ref.isDeleted()) continue;
      // V2CONV-A2 fileKind sub-filter: when subKind is supplied, only
      // singletons whose fileKind matches pass through.
      if (subKind != null && !subKind.isBlank() && !subKind.equals(ref.getFileKind())) continue;
      out.add(toIO(ref));
    }
    return out;
  }
}
