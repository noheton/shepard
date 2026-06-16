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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V2CONV-A2 / APISIMP-KIND-DISCRIMINATOR — in-tree {@link ReferenceKindHandler}
 * for {@code kind=file} (FR1b singletons). Delegates to
 * {@link SingletonFileReferenceService}.
 *
 * <p>Sets {@code referenceShape="singleton"} and surfaces {@code fileKind}.
 *
 * <p>Option C two-step file creation (APISIMP-KIND-DISCRIMINATOR):
 * <ol>
 *   <li>{@code POST /v2/references?kind=file} with JSON body {@code {name}}
 *       calls {@link #create} here, which creates a metadata-only node (no bytes).
 *   <li>{@code PUT /v2/references/{appId}/content} calls {@link #uploadContent},
 *       which stores the binary payload and attaches it to the node.
 * </ol>
 *
 * <p>The legacy {@code POST /v2/files} multipart entry point remains active
 * for backwards-compatibility until slice 2 retires it.
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
    // APISIMP-KIND-DISCRIMINATOR Option C phase 1: create the metadata node.
    // The binary payload is attached in a follow-up PUT …/content call.
    Object nameVal = body != null ? body.get("name") : null;
    if (nameVal == null || nameVal.toString().isBlank()) {
      throw new BadRequestException("kind=file create body must include a non-blank 'name' field");
    }
    FileReference created = singletonService.createSingletonMetadata(dataObjectAppId, nameVal.toString().trim());
    return toIO(created);
  }

  @Override
  public ReferenceV2IO uploadContent(String appId, InputStream input, String filename, long declaredSize) {
    FileReference updated = singletonService.attachContent(appId, filename, input, declaredSize);
    return toIO(updated);
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
